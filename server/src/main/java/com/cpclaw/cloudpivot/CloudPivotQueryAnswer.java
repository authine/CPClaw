package com.cpclaw.cloudpivot;

import java.util.List;
import java.util.Map;

public record CloudPivotQueryAnswer(
    String entityName,
    String schemaCode,
    long total,
    int returnedRecords,
    String answer,
    String sourceEndpoint,
    String actionSummary,
    String rawDataSummary,
    String conclusionSummary,
    List<Map<String, Object>> displayedRecords
) {
}
