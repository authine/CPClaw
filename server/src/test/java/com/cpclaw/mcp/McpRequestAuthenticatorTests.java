package com.cpclaw.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class McpRequestAuthenticatorTests {

    private final PrincipalContextService principals = new PrincipalContextService(
        "huangj", "黄杰", "18124691161", "default", false, "trusted");

    @Test
    void acceptsFreshHmacAndResolvesVerifiedPrincipal() {
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", "test-shared-secret", 300);
        long timestamp = Instant.now().getEpochSecond();
        String signature = authenticator.signature("CloudPivotMCP", "default", "workbuddy-user-1", timestamp);

        PrincipalContext result = authenticator.authenticate("CloudPivotMCP", "default", "workbuddy-user-1",
            Long.toString(timestamp), signature);

        assertEquals("workbuddy-user-1", result.principalId());
        assertEquals("default", result.tenantId());
        assertEquals(false, result.superAdmin());
    }

    @Test
    void signedTenantIsBoundToResolvedPrincipal() {
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", "test-shared-secret", 300);
        long timestamp = Instant.now().getEpochSecond();
        String signature = authenticator.signature("CloudPivotMCP", "tenant-b", "workbuddy-user-1", timestamp);
        PrincipalContext result = authenticator.authenticate("CloudPivotMCP", "tenant-b", "workbuddy-user-1", Long.toString(timestamp), signature);
        assertEquals("tenant-b", result.tenantId());
    }

    @Test
    void tenantChangeInvalidatesSignature() {
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", "test-shared-secret", 300);
        long timestamp = Instant.now().getEpochSecond();
        String signature = authenticator.signature("CloudPivotMCP", "tenant-a", "workbuddy-user-1", timestamp);
        assertThrows(McpAuthenticationException.class,
            () -> authenticator.authenticate("CloudPivotMCP", "tenant-b", "workbuddy-user-1", Long.toString(timestamp), signature));
    }

    @Test
    void rejectsMissingOrInvalidSignature() {
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", "test-shared-secret", 300);
        long timestamp = Instant.now().getEpochSecond();

        assertThrows(McpAuthenticationException.class,
            () -> authenticator.authenticate("CloudPivotMCP", "default", "workbuddy-user-1", Long.toString(timestamp), "bad"));
        assertThrows(McpAuthenticationException.class,
            () -> authenticator.authenticate("CloudPivotMCP", "default", "workbuddy-user-1", Long.toString(timestamp), null));
    }

    @Test
    void rejectsStaleSignature() {
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(principals, "hmac", "test-shared-secret", 30);
        long timestamp = Instant.now().minusSeconds(31).getEpochSecond();
        String signature = authenticator.signature("CloudPivotMCP", "default", "workbuddy-user-1", timestamp);

        assertThrows(McpAuthenticationException.class,
            () -> authenticator.authenticate("CloudPivotMCP", "default", "workbuddy-user-1", Long.toString(timestamp), signature));
    }

    @Test
    void disabledModeRetainsLocalDefaultWithoutTrustingArbitraryHeader() {
        PrincipalContextService defaultOnly = new PrincipalContextService(
            "huangj", "黄杰", "18124691161", "default", true, "default-only");
        McpRequestAuthenticator authenticator = new McpRequestAuthenticator(defaultOnly, "disabled", "", 300);

        PrincipalContext result = authenticator.authenticate("CloudPivotMCP", "default", "attacker", null, null);

        assertEquals("huangj", result.principalId());
        assertEquals(true, result.superAdmin());
    }

    @Test
    void hmacConfigurationRejectsMissingOrShortSharedSecret() {
        McpRequestAuthenticator missing = new McpRequestAuthenticator(principals, "hmac", "", 300);
        McpRequestAuthenticator shortSecret = new McpRequestAuthenticator(principals, "hmac", "short", 300);
        assertThrows(IllegalStateException.class, missing::validateConfiguration);
        assertThrows(IllegalStateException.class, shortSecret::validateConfiguration);
    }
}
