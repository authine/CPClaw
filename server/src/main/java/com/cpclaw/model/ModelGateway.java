package com.cpclaw.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public interface ModelGateway {

    Map<String, Object> testModel(String modelConfigId);

    /**
     * Tests a model configuration supplied for the current request only. Implementations
     * must not persist or log the credential supplied through this method.
     */
    default Map<String, Object> testUnsavedModel(String modelName, String apiBaseUrl, String apiKey) {
        return Map.of("success", false, "message", "当前模型网关不支持新增前连接验证", "latencyMs", 0L);
    }

    Optional<String> analyzeRecords(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled
    );

    default Optional<String> analyzeRecords(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled,
        Map<String, Object> reasoningContext
    ) {
        return analyzeRecords(preferredModelConfigId, userQuestion, entityName, total, records, thinkingEnabled);
    }

    default Optional<IntentPlanningResult> planIntent(
        String preferredModelConfigId,
        Map<String, Object> planningContext,
        boolean thinkingEnabled
    ) {
        return Optional.empty();
    }

    /**
     * Routes non-obvious user input before metadata lookup. Implementations must return
     * only conversation/task/clarify and must not authorize a tool call.
     */
    default Optional<ConversationRouteResult> routeConversation(
        String preferredModelConfigId,
        String userGoal,
        List<String> recentMessages,
        boolean thinkingEnabled
    ) {
        return Optional.empty();
    }

    /** Generates a direct answer after the server has ruled out every registered Skill. */
    default Optional<String> answerGeneralConversation(
        String preferredModelConfigId,
        String userGoal,
        List<String> recentMessages,
        boolean thinkingEnabled
    ) {
        return Optional.empty();
    }

    default Optional<String> analyzeRecordsStream(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled,
        Map<String, Object> reasoningContext,
        Consumer<String> chunkConsumer
    ) {
        Optional<String> result = analyzeRecords(
            preferredModelConfigId,
            userQuestion,
            entityName,
            total,
            records,
            thinkingEnabled,
            reasoningContext
        );
        result.ifPresent(value -> {
            if (chunkConsumer != null && !value.isEmpty()) {
                chunkConsumer.accept(value);
            }
        });
        return result;
    }
}
