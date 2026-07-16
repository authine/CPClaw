package com.cpclaw.insight;

import com.cpclaw.insight.dto.InsightReportDto;
import java.util.List;

public record InsightExecutionResult(
    String answer,
    InsightReportDto report,
    String primaryEntityName,
    String primarySchemaCode,
    long primaryCount,
    List<String> sourceEndpoints,
    String planSummary
) {
}
