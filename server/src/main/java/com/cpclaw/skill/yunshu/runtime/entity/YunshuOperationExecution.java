package com.cpclaw.skill.yunshu.runtime.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "yunshu_operation_executions")
public class YunshuOperationExecution {
    @Id private String id;
    @Column(name = "task_id", nullable = false) private String taskId;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "subject_id", nullable = false) private String subjectId;
    @Column(name = "host_id", nullable = false) private String hostId;
    @Column(nullable = false) private String operation;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(nullable = false) private String status;
    @Column(name = "record_ref") private String recordRef;
    @Column(name = "request_json", nullable = false, columnDefinition = "LONGTEXT") private String requestJson;
    @Column(name = "response_json", columnDefinition = "LONGTEXT") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "reconciliation_owner", length = 128) private String reconciliationOwner;
    @Column(name = "reconciliation_claimed_at") private Instant reconciliationClaimedAt;
    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getTaskId() { return taskId; } public void setTaskId(String v) { taskId = v; }
    public String getTenantId() { return tenantId; } public void setTenantId(String v) { tenantId = v; }
    public String getSubjectId() { return subjectId; } public void setSubjectId(String v) { subjectId = v; }
    public String getHostId() { return hostId; } public void setHostId(String v) { hostId = v; }
    public String getOperation() { return operation; } public void setOperation(String v) { operation = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getRecordRef() { return recordRef; } public void setRecordRef(String v) { recordRef = v; }
    public String getRequestJson() { return requestJson; } public void setRequestJson(String v) { requestJson = v; }
    public String getResponseJson() { return responseJson; } public void setResponseJson(String v) { responseJson = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
    public String getReconciliationOwner() { return reconciliationOwner; } public void setReconciliationOwner(String v) { reconciliationOwner = v; }
    public Instant getReconciliationClaimedAt() { return reconciliationClaimedAt; } public void setReconciliationClaimedAt(Instant v) { reconciliationClaimedAt = v; }
}
