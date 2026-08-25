package com.cpclaw.memory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_memories")
public class AgentMemory {

    @Id private String id;
    @Column(name = "conversation_id") private String conversationId;
    @Column(name = "memory_scope", nullable = false) private String memoryScope = "SESSION";
    @Column(name = "owner_principal") private String ownerPrincipal;
    @Column(name = "tenant_id", nullable = false) private String tenantId = "default";
    @Column(nullable = false) private int priority;
    @Column(name = "memory_type", nullable = false) private String memoryType;
    @Column(name = "content_json", nullable = false, columnDefinition = "LONGTEXT") private String contentJson;
    @Column(name = "source_agent_run_id") private String sourceAgentRunId;
    @Column(nullable = false) private double confidence;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMemoryScope() { return memoryScope; }
    public void setMemoryScope(String memoryScope) { this.memoryScope = memoryScope; }
    public String getOwnerPrincipal() { return ownerPrincipal; }
    public void setOwnerPrincipal(String ownerPrincipal) { this.ownerPrincipal = ownerPrincipal; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }
    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
    public String getSourceAgentRunId() { return sourceAgentRunId; }
    public void setSourceAgentRunId(String sourceAgentRunId) { this.sourceAgentRunId = sourceAgentRunId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
