package com.cpclaw.metadata.graph.dto;

import java.util.List;

public record MetadataGraphNeighborhoodResponse(
    Node center,
    List<Node> nodes,
    List<Edge> edges,
    int depth,
    boolean truncated,
    int totalNeighborCount
) {
    public record Node(
        String id,
        String type,
        String objectType,
        String objectId,
        String appId,
        String appCode,
        String entityId,
        String name,
        String code,
        String confidence
    ) {
    }

    public record Edge(
        String id,
        String type,
        String label,
        String source,
        String target,
        String confidence,
        double weight,
        String sourceDataItemId
    ) {
    }
}
