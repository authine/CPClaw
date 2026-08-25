CREATE TABLE metadata_sync_logs (
    id VARCHAR(64) PRIMARY KEY,
    sync_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    duration_ms BIGINT,
    app_count INT NOT NULL DEFAULT 0,
    entity_count INT NOT NULL DEFAULT 0,
    data_item_count INT NOT NULL DEFAULT 0,
    relation_count INT NOT NULL DEFAULT 0,
    search_document_count INT NOT NULL DEFAULT 0,
    graph_node_count INT NOT NULL DEFAULT 0,
    graph_edge_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    INDEX idx_metadata_sync_logs_time (started_at DESC),
    INDEX idx_metadata_sync_logs_status_time (status, completed_at DESC)
);

-- Preserve the useful history already available from graph snapshots. New
-- records additionally retain failures and the metadata object counts.
INSERT INTO metadata_sync_logs (
    id, sync_id, status, started_at, completed_at, duration_ms,
    app_count, graph_node_count, graph_edge_count, error_message
)
SELECT
    id,
    source_sync_id,
    CASE WHEN completed_at IS NOT NULL THEN 'succeeded' ELSE 'incomplete' END,
    started_at,
    completed_at,
    NULL,
    application_count,
    node_count,
    edge_count,
    error_message
FROM metadata_graph_snapshots;
