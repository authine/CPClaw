CREATE TABLE yunshu_operation_executions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(128) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    host_id VARCHAR(128) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    record_ref VARCHAR(255),
    request_json LONGTEXT NOT NULL,
    response_json LONGTEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_yunshu_operation_scope_key (tenant_id, subject_id, host_id, idempotency_key),
    KEY idx_yunshu_operation_task (task_id),
    KEY idx_yunshu_operation_status (status)
);
