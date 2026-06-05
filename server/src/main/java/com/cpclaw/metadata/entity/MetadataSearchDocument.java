package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "metadata_search_documents")
public class MetadataSearchDocument {
    @Id private String id;
    @Column(name = "object_type", nullable = false) private String objectType;
    @Column(name = "object_id", nullable = false) private String objectId;
    @Column(name = "app_id") private String appId;
    @Column(name = "entity_id") private String entityId;
    @Column(nullable = false) private String name;
    private String code;
    @Column(name = "search_text", nullable = false, columnDefinition = "TEXT") private String searchText;
    @Column(name = "embedding_text", columnDefinition = "TEXT") private String embeddingText;
    @Column(name = "graph_path") private String graphPath;
    @Column(name = "risk_level") private String riskLevel;
    @Column(name = "sync_batch_id") private String syncBatchId;
    @Column(name = "indexed_at") private Instant indexedAt;
    @Column(name = "created_at") private Instant createdAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSearchText() { return searchText; }
    public void setSearchText(String searchText) { this.searchText = searchText; }
    public String getEmbeddingText() { return embeddingText; }
    public void setEmbeddingText(String embeddingText) { this.embeddingText = embeddingText; }
    public String getGraphPath() { return graphPath; }
    public void setGraphPath(String graphPath) { this.graphPath = graphPath; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public String getSyncBatchId() { return syncBatchId; }
    public void setSyncBatchId(String syncBatchId) { this.syncBatchId = syncBatchId; }
    public Instant getIndexedAt() { return indexedAt; }
    public void setIndexedAt(Instant indexedAt) { this.indexedAt = indexedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
