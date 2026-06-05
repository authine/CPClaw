package com.cpclaw.agent;

import com.cpclaw.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;

    public AgentController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview() {
        return ApiResponse.ok(agentOrchestrator.previewPlaceholderPlan());
    }
}
