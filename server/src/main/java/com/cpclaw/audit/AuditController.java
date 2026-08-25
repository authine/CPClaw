package com.cpclaw.audit;

import com.cpclaw.common.api.ApiResponse;
import java.util.Map;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/analytics")
    public ApiResponse<Map<String, Object>> getAnalytics(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String intent,
        @RequestParam(required = false) String modelConfigId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(auditService.getAnalytics(parseInstant(from), parseInstant(to), status, intent, modelConfigId, page, size));
    }

    @GetMapping("/analytics/usage")
    public ApiResponse<Map<String, Object>> getUsageAnalytics(
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String modelConfigId
    ) {
        return ApiResponse.ok(auditService.getUsageDashboard(parseInstant(from), parseInstant(to), status, modelConfigId));
    }

    @PostMapping("/confirmations/{id}/confirm")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable String id) {
        return ApiResponse.ok(auditService.confirm(id));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("时间筛选必须使用 ISO-8601 格式");
        }
    }
}
