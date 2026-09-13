package com.cpclaw.skill.yunshu.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/** Durable aggregate state; JSON columns are a serialized, schema-versioned task snapshot. */
@Entity
@Table(name = "yunshu_form_tasks")
public class FormTask {
    @Id private String id;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "subject_id", nullable = false) private String subjectId;
    @Column(name = "host_id", nullable = false) private String hostId;
    @Column(nullable = false) private String status;
    @Lob @Column(name = "form_ref_json", nullable = false) private String formRefJson;
    @Column(name = "metadata_version") private String metadataVersion;
    @Column(name = "state_version", nullable = false) private long stateVersion;
    @Column(name = "draft_version", nullable = false) private long draftVersion;
    @Version @Column(name = "row_version", nullable = false) private long rowVersion;
    @Lob @Column(name = "draft_json", nullable = false) private String draftJson;
    @Lob @Column(name = "validation_json") private String validationJson;
    @Column(name = "plan_hash") private String planHash;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getTenantId() { return tenantId; } public void setTenantId(String v) { tenantId = v; }
    public String getSubjectId() { return subjectId; } public void setSubjectId(String v) { subjectId = v; }
    public String getHostId() { return hostId; } public void setHostId(String v) { hostId = v; }
    public String getStatus() { return status; } public void setStatus(String v) { status = v; }
    public String getFormRefJson() { return formRefJson; } public void setFormRefJson(String v) { formRefJson = v; }
    public String getMetadataVersion() { return metadataVersion; } public void setMetadataVersion(String v) { metadataVersion = v; }
    public long getStateVersion() { return stateVersion; } public void setStateVersion(long v) { stateVersion = v; }
    public long getDraftVersion() { return draftVersion; } public void setDraftVersion(long v) { draftVersion = v; }
    public long getRowVersion() { return rowVersion; } public void setRowVersion(long v) { rowVersion = v; }
    public String getDraftJson() { return draftJson; } public void setDraftJson(String v) { draftJson = v; }
    public String getValidationJson() { return validationJson; } public void setValidationJson(String v) { validationJson = v; }
    public String getPlanHash() { return planHash; } public void setPlanHash(String v) { planHash = v; }
    public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String v) { idempotencyKey = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt = v; }
}
