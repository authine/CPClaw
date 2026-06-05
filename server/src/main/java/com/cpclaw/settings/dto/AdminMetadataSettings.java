package com.cpclaw.settings.dto;

public record AdminMetadataSettings(
    String targetBaseUrl,
    String username,
    String searchEngineType,
    String searchEndpoint,
    boolean hasPassword
) {
}
