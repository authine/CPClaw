package com.cpclaw.agent.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ExecutionStepDto(
    String title,
    String status,
    String phase,
    String state,
    String kind,
    Map<String, Object> data,
    long elapsedMs
) {

    public ExecutionStepDto {
        data = data == null || data.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        elapsedMs = Math.max(0L, elapsedMs);
    }

    public ExecutionStepDto(String title, String status) {
        this(title, status, null, null, null, Map.of(), 0L);
    }
}
