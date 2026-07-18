package com.cpclaw.metadata.graph.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "metadata_graph_edges")
public class MetadataGraphEdge {
    @Id private String id;
    @Column(name = "snapshot_id", nullable = false) private String snapshotId;
    @Column(name = "stable_key", nullable = false, length = 512) private String stableKey;
    @Column(name = "edge_type", nullable = false) private String edgeType;
    @Column(length = 512) private String label;
    @Column(name = "source_node_key", nullable = false, length = 512) private String sourceNodeKey;
    @Column(name = "target_node_key", nullable = false, length = 512) private String targetNodeKey;
    @Column(nullable = false) private String confidence;
    @Column(nullable = false) private BigDecimal weight;
    @Column(name = "source_data_item_id") private String sourceDataItemId;
    @Column(name = "source_uri", length = 1024) private String sourceUri;
    @Column(name = "attributes_json", columnDefinition = "LONGTEXT") private String attributesJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
