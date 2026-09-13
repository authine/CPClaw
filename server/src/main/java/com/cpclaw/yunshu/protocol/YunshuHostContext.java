package com.cpclaw.yunshu.protocol;

import java.util.Map;

/** Trusted host and subject context supplied by an adapter authentication layer. */
public record YunshuHostContext(String tenantId, String subjectId, String hostId, Map<String, Boolean> capabilities) {
    public YunshuHostContext {
        tenantId = tenantId == null ? "" : tenantId.trim();
        subjectId = subjectId == null ? "" : subjectId.trim();
        hostId = hostId == null ? "" : hostId.trim();
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    }
}
