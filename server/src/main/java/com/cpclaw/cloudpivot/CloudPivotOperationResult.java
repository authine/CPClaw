package com.cpclaw.cloudpivot;

import java.util.Map;

public record CloudPivotOperationResult(
    boolean success,
    String operation,
    String appCode,
    String schemaCode,
    String bizObjectId,
    String endpoint,
    String message,
    Map<String, Object> responseSummary
) {
}
