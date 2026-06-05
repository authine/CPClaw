package com.cpclaw.settings.dto;

public record SaveAdminSettingsRequest(
    String targetBaseUrl,
    String username,
    String password,
    String searchEngineType,
    String searchEndpoint
) {
}
