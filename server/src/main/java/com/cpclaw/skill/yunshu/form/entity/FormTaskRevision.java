package com.cpclaw.skill.yunshu.form.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "yunshu_form_task_revisions")
public class FormTaskRevision {
    @Id private String id;
    @Column(name = "task_id", nullable = false) private String taskId;
    @Column(name = "draft_version", nullable = false) private long draftVersion;
    @Column(name = "state_version", nullable = false) private long stateVersion;
    @Lob @Column(name = "draft_json", nullable = false) private String draftJson;
    @Lob @Column(name = "validation_json") private String validationJson;
    @Column(nullable = false) private String source;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getTaskId() { return taskId; } public void setTaskId(String v) { taskId = v; }
    public long getDraftVersion() { return draftVersion; } public void setDraftVersion(long v) { draftVersion = v; }
    public long getStateVersion() { return stateVersion; } public void setStateVersion(long v) { stateVersion = v; }
    public String getDraftJson() { return draftJson; } public void setDraftJson(String v) { draftJson = v; }
    public String getValidationJson() { return validationJson; } public void setValidationJson(String v) { validationJson = v; }
    public String getSource() { return source; } public void setSource(String v) { source = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
}
