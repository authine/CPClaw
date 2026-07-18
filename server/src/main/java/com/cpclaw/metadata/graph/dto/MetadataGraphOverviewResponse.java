package com.cpclaw.metadata.graph.dto;

import java.util.List;
import java.util.Map;

public record MetadataGraphOverviewResponse(
    String provider,
    String status,
    String syncId,
    String generatedAt,
    int applicationCount,
    int coveredApplicationCount,
    double coverageRate,
    int nodeCount,
    int edgeCount,
    int unresolvedEdgeCount,
    Map<String, Integer> nodesByType,
    Map<String, Integer> edgesByType,
    List<ApplicationCoverage> applications,
    String exportPath
) {
    public record ApplicationCoverage(
        String appId,
        String appCode,
        String name,
        int entityCount,
        int dataItemCount,
        int edgeCount,
        double coverageRate,
        String status
    ) {
    }
}
