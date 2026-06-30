package com.cpclaw.cloudpivot;

import java.util.List;

public record CloudPivotMetadataSnapshot(
    List<AppMetadata> apps,
    List<EntityMetadata> entities,
    List<DataItemMetadata> dataItems,
    List<EntityRelationMetadata> relations
) {

    public CloudPivotMetadataSnapshot {
        apps = apps == null ? List.of() : apps;
        entities = entities == null ? List.of() : entities;
        dataItems = dataItems == null ? List.of() : dataItems;
        relations = relations == null ? List.of() : relations;
    }

    public CloudPivotMetadataSnapshot(List<AppMetadata> apps, List<EntityMetadata> entities) {
        this(apps, entities, List.of(), List.of());
    }

    public record AppMetadata(String code, String name, String description) {
    }

    public record EntityMetadata(String appCode, String code, String name, String type, String riskLevel) {
    }

    public record DataItemMetadata(
        String appCode,
        String entityCode,
        String code,
        String name,
        String dataType,
        boolean required,
        boolean reference,
        String referenceEntityCode,
        String description,
        String rawJson
    ) {
    }

    public record EntityRelationMetadata(
        String appCode,
        String sourceEntityCode,
        String sourceDataItemCode,
        String targetEntityCode,
        String relationType,
        String relationName,
        String rawJson
    ) {
    }
}
