package com.cpclaw.identity;

/** Transitional identity boundary until login is introduced. */
public record PrincipalContext(String principalId, String username, String displayName, String phone, String tenantId, boolean authenticated, boolean superAdmin) {
    public static PrincipalContext defaultSystemUser() {
        return new PrincipalContext("huangj", "huangj", "黄杰", "18124691161", "default", true, true);
    }
}
