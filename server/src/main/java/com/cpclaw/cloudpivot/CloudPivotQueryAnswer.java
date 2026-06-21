package com.cpclaw.cloudpivot;

public record CloudPivotQueryAnswer(
    String entityName,
    String schemaCode,
    long total,
    int returnedRecords,
    String answer,
    String sourceEndpoint,
    String actionSummary,
    String rawDataSummary,
    String conclusionSummary
) {
}
