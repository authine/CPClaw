CREATE TABLE query_result_references (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    agent_run_id VARCHAR(36) NOT NULL,
    app_code VARCHAR(128) NOT NULL,
    schema_code VARCHAR(128) NOT NULL,
    record_id VARCHAR(128) NOT NULL,
    row_index INT NOT NULL,
    display_snapshot_json LONGTEXT,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_query_result_reference_row (conversation_id, message_id, row_index),
    INDEX idx_query_result_reference_conversation_expiry (conversation_id, expires_at),
    INDEX idx_query_result_reference_agent_run (agent_run_id)
);
