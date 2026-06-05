package com.cpclaw.metadata.dto;

public record MetadataSearchResult(
    String objectType,
    String objectId,
    String name,
    String code,
    String graphPath,
    String riskLevel,
    String reason
) {
}
