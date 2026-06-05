package com.cpclaw.audit;

import com.cpclaw.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/agent-runs/{id}")
    public ApiResponse<Map<String, Object>> getAgentRun(@PathVariable String id) {
        return ApiResponse.ok(auditService.getAgentRunPlaceholder(id));
    }

    @PostMapping("/confirmations/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable String id) {
        return ApiResponse.ok(auditService.confirm(id));
    }
}
