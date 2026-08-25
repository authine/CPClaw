package com.cpclaw.settings.dto;

public record SaveModelConfigRequest(
    String modelName,
    String modelApiBaseUrl,
    String modelApiKey,
    String modelDisplayName,
    boolean supportsThinking,
    boolean defaultThinkingEnabled
) {}
