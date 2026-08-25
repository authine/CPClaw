package com.cpclaw.mcp;

import com.cpclaw.mcp.entity.McpToolCallAudit;
import com.cpclaw.mcp.entity.McpInstallation;
import com.cpclaw.mcp.repository.McpInstallationRepository;
import com.cpclaw.mcp.repository.McpToolCallAuditRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpAuditService {
    private final McpToolCallAuditRepository auditRepository;
    private final McpInstallationRepository installationRepository;
    public McpAuditService(McpToolCallAuditRepository auditRepository, McpInstallationRepository installationRepository) {
        this.auditRepository = auditRepository;
        this.installationRepository = installationRepository;
    }
    @Transactional
    public void record(String installationKey, String toolName, String status, String summary) {
        McpToolCallAudit audit = new McpToolCallAudit();
        audit.setId(UUID.randomUUID().toString());
        audit.setInstallationId(installationRepository.findByInstallationKey(installationKey).map(McpInstallation::getId).orElse(null));
        audit.setToolName(limit(toolName, 128));
        audit.setStatus(limit(status, 32));
        audit.setSummaryMasked(limit(mask(summary), 1024));
        audit.setCreatedAt(Instant.now());
        auditRepository.save(audit);
    }
    private String mask(String value) {
        if (value == null) return "";
        return value
            .replaceAll("(?i)(password|token|cookie|secret|authorization)\\s*[:=]\\s*[^,\\s]+", "$1=***")
            .replaceAll("(?i)(\\\"(?:password|token|cookie|secret|authorization)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")", "$1***$2")
            .replaceAll("(?i)(密码|口令)\\s*[:：]\\s*[^,，\\s]+", "$1=***");
    }
    private String limit(String value, int max) { String safe = value == null ? "" : value; return safe.length() <= max ? safe : safe.substring(0, max); }
}
