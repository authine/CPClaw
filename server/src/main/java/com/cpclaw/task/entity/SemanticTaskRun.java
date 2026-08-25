package com.cpclaw.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "semantic_task_runs")
public class SemanticTaskRun {
    @Id private String id;
    @Column(nullable = false) private String channel;
    @Column(name = "installation_key") private String installationKey;
    @Column(name = "external_principal") private String externalPrincipal;
    @Column(name = "client_request_id") private String clientRequestId;
    @Column(name = "turn_id") private String turnId;
    @Column(name = "parent_task_id") private String parentTaskId;
    @Column(name = "continuation_consumed", nullable = false) private boolean continuationConsumed;
    @Column(nullable = false) private String status;
    @Lob @Column(name = "request_masked") private String requestMasked;
    @Lob @Column(name = "task_spec_json") private String taskSpecJson;
    @Lob @Column(name = "result_json") private String resultJson;
    @Lob @Column(name = "completion_json") private String completionJson;
    @Lob @Column(name = "evidence_json") private String evidenceJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getChannel() { return channel; } public void setChannel(String value) { channel = value; }
    public String getInstallationKey() { return installationKey; } public void setInstallationKey(String value) { installationKey = value; }
    public String getExternalPrincipal() { return externalPrincipal; } public void setExternalPrincipal(String value) { externalPrincipal = value; }
    public String getClientRequestId() { return clientRequestId; } public void setClientRequestId(String value) { clientRequestId = value; }
    public String getTurnId() { return turnId; } public void setTurnId(String value) { turnId = value; }
    public String getParentTaskId() { return parentTaskId; } public void setParentTaskId(String value) { parentTaskId = value; }
    public boolean isContinuationConsumed() { return continuationConsumed; } public void setContinuationConsumed(boolean value) { continuationConsumed = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getRequestMasked() { return requestMasked; } public void setRequestMasked(String value) { requestMasked = value; }
    public String getTaskSpecJson() { return taskSpecJson; } public void setTaskSpecJson(String value) { taskSpecJson = value; }
    public String getResultJson() { return resultJson; } public void setResultJson(String value) { resultJson = value; }
    public String getCompletionJson() { return completionJson; } public void setCompletionJson(String value) { completionJson = value; }
    public String getEvidenceJson() { return evidenceJson; } public void setEvidenceJson(String value) { evidenceJson = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
}
