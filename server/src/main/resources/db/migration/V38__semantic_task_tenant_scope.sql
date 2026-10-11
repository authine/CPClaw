ALTER TABLE semantic_task_runs ADD COLUMN tenant_id VARCHAR(255) NOT NULL DEFAULT 'default';
CREATE INDEX idx_semantic_task_scope ON semantic_task_runs(channel, installation_key, tenant_id, external_principal, client_request_id);
