package com.cpclaw.settings.dto;

public record ModelConfigResponse(
    String id,
    String name,
    String apiBaseUrl,
    String modelName,
    boolean supportsThinking,
    boolean defaultThinkingEnabled,
    boolean enabled,
    boolean hasApiKey
) {
}
