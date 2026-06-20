package com.cpclaw.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ModelGateway {

    Map<String, Object> testModel(String modelConfigId);

    Optional<String> analyzeRecords(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled
    );
}
