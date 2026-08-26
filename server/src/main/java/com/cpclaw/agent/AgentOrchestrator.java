package com.cpclaw.agent;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Deprecated compatibility facade for the legacy preview endpoint.
 * Runtime execution is owned exclusively by TaskGateway and registered skills.
 */
@Deprecated(forRemoval = true)
@Service
public class AgentOrchestrator {
    public Map<String, Object> previewPlaceholderPlan() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "deprecated");
        response.put("message", "请通过统一任务网关调用已注册 Skill；旧 Agent 预览入口仅保留兼容响应。");
        response.put("executionPath", "TaskGateway -> SkillRegistry -> SkillExecutor");
        return response;
    }
}
