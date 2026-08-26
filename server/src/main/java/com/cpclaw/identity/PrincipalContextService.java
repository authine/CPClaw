package com.cpclaw.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resolves the current user. It is deliberately replaceable by OIDC/session auth later. */
@Service
public class PrincipalContextService {
    private final PrincipalContext defaultPrincipal;
    private final String externalMode;

    public PrincipalContextService(
        @Value("${cpclaw.identity.default-id:huangj}") String id,
        @Value("${cpclaw.identity.default-name:黄杰}") String name,
        @Value("${cpclaw.identity.default-phone:18124691161}") String phone,
        @Value("${cpclaw.identity.default-tenant:default}") String tenant,
        @Value("${cpclaw.identity.default-super-admin:true}") boolean superAdmin,
        @Value("${cpclaw.identity.external-mode:default-only}") String externalMode
    ) {
        this.defaultPrincipal = new PrincipalContext(id, id, name, phone, tenant, true, superAdmin);
        this.externalMode = externalMode == null || externalMode.isBlank() ? "default-only" : externalMode.trim().toLowerCase();
    }

    public PrincipalContext current() { return defaultPrincipal; }

    public PrincipalContext resolveExternal(String externalPrincipal) {
        if (externalPrincipal == null || externalPrincipal.isBlank()) return defaultPrincipal;
        String value = externalPrincipal.trim();
        if (value.equals(defaultPrincipal.principalId()) || value.equals(defaultPrincipal.username())) return defaultPrincipal;
        // Until a trusted OIDC/JWT verifier is installed, an arbitrary host
        // header must never create an authenticated CPClaw principal. The
        // default-only mode keeps the current no-login deployment usable while
        // preventing impersonation through MCP/Remote headers.
        if ("default-only".equals(externalMode)) return defaultPrincipal;
        return new PrincipalContext(value, value, value, "", defaultPrincipal.tenantId(), true, false);
    }
}
