package com.cpclaw.metadata.dto;

public record MetadataSyncResponse(
    String syncId,
    String status,
    int appCount,
    int entityCount,
    int dataItemCount,
    int relationCount,
    int searchDocumentCount,
    String createdAt
) {
}
