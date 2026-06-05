package com.cpclaw.metadata.dto;

public record MetadataAppSummary(
    String id,
    String code,
    String name,
    long entityCount,
    String syncedAt
) {
}
