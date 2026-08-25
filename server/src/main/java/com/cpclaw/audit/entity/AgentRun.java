package com.cpclaw.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRun {
    @Id private String id;
    @Column(name = "conversation_id") private String conversationId;
    @Column(name = "user_message_id") private String userMessageId;
    @Column(name = "assistant_message_id") private String assistantMessageId;
    @Column(name = "model_config_id") private String modelConfigId;
    @Column(name = "intent_summary") private String intentSummary;
    @Column(name = "business_intent") private String businessIntent;
    @Column(nullable = false) private String status;
    @Column(name = "plan_json", columnDefinition = "LONGTEXT") private String planJson;
    @Column(name = "reflection_json", columnDefinition = "LONGTEXT") private String reflectionJson;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "input_summary_masked", columnDefinition = "LONGTEXT") private String inputSummaryMasked;
    @Column(name = "output_summary_masked", columnDefinition = "LONGTEXT") private String outputSummaryMasked;
    @Column(name = "prompt_tokens") private Long promptTokens;
    @Column(name = "completion_tokens") private Long completionTokens;
    @Column(name = "cached_tokens") private Long cachedTokens;
    @Column(name = "total_tokens") private Long totalTokens;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "tool_call_count") private Integer toolCallCount;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserMessageId() { return userMessageId; }
    public void setUserMessageId(String userMessageId) { this.userMessageId = userMessageId; }
    public String getAssistantMessageId() { return assistantMessageId; }
    public void setAssistantMessageId(String assistantMessageId) { this.assistantMessageId = assistantMessageId; }
    public String getModelConfigId() { return modelConfigId; }
    public void setModelConfigId(String modelConfigId) { this.modelConfigId = modelConfigId; }
    public String getIntentSummary() { return intentSummary; }
    public void setIntentSummary(String intentSummary) { this.intentSummary = intentSummary; }
    public String getBusinessIntent() { return businessIntent; }
    public void setBusinessIntent(String businessIntent) { this.businessIntent = businessIntent; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getReflectionJson() { return reflectionJson; }
    public void setReflectionJson(String reflectionJson) { this.reflectionJson = reflectionJson; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getInputSummaryMasked() { return inputSummaryMasked; }
    public void setInputSummaryMasked(String inputSummaryMasked) { this.inputSummaryMasked = inputSummaryMasked; }
    public String getOutputSummaryMasked() { return outputSummaryMasked; }
    public void setOutputSummaryMasked(String outputSummaryMasked) { this.outputSummaryMasked = outputSummaryMasked; }
    public Long getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Long promptTokens) { this.promptTokens = promptTokens; }
    public Long getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Long completionTokens) { this.completionTokens = completionTokens; }
    public Long getCachedTokens() { return cachedTokens; }
    public void setCachedTokens(Long cachedTokens) { this.cachedTokens = cachedTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
