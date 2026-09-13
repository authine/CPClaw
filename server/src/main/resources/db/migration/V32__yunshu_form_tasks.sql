CREATE TABLE IF NOT EXISTS yunshu_form_tasks (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    host_id VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    form_ref_json TEXT NOT NULL,
    metadata_version VARCHAR(128),
    state_version BIGINT NOT NULL,
    draft_version BIGINT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    draft_json TEXT NOT NULL,
    validation_json TEXT,
    plan_hash VARCHAR(128),
    idempotency_key VARCHAR(256) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_yunshu_form_task_idempotency (tenant_id, subject_id, host_id, idempotency_key),
    INDEX idx_yunshu_form_tasks_scope (tenant_id, subject_id, host_id, updated_at)
);

CREATE TABLE IF NOT EXISTS yunshu_form_task_revisions (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    draft_version BIGINT NOT NULL,
    state_version BIGINT NOT NULL,
    draft_json TEXT NOT NULL,
    validation_json TEXT,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_yunshu_form_task_revision (task_id, draft_version),
    INDEX idx_yunshu_form_task_revisions_task (task_id, created_at)
);
