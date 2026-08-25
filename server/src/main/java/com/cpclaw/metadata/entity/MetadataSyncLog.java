package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "metadata_sync_logs")
public class MetadataSyncLog {
    @Id private String id;
    @Column(name = "sync_id", nullable = false) private String syncId;
    @Column(nullable = false) private String status;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "duration_ms") private Long durationMs;
    @Column(name = "app_count", nullable = false) private int appCount;
    @Column(name = "entity_count", nullable = false) private int entityCount;
    @Column(name = "data_item_count", nullable = false) private int dataItemCount;
    @Column(name = "relation_count", nullable = false) private int relationCount;
    @Column(name = "search_document_count", nullable = false) private int searchDocumentCount;
    @Column(name = "graph_node_count", nullable = false) private int graphNodeCount;
    @Column(name = "graph_edge_count", nullable = false) private int graphEdgeCount;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSyncId() { return syncId; }
    public void setSyncId(String syncId) { this.syncId = syncId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public int getAppCount() { return appCount; }
    public void setAppCount(int appCount) { this.appCount = appCount; }
    public int getEntityCount() { return entityCount; }
    public void setEntityCount(int entityCount) { this.entityCount = entityCount; }
    public int getDataItemCount() { return dataItemCount; }
    public void setDataItemCount(int dataItemCount) { this.dataItemCount = dataItemCount; }
    public int getRelationCount() { return relationCount; }
    public void setRelationCount(int relationCount) { this.relationCount = relationCount; }
    public int getSearchDocumentCount() { return searchDocumentCount; }
    public void setSearchDocumentCount(int searchDocumentCount) { this.searchDocumentCount = searchDocumentCount; }
    public int getGraphNodeCount() { return graphNodeCount; }
    public void setGraphNodeCount(int graphNodeCount) { this.graphNodeCount = graphNodeCount; }
    public int getGraphEdgeCount() { return graphEdgeCount; }
    public void setGraphEdgeCount(int graphEdgeCount) { this.graphEdgeCount = graphEdgeCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
