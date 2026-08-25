package com.cpclaw.metadata.dto;

import java.util.List;
import java.util.Map;

public record WorkflowContractProbeResponse(
    int verifiedCount,
    int attemptedCount,
    String verifiedAt,
    List<Contract> contracts
) {
    public record Contract(String apiCode, boolean verified, String method, String path, Map<String, Object> responseShape, String error) {
    }
}
