package com.cpclaw.model;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleModelGateway implements ModelGateway {

    @Override
    public Map<String, Object> testModel(String modelConfigId) {
        return Map.of("modelConfigId", modelConfigId, "status", "openai-compatible-placeholder");
    }
}
