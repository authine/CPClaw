package com.cpclaw.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public interface ModelGateway {

    Map<String, Object> testModel(String modelConfigId);

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
