package com.cpclaw.mcp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mcp_tool_call_audits")
public class McpToolCallAudit {
    @Id private String id;
    @Column(name = "installation_id") private String installationId;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(nullable = false) private String status;
    @Column(name = "summary_masked") private String summaryMasked;
    @Column(name = "created_at") private Instant createdAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummaryMasked() { return summaryMasked; }
    public void setSummaryMasked(String summaryMasked) { this.summaryMasked = summaryMasked; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
