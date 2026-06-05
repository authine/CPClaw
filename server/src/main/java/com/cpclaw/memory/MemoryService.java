package com.cpclaw.memory;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

    public List<Map<String, Object>> recall(String conversationId) {
        return List.of(Map.of("conversationId", conversationId, "status", "memory-placeholder"));
    }
}
