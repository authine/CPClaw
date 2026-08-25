package com.cpclaw.cloudpivot;

import java.util.List;
import java.util.Map;

public record WorkflowReadResult(
    String apiCode,
    String sourceEndpoint,
    long total,
    List<Map<String, Object>> items,
    Map<String, Object> responseShape
) {}
