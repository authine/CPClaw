package com.cpclaw.insight;

import com.cpclaw.insight.dto.InsightReportDto;
import java.util.List;
import java.util.Map;

public record InsightExecutionResult(
    String answer,
    InsightReportDto report,
    String primaryEntityName,
    String primarySchemaCode,
    long primaryCount,
    List<String> sourceEndpoints,
    String planSummary,
    Map<String, Object> evidence
) {
    public InsightExecutionResult(
        String answer,
        InsightReportDto report,
        String primaryEntityName,
        String primarySchemaCode,
        long primaryCount,
        List<String> sourceEndpoints,
        String planSummary
    ) {
        this(answer, report, primaryEntityName, primarySchemaCode, primaryCount, sourceEndpoints, planSummary, Map.of());
    }
}
