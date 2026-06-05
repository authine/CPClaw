package com.cpclaw.settings.dto;

public record SaveUserSettingsRequest(
    String cloudPivotBaseUrl,
    String cloudPivotUsername,
    String cloudPivotPassword,
    String modelName,
    String modelApiBaseUrl,
    String modelApiKey,
    String modelDisplayName,
    boolean supportsThinking,
    boolean defaultThinkingEnabled
) {
}
