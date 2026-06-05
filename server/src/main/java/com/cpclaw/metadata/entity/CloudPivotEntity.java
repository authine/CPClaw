package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cloudpivot_entities")
public class CloudPivotEntity {
    @Id private String id;
    @Column(name = "app_id", nullable = false) private String appId;
    @Column(name = "entity_code", nullable = false) private String entityCode;
    @Column(nullable = false) private String name;
    @Column(name = "entity_type") private String entityType;
    @Column(name = "raw_json", columnDefinition = "TEXT") private String rawJson;
    @Column(name = "synced_at") private Instant syncedAt;
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getEntityCode() { return entityCode; }
    public void setEntityCode(String entityCode) { this.entityCode = entityCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
