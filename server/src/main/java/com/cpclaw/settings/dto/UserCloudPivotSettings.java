package com.cpclaw.settings.dto;

public record UserCloudPivotSettings(
    String baseUrl,
    String username,
    boolean hasPassword
) {
}
