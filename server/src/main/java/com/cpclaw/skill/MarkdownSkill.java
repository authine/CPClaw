package com.cpclaw.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "markdown_skills")
public class MarkdownSkill {
    @Id private String id;
    @Column(nullable = false) private String name;
    @Column private String scope;
    @Column(name = "executor_id", nullable = false) private String executorId;
    @Column(name = "requires_confirmation_for_write", nullable = false) private boolean requiresConfirmationForWrite;
    @Column(nullable = false) private String version;
    @Column private String source;
    @Column(columnDefinition = "LONGTEXT", nullable = false) private String markdown;
    @Column(name = "publication_status", nullable = false) private String publicationStatus;
    @Column private String signature;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String value) { id = value; }
    public String getName() { return name; }
    public void setName(String value) { name = value; }
    public String getScope() { return scope; }
    public void setScope(String value) { scope = value; }
    public String getExecutorId() { return executorId; }
    public void setExecutorId(String value) { executorId = value; }
    public boolean isRequiresConfirmationForWrite() { return requiresConfirmationForWrite; }
    public void setRequiresConfirmationForWrite(boolean value) { requiresConfirmationForWrite = value; }
    public String getVersion() { return version; }
    public void setVersion(String value) { version = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public String getMarkdown() { return markdown; }
    public void setMarkdown(String value) { markdown = value; }
    public String getPublicationStatus() { return publicationStatus; }
    public void setPublicationStatus(String value) { publicationStatus = value; }
    public String getSignature() { return signature; }
    public void setSignature(String value) { signature = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { updatedAt = value; }
}
