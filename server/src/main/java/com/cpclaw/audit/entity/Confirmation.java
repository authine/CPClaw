package com.cpclaw.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "confirmations")
public class Confirmation {
    @Id private String id;
    @Column(name = "conversation_id") private String conversationId;
    @Column(name = "agent_run_id") private String agentRunId;
    @Column(name = "plan_id") private String planId;
    @Column(name = "risk_level", nullable = false) private String riskLevel;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String summary;
    @Column(name = "affected_objects_json", columnDefinition = "LONGTEXT") private String affectedObjectsJson;
    @Column(name = "changes_json_masked", columnDefinition = "LONGTEXT") private String changesJsonMasked;
    @Column(name = "attachments_json", columnDefinition = "LONGTEXT") private String attachmentsJson;
    @Column(nullable = false) private String status;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAffectedObjectsJson() { return affectedObjectsJson; }
    public void setAffectedObjectsJson(String affectedObjectsJson) { this.affectedObjectsJson = affectedObjectsJson; }
    public String getChangesJsonMasked() { return changesJsonMasked; }
    public void setChangesJsonMasked(String changesJsonMasked) { this.changesJsonMasked = changesJsonMasked; }
    public String getAttachmentsJson() { return attachmentsJson; }
    public void setAttachmentsJson(String attachmentsJson) { this.attachmentsJson = attachmentsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
}
