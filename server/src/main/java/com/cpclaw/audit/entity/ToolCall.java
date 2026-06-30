package com.cpclaw.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tool_calls")
public class ToolCall {
    @Id private String id;
    @Column(name = "agent_run_id", nullable = false) private String agentRunId;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(name = "input_json_masked", columnDefinition = "LONGTEXT") private String inputJsonMasked;
    @Column(name = "output_json_masked", columnDefinition = "LONGTEXT") private String outputJsonMasked;
    @Column(nullable = false) private String status;
    @Column(name = "error_message_masked", columnDefinition = "LONGTEXT") private String errorMessageMasked;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getInputJsonMasked() { return inputJsonMasked; }
    public void setInputJsonMasked(String inputJsonMasked) { this.inputJsonMasked = inputJsonMasked; }
    public String getOutputJsonMasked() { return outputJsonMasked; }
    public void setOutputJsonMasked(String outputJsonMasked) { this.outputJsonMasked = outputJsonMasked; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessageMasked() { return errorMessageMasked; }
    public void setErrorMessageMasked(String errorMessageMasked) { this.errorMessageMasked = errorMessageMasked; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
