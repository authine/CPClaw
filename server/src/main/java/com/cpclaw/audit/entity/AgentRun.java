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
    @Column(name = "intent_summary") private String intentSummary;
    @Column(nullable = false) private String status;
    @Column(name = "plan_json", columnDefinition = "LONGTEXT") private String planJson;
    @Column(name = "reflection_json", columnDefinition = "LONGTEXT") private String reflectionJson;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserMessageId() { return userMessageId; }
    public void setUserMessageId(String userMessageId) { this.userMessageId = userMessageId; }
    public String getIntentSummary() { return intentSummary; }
    public void setIntentSummary(String intentSummary) { this.intentSummary = intentSummary; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
    public String getReflectionJson() { return reflectionJson; }
    public void setReflectionJson(String reflectionJson) { this.reflectionJson = reflectionJson; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
