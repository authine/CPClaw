CREATE TABLE agent_memories (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    memory_type VARCHAR(64) NOT NULL,
    content_json LONGTEXT NOT NULL,
    source_agent_run_id VARCHAR(36),
    confidence DECIMAL(5, 4) NOT NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_agent_memories_conversation (conversation_id, created_at),
    INDEX idx_agent_memories_expiry (expires_at)
);
