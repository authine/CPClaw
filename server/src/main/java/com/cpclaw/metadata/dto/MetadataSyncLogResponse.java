package com.cpclaw.metadata.dto;

public record MetadataSyncLogResponse(
    String id,
    String syncId,
    String status,
    String startedAt,
    String completedAt,
    Long durationMs,
    int appCount,
    int entityCount,
    int dataItemCount,
    int relationCount,
    boolean detailedCountsRecorded,
    int searchDocumentCount,
    int graphNodeCount,
    int graphEdgeCount,
    String errorMessage
) {
}
