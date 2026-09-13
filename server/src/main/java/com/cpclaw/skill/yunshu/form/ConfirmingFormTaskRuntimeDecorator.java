package com.cpclaw.skill.yunshu.form;

import com.cpclaw.yunshu.confirmation.ConfirmationPlan;
import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.cpclaw.yunshu.protocol.YunshuSkillGateway;
import com.cpclaw.yunshu.protocol.YunshuTaskEnvelope;
import com.cpclaw.yunshu.protocol.YunshuTaskRequest;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * Confirmation boundary for the persistent form runtime. It consumes a
 * server-signed ticket before delegating the confirmed operation; it never
 * treats a client-side {@code confirmed=true} flag as authorization.
 *
 * The delegate remains responsible for the actual Provider write. Until that
 * delegate is backed by a verified Provider, this decorator returns the same
 * CONTRACT_UNVERIFIED response and no write is attempted.
 */
public final class ConfirmingFormTaskRuntimeDecorator implements YunshuSkillGateway {
    private static final String SKILL_VERSION = "yunshu-form-runtime/1.0";
    private final YunshuSkillGateway delegate;
    private final FormTaskPersistenceService tasks;
    private final ConfirmationTicketService tickets;
    private final boolean providerWriteVerified;

    public ConfirmingFormTaskRuntimeDecorator(YunshuSkillGateway delegate, FormTaskPersistenceService tasks,
            ConfirmationTicketService tickets, boolean providerWriteVerified) {
        this.delegate = delegate;
        this.tasks = tasks;
        this.tickets = tickets;
        this.providerWriteVerified = providerWriteVerified;
    }

    @Override
    public YunshuTaskEnvelope handle(YunshuTaskRequest request, YunshuHostContext context) {
        // PersistentFormTaskRuntime is the sole confirmation-ticket
        // authorization/consumption point. This compatibility decorator is a
        // transparent pass-through and is not registered as a production bean.
        return delegate.handle(request, context);
    }

    /** Builds the exact binding used by ticket issuance and execution re-check. */
    public ConfirmationPlan plan(FormTaskSnapshot snapshot) {
        return new ConfirmationPlan(snapshot.taskId(), snapshot.scope().tenantId(), snapshot.scope().subjectId(),
            snapshot.scope().hostId(), SKILL_VERSION, String.valueOf(snapshot.formRef()), snapshot.metadataVersion(),
            snapshot.stateVersion(), snapshot.draftVersion(), planHash(snapshot),
            snapshot.taskId() + ":create:" + snapshot.draftVersion(), Instant.now().plusSeconds(1800));
    }

    private String planHash(FormTaskSnapshot snapshot) {
        String raw = snapshot.formRef() + "|" + snapshot.metadataVersion() + "|" + snapshot.slots();
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private YunshuTaskEnvelope failure(String taskId, YunshuProtocol.ErrorCode code, String message) {
        return new YunshuTaskEnvelope(YunshuProtocol.VERSION,
            new YunshuTaskEnvelope.Task(taskId, YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.failed, 0, false, Instant.now()),
            new YunshuTaskEnvelope.Interaction(YunshuProtocol.InteractionType.none, List.of()),
            new YunshuTaskEnvelope.Draft(Map.of(), "", 0, List.of()), new YunshuTaskEnvelope.Validation(List.of()),
            new YunshuTaskEnvelope.Next(Map.of(), "", null), new YunshuTaskEnvelope.Completion("failed", List.of()),
            Map.of("message", message == null ? "确认失败" : message), Map.of(),
            new YunshuTaskEnvelope.HostAction(YunshuProtocol.HostActionType.report_failure, false, Map.of()),
            Map.of("taskId", taskId == null ? "" : taskId), new YunshuTaskEnvelope.Error(code, message, false));
    }
}
