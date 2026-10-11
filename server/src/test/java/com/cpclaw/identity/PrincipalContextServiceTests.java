package com.cpclaw.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class PrincipalContextServiceTests {
    @Test
    void defaultOnlyModeDoesNotTrustArbitraryExternalHeader() {
        PrincipalContextService service = new PrincipalContextService("huangj", "黄杰", "18124691161", "default", true, "default-only");
        PrincipalContext resolved = service.resolveExternal("attacker");
        assertEquals("huangj", resolved.principalId());
        assertFalse(!resolved.superAdmin() && resolved.authenticated());
    }

    @Test
    void authenticatedExternalIdentityIsNotCollapsedToDefaultOnlyPrincipal() {
        PrincipalContextService service = new PrincipalContextService("huangj", "黄杰", "18124691161", "default", true, "default-only");
        PrincipalContext resolved = service.resolveAuthenticatedExternal("workbuddy-user-1");
        assertEquals("workbuddy-user-1", resolved.principalId());
        assertEquals("default", resolved.tenantId());
        assertFalse(resolved.superAdmin());
    }
}
