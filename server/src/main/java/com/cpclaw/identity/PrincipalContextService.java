package com.cpclaw.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resolves the current user. It is deliberately replaceable by OIDC/session auth later. */
@Service
public class PrincipalContextService {
    private final PrincipalContext defaultPrincipal;

    public PrincipalContextService(
        @Value("${cpclaw.identity.default-id:huangj}") String id,
        @Value("${cpclaw.identity.default-name:黄杰}") String name,
        @Value("${cpclaw.identity.default-phone:18124691161}") String phone,
        @Value("${cpclaw.identity.default-tenant:default}") String tenant,
        @Value("${cpclaw.identity.default-super-admin:true}") boolean superAdmin
    ) {
        this.defaultPrincipal = new PrincipalContext(id, id, name, phone, tenant, true, superAdmin);
    }

    public PrincipalContext current() { return defaultPrincipal; }

    public PrincipalContext resolveExternal(String externalPrincipal) {
        if (externalPrincipal == null || externalPrincipal.isBlank()) return defaultPrincipal;
        String value = externalPrincipal.trim();
        if (value.equals(defaultPrincipal.principalId()) || value.equals(defaultPrincipal.username())) return defaultPrincipal;
        return new PrincipalContext(value, value, value, "", defaultPrincipal.tenantId(), true, false);
    }
}
