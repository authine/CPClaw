CREATE TABLE semantic_task_runs (
    id VARCHAR(36) PRIMARY KEY,
    channel VARCHAR(32) NOT NULL,
    installation_key VARCHAR(64),
    external_principal VARCHAR(255),
    client_request_id VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    request_masked TEXT,
    result_json LONGTEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    UNIQUE KEY uk_semantic_task_idempotency (channel, installation_key, external_principal, client_request_id),
    INDEX idx_semantic_task_status_time (status, updated_at DESC)
);

CREATE TABLE semantic_task_events (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    event_sequence BIGINT NOT NULL,
    event_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_semantic_task_event_sequence (task_id, event_sequence),
    INDEX idx_semantic_task_events_task (task_id, event_sequence)
);
