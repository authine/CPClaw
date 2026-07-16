package com.cpclaw.metadata.dto;

import java.util.List;

public record MetadataModelResponse(
    List<AppModel> apps,
    List<ApiAction> apiActions
) {
    public record AppModel(
        String id,
        String code,
        String name,
        String description,
        String syncedAt,
        List<EntityModel> entities
    ) {
    }

    public record EntityModel(
        String id,
        String appId,
        String code,
        String name,
        String type,
        String syncedAt,
        int dataItemCount,
        int relationCount,
        List<DataItemModel> dataItems,
        List<RelationModel> relations,
        List<ApiAction> apiActions
    ) {
    }

    public record DataItemModel(
        String id,
        String code,
        String name,
        String dataType,
        boolean required,
        boolean reference,
        String referenceEntityId,
        String referenceEntityCode,
        String referenceEntityName,
        String description,
        String syncedAt
    ) {
    }

    public record RelationModel(
        String id,
        String relationType,
        String relationName,
        String sourceEntityId,
        String sourceEntityCode,
        String sourceEntityName,
        String sourceDataItemId,
        String sourceDataItemCode,
        String sourceDataItemName,
        String targetEntityId,
        String targetEntityCode,
        String targetEntityName,
        String syncedAt
    ) {
    }

    public record ApiAction(
        String id,
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
        String syncedAt
    ) {
    }
}