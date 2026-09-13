package com.cpclaw.yunshu.confirmation;

import com.cpclaw.yunshu.confirmation.entity.YunshuConfirmationTicket;
import com.cpclaw.yunshu.confirmation.repository.YunshuConfirmationTicketRepository;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues signed tickets and atomically consumes their durable state. It is not
 * an executor: only an already verified Provider may proceed after authorize.
 */
@Service
public class ConfirmationTicketService {
    private static final String KID = "yunshu-confirmation-hmac-v1";
    private static final String AUDIENCE = "yunshu-skill";
    private final YunshuConfirmationTicketRepository repository;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public ConfirmationTicketService(YunshuConfirmationTicketRepository repository, ObjectMapper objectMapper,
            @Value("${cpclaw.yunshu-skill.confirmation-secret:${CPCLAW_YUNSHU_CONFIRMATION_SECRET:}}") String secret) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /** A plan is allowed for preview; a ticket is not issued until write Provider verification exists. */
    @Transactional
    public TicketIssueResult issue(ConfirmationPlan plan, boolean providerWriteVerified) {
        String invalid = plan == null ? "PLAN_REQUIRED" : plan.validationError();
        if (!invalid.isBlank()) return TicketIssueResult.rejected(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, invalid);
        if (!providerWriteVerified) return TicketIssueResult.rejected(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED,
            "Provider 写入契约未验证，只能展示确认计划，不能签发执行票据。");
        if (secret.length < 32) return TicketIssueResult.rejected(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID,
            "确认票签名密钥未配置或长度不足，不能开放写入。");
        YunshuConfirmationTicket entity = fromPlan(UUID.randomUUID().toString(), plan);
        repository.saveAndFlush(entity);
        return TicketIssueResult.issued(sign(claims(entity)), entity.getExpiresAt());
    }

    /**
     * Verifies signature, all plan bindings and trusted host context, then
     * transitions PENDING to CONSUMED while holding a database row lock.
     */
    @Transactional
    public TicketAuthorization authorizeExecution(String token, YunshuHostContext context,
            ConfirmationPlan livePlan, boolean providerWriteVerified) {
        if (!providerWriteVerified) return TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED,
            "Provider 写入契约未验证，禁止执行云枢写入。");
        ConfirmationTicketClaims claims = verify(token);
        if (claims == null) return TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票无效或签名校验失败。");
        YunshuConfirmationTicket ticket = repository.findByIdForConsumption(claims.ticketId()).orElse(null);
        if (ticket == null || !matches(ticket, claims) || !matchesContext(ticket, context) || !matchesPlan(ticket, livePlan)) {
            return TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票与当前任务、身份或计划不匹配。");
        }
        Instant now = Instant.now();
        if (!"PENDING".equals(ticket.getStatus())) return TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票已被消费或已失效。");
        if (!ticket.getExpiresAt().isAfter(now)) {
            ticket.setStatus("EXPIRED"); repository.save(ticket);
            return TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票已过期。");
        }
        ticket.setStatus("CONSUMED");
        ticket.setConsumedAt(now);
        repository.save(ticket);
        return TicketAuthorization.granted(ticket.getId(), ticket.getIdempotencyKey());
    }

    private YunshuConfirmationTicket fromPlan(String id, ConfirmationPlan plan) {
        YunshuConfirmationTicket ticket = new YunshuConfirmationTicket();
        ticket.setId(id); ticket.setTaskId(plan.taskId()); ticket.setTenantId(plan.tenantId()); ticket.setSubjectId(plan.subjectId());
        ticket.setHostId(plan.hostId()); ticket.setSkillVersion(plan.skillVersion()); ticket.setFormRef(plan.formRef());
        ticket.setMetadataVersion(plan.metadataVersion()); ticket.setStateVersion(plan.stateVersion()); ticket.setDraftVersion(plan.draftVersion());
        ticket.setPlanHash(plan.planHash()); ticket.setIdempotencyKey(plan.idempotencyKey()); ticket.setStatus("PENDING");
        ticket.setExpiresAt(plan.expiresAt()); ticket.setCreatedAt(Instant.now());
        return ticket;
    }

    private ConfirmationTicketClaims claims(YunshuConfirmationTicket ticket) {
        return new ConfirmationTicketClaims(ticket.getId(), KID, "HS256", AUDIENCE, ticket.getTaskId(), ticket.getTenantId(),
            ticket.getSubjectId(), ticket.getHostId(), ticket.getSkillVersion(), ticket.getFormRef(), ticket.getMetadataVersion(),
            ticket.getStateVersion(), ticket.getDraftVersion(), ticket.getPlanHash(), ticket.getIdempotencyKey(), ticket.getExpiresAt());
    }

    private String sign(ConfirmationTicketClaims claims) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(claims));
            return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
        } catch (JsonProcessingException exception) { throw new IllegalStateException("无法签发确认票", exception); }
    }

    private ConfirmationTicketClaims verify(String token) {
        if (secret.length < 32 || token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 2) return null;
        try {
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!java.security.MessageDigest.isEqual(hmac(parts[0]), actual)) return null;
            ConfirmationTicketClaims claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), ConfirmationTicketClaims.class);
            return KID.equals(claims.kid()) && "HS256".equals(claims.alg()) && AUDIENCE.equals(claims.aud())
                && claims.expiresAt() != null && claims.expiresAt().isAfter(Instant.now()) ? claims : null;
        } catch (IllegalArgumentException | IOException exception) { return null; }
    }

    private byte[] hmac(String payload) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException("HMAC 不可用", exception); }
    }

    private boolean matches(YunshuConfirmationTicket t, ConfirmationTicketClaims c) {
        return equals(t.getId(), c.ticketId()) && equals(t.getTaskId(), c.taskId()) && equals(t.getTenantId(), c.tenantId())
            && equals(t.getSubjectId(), c.subjectId()) && equals(t.getHostId(), c.hostId()) && equals(t.getSkillVersion(), c.skillVersion())
            && equals(t.getFormRef(), c.formRef()) && equals(t.getMetadataVersion(), c.metadataVersion())
            && t.getStateVersion() == c.stateVersion() && t.getDraftVersion() == c.draftVersion() && equals(t.getPlanHash(), c.planHash())
            && equals(t.getIdempotencyKey(), c.idempotencyKey()) && sameInstant(t.getExpiresAt(), c.expiresAt());
    }
    private boolean matchesContext(YunshuConfirmationTicket t, YunshuHostContext c) {
        return c != null && equals(t.getTenantId(), c.tenantId()) && equals(t.getSubjectId(), c.subjectId()) && equals(t.getHostId(), c.hostId());
    }
    private boolean matchesPlan(YunshuConfirmationTicket t, ConfirmationPlan p) {
        return p != null && equals(t.getTaskId(), p.taskId()) && equals(t.getTenantId(), p.tenantId()) && equals(t.getSubjectId(), p.subjectId())
            && equals(t.getHostId(), p.hostId()) && equals(t.getSkillVersion(), p.skillVersion()) && equals(t.getFormRef(), p.formRef())
            && equals(t.getMetadataVersion(), p.metadataVersion()) && t.getStateVersion() == p.stateVersion()
            && t.getDraftVersion() == p.draftVersion() && equals(t.getPlanHash(), p.planHash()) && equals(t.getIdempotencyKey(), p.idempotencyKey());
    }
    private boolean equals(Object left, Object right) { return Objects.equals(left, right); }
    private boolean sameInstant(Instant left, Instant right) {
        return left != null && right != null && left.toEpochMilli() == right.toEpochMilli();
    }

    public record TicketIssueResult(String ticket, Instant expiresAt, YunshuProtocol.ErrorCode errorCode, String message) {
        static TicketIssueResult issued(String ticket, Instant expiresAt) { return new TicketIssueResult(ticket, expiresAt, null, ""); }
        static TicketIssueResult rejected(YunshuProtocol.ErrorCode code, String message) { return new TicketIssueResult("", null, code, message); }
        public boolean issued() { return !ticket.isBlank(); }
    }
    public record TicketAuthorization(boolean granted, String ticketId, String idempotencyKey, YunshuProtocol.ErrorCode errorCode, String message) {
        public static TicketAuthorization granted(String ticketId, String idempotencyKey) { return new TicketAuthorization(true, ticketId, idempotencyKey, null, ""); }
        public static TicketAuthorization denied(YunshuProtocol.ErrorCode code, String message) { return new TicketAuthorization(false, "", "", code, message); }
    }
}
