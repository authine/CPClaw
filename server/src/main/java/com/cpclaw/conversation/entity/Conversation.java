package com.cpclaw.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "default_model_config_id")
    private String defaultModelConfigId;

    @Column(name = "default_thinking_enabled", nullable = false)
    private boolean defaultThinkingEnabled;

    @Column(name = "lifecycle_status", nullable = false, columnDefinition = "VARCHAR(24) DEFAULT 'COMPLETED'")
    private String lifecycleStatus = "DRAFT";

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean unread;

    @Column(name = "output_started_at")
    private Instant outputStartedAt;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDefaultModelConfigId() { return defaultModelConfigId; }
    public void setDefaultModelConfigId(String defaultModelConfigId) { this.defaultModelConfigId = defaultModelConfigId; }
    public boolean isDefaultThinkingEnabled() { return defaultThinkingEnabled; }
    public void setDefaultThinkingEnabled(boolean defaultThinkingEnabled) { this.defaultThinkingEnabled = defaultThinkingEnabled; }
    public String getLifecycleStatus() { return lifecycleStatus; }
    public void setLifecycleStatus(String lifecycleStatus) { this.lifecycleStatus = lifecycleStatus; }
    public boolean isUnread() { return unread; }
    public void setUnread(boolean unread) { this.unread = unread; }
    public Instant getOutputStartedAt() { return outputStartedAt; }
    public void setOutputStartedAt(Instant outputStartedAt) { this.outputStartedAt = outputStartedAt; }
    public Instant getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Instant lastReadAt) { this.lastReadAt = lastReadAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
