package com.cpclaw.yunshu.protocol;

import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.mcp.McpRequestAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Built-in resolver for a direct OpenClaw-compatible Yunshu Skill host.
 * The host id is routing data; the subject is accepted only after the shared
 * HMAC proof has been verified by {@link McpRequestAuthenticator}.
 */
@Component
@ConditionalOnProperty(name = "cpclaw.yunshu-skill.identity-mode", havingValue = "hmac", matchIfMissing = true)
public class HmacYunshuHostContextResolver implements YunshuHostContextResolver {
    public static final String PRINCIPAL_HEADER = "X-Yunshu-Principal";
    public static final String TENANT_HEADER = "X-Yunshu-Tenant-Id";
    public static final String TIMESTAMP_HEADER = "X-Yunshu-Auth-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Yunshu-Auth-Signature";

    private final McpRequestAuthenticator authenticator;
    private final String hostId;

    public HmacYunshuHostContextResolver(
        McpRequestAuthenticator authenticator,
        @Value("${cpclaw.yunshu-skill.host-id:workbuddy}") String hostId
    ) {
        this.authenticator = authenticator;
        this.hostId = hostId == null || hostId.isBlank() ? "workbuddy" : hostId.trim();
    }

    @Override
    public String hostId() {
        return hostId;
    }

    @Override
    public Optional<YunshuHostContext> resolve(HttpServletRequest request) {
        try {
            PrincipalContext principal = authenticator.authenticate(
                hostId,
                request.getHeader(TENANT_HEADER),
                request.getHeader(PRINCIPAL_HEADER),
                request.getHeader(TIMESTAMP_HEADER),
                request.getHeader(SIGNATURE_HEADER));
            return Optional.of(new YunshuHostContext(
                principal.tenantId(), principal.principalId(), hostId, Map.of()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
