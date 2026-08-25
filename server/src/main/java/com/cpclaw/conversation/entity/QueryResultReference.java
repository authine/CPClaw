package com.cpclaw.conversation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "query_result_references")
public class QueryResultReference {

    @Id private String id;
    @Column(name = "conversation_id", nullable = false) private String conversationId;
    @Column(name = "message_id", nullable = false) private String messageId;
    @Column(name = "agent_run_id", nullable = false) private String agentRunId;
    @Column(name = "app_code", nullable = false) private String appCode;
    @Column(name = "schema_code", nullable = false) private String schemaCode;
    @Column(name = "record_id", nullable = false) private String recordId;
    @Column(name = "row_index", nullable = false) private int rowIndex;
    @Column(name = "display_snapshot_json", columnDefinition = "LONGTEXT") private String displaySnapshotJson;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getAgentRunId() { return agentRunId; }
    public void setAgentRunId(String agentRunId) { this.agentRunId = agentRunId; }
    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getSchemaCode() { return schemaCode; }
    public void setSchemaCode(String schemaCode) { this.schemaCode = schemaCode; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public String getDisplaySnapshotJson() { return displaySnapshotJson; }
    public void setDisplaySnapshotJson(String displaySnapshotJson) { this.displaySnapshotJson = displaySnapshotJson; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
