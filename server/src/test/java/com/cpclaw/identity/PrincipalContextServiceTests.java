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
}
