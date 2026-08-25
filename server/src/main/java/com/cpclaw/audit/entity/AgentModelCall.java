package com.cpclaw.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_model_calls")
public class AgentModelCall {
    @Id private String id;
    @Column(name = "agent_run_id", nullable = false) private String agentRunId;
    @Column(name = "model_config_id") private String modelConfigId;
    @Column(name = "model_name") private String modelName;
    @Column(nullable = false) private String operation;
    @Column(nullable = false) private String status;
    @Column(name = "input_summary_masked", columnDefinition = "LONGTEXT") private String inputSummaryMasked;
    @Column(name = "output_summary_masked", columnDefinition = "LONGTEXT") private String outputSummaryMasked;
    @Column(name = "error_message_masked", columnDefinition = "LONGTEXT") private String errorMessageMasked;
    @Column(name = "prompt_tokens") private Long promptTokens;
    @Column(name = "completion_tokens") private Long completionTokens;
    @Column(name = "cached_tokens") private Long cachedTokens;
    @Column(name = "total_tokens") private Long totalTokens;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getAgentRunId() { return agentRunId; } public void setAgentRunId(String value) { agentRunId = value; }
    public String getModelConfigId() { return modelConfigId; } public void setModelConfigId(String value) { modelConfigId = value; }
    public String getModelName() { return modelName; } public void setModelName(String value) { modelName = value; }
    public String getOperation() { return operation; } public void setOperation(String value) { operation = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getInputSummaryMasked() { return inputSummaryMasked; } public void setInputSummaryMasked(String value) { inputSummaryMasked = value; }
    public String getOutputSummaryMasked() { return outputSummaryMasked; } public void setOutputSummaryMasked(String value) { outputSummaryMasked = value; }
    public String getErrorMessageMasked() { return errorMessageMasked; } public void setErrorMessageMasked(String value) { errorMessageMasked = value; }
    public Long getPromptTokens() { return promptTokens; } public void setPromptTokens(Long value) { promptTokens = value; }
    public Long getCompletionTokens() { return completionTokens; } public void setCompletionTokens(Long value) { completionTokens = value; }
    public Long getCachedTokens() { return cachedTokens; } public void setCachedTokens(Long value) { cachedTokens = value; }
    public Long getTotalTokens() { return totalTokens; } public void setTotalTokens(Long value) { totalTokens = value; }
    public Long getDurationMs() { return durationMs; } public void setDurationMs(Long value) { durationMs = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
}
