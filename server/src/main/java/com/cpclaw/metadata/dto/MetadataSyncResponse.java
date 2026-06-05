package com.cpclaw.metadata.dto;

public record MetadataSyncResponse(
    String syncId,
    String status,
    int appCount,
    int entityCount,
    int searchDocumentCount,
    String createdAt
) {
}
