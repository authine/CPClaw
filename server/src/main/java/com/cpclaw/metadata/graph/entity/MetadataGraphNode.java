package com.cpclaw.metadata.graph.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "metadata_graph_nodes")
public class MetadataGraphNode {
    @Id private String id;
    @Column(name = "snapshot_id", nullable = false) private String snapshotId;
    @Column(name = "stable_key", nullable = false, length = 512) private String stableKey;
    @Column(name = "node_type", nullable = false) private String nodeType;
    @Column(name = "object_type", nullable = false) private String objectType;
    @Column(name = "object_id") private String objectId;
    @Column(name = "app_id") private String appId;
    @Column(name = "app_code") private String appCode;
    @Column(name = "entity_id") private String entityId;
    private String code;
    @Column(nullable = false, length = 512) private String name;
    @Column(nullable = false) private String confidence;
    @Column(nullable = false) private int community;
    @Column(name = "source_uri", length = 1024) private String sourceUri;
    @Column(name = "attributes_json", columnDefinition = "LONGTEXT") private String attributesJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
}
