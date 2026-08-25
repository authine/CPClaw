package com.cpclaw.settings.dto;

public record UserCloudPivotSettings(
    boolean environmentConfigured,
    String username,
    boolean hasPassword,
    String credentialStatus
) {
}
