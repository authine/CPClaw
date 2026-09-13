package com.cpclaw.skill.yunshu.runtime;

import java.util.Map;

/** No result may be treated as a successful CloudPivot write unless contractVerified and completed are true. */
public record ProviderOperationResult(boolean contractVerified, boolean completed, boolean unknown,
        String operationId, String recordRef, String message, Map<String, Object> evidence) {
    public ProviderOperationResult { evidence = evidence == null ? Map.of() : Map.copyOf(evidence); }
    public static ProviderOperationResult unverified(String operation, String message) {
        return new ProviderOperationResult(false, false, false, null, null, message, Map.of("operation", operation));
    }
}
