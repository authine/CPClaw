package com.cpclaw.metadata.graph.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record GraphifyExportResponse(
    boolean directed,
    boolean multigraph,
    Map<String, Object> graph,
    List<Node> nodes,
    List<Link> links
) {
    public record Node(
        String id,
        String label,
        @JsonProperty("file_type") String fileType,
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("source_location") String sourceLocation,
        int community,
        @JsonProperty("node_type") String nodeType,
        @JsonProperty("object_type") String objectType,
        @JsonProperty("object_id") String objectId,
        @JsonProperty("app_code") String appCode,
        String code,
        String confidence,
        Map<String, Object> attributes
    ) {
    }

    public record Link(
        String relation,
        String confidence,
        @JsonProperty("source_file") String sourceFile,
        @JsonProperty("source_location") String sourceLocation,
        double weight,
        @JsonProperty("_src") String sourceAlias,
        @JsonProperty("_tgt") String targetAlias,
        String source,
        String target,
        String label,
        Map<String, Object> attributes
    ) {
    }
}
