package com.cpclaw.yunshu.protocol;

import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Development/CPClaw-host adapter. It accepts only the fixed host id and gets
 * the identity from the server authentication boundary, rather than request
 * body or external-user headers. External hosts must provide their own
 * verified resolver bean.
 */
@Component
public class CpClawServerHostContextResolver implements YunshuHostContextResolver {
    public static final String HOST_ID = "cpclaw-web";
    public static final String HOST_HEADER = "X-Yunshu-Host-Id";

    private final PrincipalContextService principalContextService;

    public CpClawServerHostContextResolver(PrincipalContextService principalContextService) {
        this.principalContextService = principalContextService;
    }

    @Override
    public String hostId() { return HOST_ID; }

    @Override
    public Optional<YunshuHostContext> resolve(HttpServletRequest request) {
        if (!HOST_ID.equals(normalize(request.getHeader(HOST_HEADER)))) return Optional.empty();
        // Do not let a caller turn server-owned CPClaw context into an external
        // identity assertion while the OIDC adapter has not been installed.
        if (hasText(request.getHeader("X-Yunshu-External-Principal"))
                || hasText(request.getHeader("X-Yunshu-Tenant-Id"))) return Optional.empty();
        PrincipalContext principal = principalContextService.current();
        if (!principal.authenticated() || principal.tenantId().isBlank() || principal.principalId().isBlank()) return Optional.empty();
        return Optional.of(new YunshuHostContext(principal.tenantId(), principal.principalId(), HOST_ID, Map.of()));
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
