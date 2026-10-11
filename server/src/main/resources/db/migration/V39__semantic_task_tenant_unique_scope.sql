ALTER TABLE semantic_task_runs DROP INDEX uk_semantic_task_idempotency;
ALTER TABLE semantic_task_runs DROP INDEX uk_semantic_task_turn;
CREATE UNIQUE INDEX uk_semantic_task_tenant_idempotency
    ON semantic_task_runs(channel, installation_key, tenant_id, external_principal, client_request_id);
CREATE UNIQUE INDEX uk_semantic_task_tenant_turn
    ON semantic_task_runs(channel, installation_key, tenant_id, external_principal, turn_id);
