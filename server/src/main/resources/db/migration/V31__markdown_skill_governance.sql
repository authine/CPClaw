CREATE TABLE IF NOT EXISTS markdown_skills (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scope VARCHAR(512) NULL,
    executor_id VARCHAR(128) NOT NULL,
    requires_confirmation_for_write BOOLEAN NOT NULL DEFAULT TRUE,
    version VARCHAR(64) NOT NULL,
    source VARCHAR(512) NULL,
    markdown LONGTEXT NOT NULL,
    publication_status VARCHAR(32) NOT NULL DEFAULT 'draft',
    signature VARCHAR(128) NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    UNIQUE KEY uk_markdown_skill_version (id, version)
);
