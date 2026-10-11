CREATE TABLE user_feedback (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    subject_id VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    app_code VARCHAR(128),
    category VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    KEY idx_user_feedback_status (status),
    KEY idx_user_feedback_created (created_at)
);
