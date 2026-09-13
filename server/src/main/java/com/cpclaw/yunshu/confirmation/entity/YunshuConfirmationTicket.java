package com.cpclaw.yunshu.confirmation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Durable, one-time confirmation ticket state. Never store the signed token itself. */
@Entity
@Table(name = "yunshu_confirmation_tickets")
public class YunshuConfirmationTicket {
    @Id private String id;
    @Column(name = "task_id", nullable = false) private String taskId;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "subject_id", nullable = false) private String subjectId;
    @Column(name = "host_id", nullable = false) private String hostId;
    @Column(name = "skill_version", nullable = false) private String skillVersion;
    @Column(name = "form_ref", nullable = false) private String formRef;
    @Column(name = "metadata_version", nullable = false) private String metadataVersion;
    @Column(name = "state_version", nullable = false) private long stateVersion;
    @Column(name = "draft_version", nullable = false) private long draftVersion;
    @Column(name = "plan_hash", nullable = false) private String planHash;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(nullable = false) private String status;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getTaskId() { return taskId; } public void setTaskId(String v) { taskId = v; }
    public String getTenantId() { return tenantId; } public void setTenantId(String v) { tenantId = v; }
    public String getSubjectId() { return subjectId; } public void setSubjectId(String v) { subjectId = v; }
    public String getHostId() { return hostId; } public void setHostId(String v) { hostId = v; }
    public String getSkillVersion() { return skillVersion; } public void setSkillVersion(String v) { skillVersion = v; }
    public String getFormRef() { return formRef; } public void setFormRef(String v) { formRef = v; }
    public String getMetadataVersion() { return metadataVersion; } public void setMetadataVersion(String v) { metadataVersion = v; }
    public long getStateVersion() { return stateVersion; } public void setStateVersion(long v) { stateVersion = v; }
    public long getDraftVersion() { return draftVersion; } public void setDraftVersion(long v) { draftVersion = v; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String v) { planHash = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant v) { expiresAt = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getConsumedAt() { return consumedAt; } public void setConsumedAt(Instant v) { consumedAt = v; }
    public long getRowVersion() { return rowVersion; }
}
