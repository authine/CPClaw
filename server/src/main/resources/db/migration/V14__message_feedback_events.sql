CREATE TABLE IF NOT EXISTS message_feedback_events (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36) NOT NULL,
    agent_run_id VARCHAR(36),
    actor_type VARCHAR(64) NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    feedback_type VARCHAR(32),
    reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feedback_message_actor (message_id, actor_type, actor_id, created_at),
    INDEX idx_feedback_conversation (conversation_id, created_at),
    INDEX idx_feedback_type (feedback_type, created_at)
);
