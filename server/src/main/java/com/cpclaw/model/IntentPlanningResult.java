package com.cpclaw.model;

import java.util.List;
import java.util.Map;

public record IntentPlanningResult(
    String intent,
    String actionLabel,
    String businessObject,
    String dimension,
    String filters,
    List<String> analysisDimensions,
    List<String> fieldHints,
    List<String> relationHints,
    String apiOperation,
    List<Map<String, Object>> executionSteps,
    List<Map<String, Object>> runtimeFilters,
    List<String> metricFieldCodes,
    List<String> groupByFieldCodes,
    List<Map<String, Object>> sortFields,
    int resultLimit,
    boolean requiresConfirmation,
    String reasoning,
    boolean clarificationNeeded,
    double confidence
) {
    public static IntentPlanningResult empty() {
        return new IntentPlanningResult("", "", "", "", "", List.of(), List.of(), List.of(), "", List.of(), List.of(), List.of(), List.of(), List.of(), 0, false, "", false, 0D);
    }
}
