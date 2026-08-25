package com.cpclaw.cloudpivot;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record WorkflowContractProbeResult(List<Contract> contracts, Instant verifiedAt) {
    public record Contract(
        String apiCode,
        boolean verified,
        String method,
        String path,
        List<String> requestKeys,
        Map<String, Object> responseShape,
        String error
    ) {}
}
