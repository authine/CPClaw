package com.cpclaw.cloudpivot;

import java.util.List;

public record CloudPivotMetadataSnapshot(
    List<AppMetadata> apps,
    List<EntityMetadata> entities,
    List<DataItemMetadata> dataItems,
    List<EntityRelationMetadata> relations,
    List<ApiEndpointMetadata> apiEndpoints
) {

    public CloudPivotMetadataSnapshot {
        apps = apps == null ? List.of() : apps;
        entities = entities == null ? List.of() : entities;
        dataItems = dataItems == null ? List.of() : dataItems;
        relations = relations == null ? List.of() : relations;
        apiEndpoints = apiEndpoints == null ? List.of() : apiEndpoints;
    }

    public CloudPivotMetadataSnapshot(List<AppMetadata> apps, List<EntityMetadata> entities) {
        this(apps, entities, List.of(), List.of(), List.of());
    }

    public CloudPivotMetadataSnapshot(List<AppMetadata> apps, List<EntityMetadata> entities, List<DataItemMetadata> dataItems, List<EntityRelationMetadata> relations) {
        this(apps, entities, dataItems, relations, List.of());
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

    public record ApiEndpointMetadata(
        String apiCode,
        String name,
        String method,
        String path,
        String category,
        String operationType,
        String riskLevel,
        boolean requiresConfirmation,
        String inputSchemaJson,
        String outputSchemaJson,
        String dataScope,
        String applicableObjectType,
        String rawJson
    ) {
    }
}
