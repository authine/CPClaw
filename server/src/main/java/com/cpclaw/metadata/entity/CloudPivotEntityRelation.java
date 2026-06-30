package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cloudpivot_entity_relations")
public class CloudPivotEntityRelation {
    @Id private String id;
    @Column(name = "app_id", nullable = false) private String appId;
    @Column(name = "source_entity_id", nullable = false) private String sourceEntityId;
    @Column(name = "source_data_item_id") private String sourceDataItemId;
    @Column(name = "target_entity_id", nullable = false) private String targetEntityId;
    @Column(name = "relation_type") private String relationType;
    @Column(name = "relation_name") private String relationName;
    @Column(name = "raw_json", columnDefinition = "LONGTEXT") private String rawJson;
    @Column(name = "synced_at") private Instant syncedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getSourceEntityId() { return sourceEntityId; }
    public void setSourceEntityId(String sourceEntityId) { this.sourceEntityId = sourceEntityId; }
    public String getSourceDataItemId() { return sourceDataItemId; }
    public void setSourceDataItemId(String sourceDataItemId) { this.sourceDataItemId = sourceDataItemId; }
    public String getTargetEntityId() { return targetEntityId; }
    public void setTargetEntityId(String targetEntityId) { this.targetEntityId = targetEntityId; }
    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }
    public String getRelationName() { return relationName; }
    public void setRelationName(String relationName) { this.relationName = relationName; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
