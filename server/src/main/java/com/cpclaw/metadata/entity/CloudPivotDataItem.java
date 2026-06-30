package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cloudpivot_data_items")
public class CloudPivotDataItem {
    @Id private String id;
    @Column(name = "entity_id", nullable = false) private String entityId;
    @Column(name = "data_item_code", nullable = false) private String dataItemCode;
    @Column(nullable = false) private String name;
    @Column(name = "data_type") private String dataType;
    @Column(nullable = false) private boolean required;
    @Column(name = "is_reference", nullable = false) private boolean reference;
    @Column(name = "reference_entity_id") private String referenceEntityId;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "raw_json", columnDefinition = "LONGTEXT") private String rawJson;
    @Column(name = "synced_at") private Instant syncedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDataItemCode() { return dataItemCode; }
    public void setDataItemCode(String dataItemCode) { this.dataItemCode = dataItemCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isReference() { return reference; }
    public void setReference(boolean reference) { this.reference = reference; }
    public String getReferenceEntityId() { return referenceEntityId; }
    public void setReferenceEntityId(String referenceEntityId) { this.referenceEntityId = referenceEntityId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
