package com.cpclaw.cloudpivot;

import java.util.List;
import java.util.Map;

public record CloudPivotRuntimeQueryResult(
    String schemaCode,
    long total,
    List<Map<String, Object>> records,
    String sourceEndpoint
) {
}
