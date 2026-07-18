package com.cpclaw.metadata.graph.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "metadata_graph_snapshots")
public class MetadataGraphSnapshot {
    @Id private String id;
    @Column(name = "source_sync_id", nullable = false) private String sourceSyncId;
    @Column(nullable = false) private String provider;
    @Column(nullable = false) private String status;
    @Column(name = "application_count", nullable = false) private int applicationCount;
    @Column(name = "node_count", nullable = false) private int nodeCount;
    @Column(name = "edge_count", nullable = false) private int edgeCount;
    @Column(name = "unresolved_edge_count", nullable = false) private int unresolvedEdgeCount;
    @Column(name = "coverage_rate", nullable = false) private BigDecimal coverageRate;
    @Column(name = "export_path", length = 1024) private String exportPath;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
}
