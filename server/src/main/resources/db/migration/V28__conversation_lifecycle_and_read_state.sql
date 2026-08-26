ALTER TABLE conversations
    ADD COLUMN lifecycle_status VARCHAR(24) NOT NULL DEFAULT 'COMPLETED',
    ADD COLUMN unread BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN output_started_at TIMESTAMP NULL,
    ADD COLUMN last_read_at TIMESTAMP NULL;

UPDATE conversations
SET lifecycle_status = 'COMPLETED',
    unread = FALSE
WHERE lifecycle_status IS NULL OR lifecycle_status = '';

CREATE INDEX idx_conversations_lifecycle_updated_at
    ON conversations (lifecycle_status, updated_at);
