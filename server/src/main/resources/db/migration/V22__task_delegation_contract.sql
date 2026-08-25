ALTER TABLE semantic_task_runs ADD COLUMN turn_id VARCHAR(128) NULL;
ALTER TABLE semantic_task_runs ADD COLUMN task_spec_json LONGTEXT NULL;
ALTER TABLE semantic_task_runs ADD COLUMN completion_json LONGTEXT NULL;
ALTER TABLE semantic_task_runs ADD COLUMN evidence_json LONGTEXT NULL;
CREATE INDEX idx_semantic_task_turn ON semantic_task_runs(channel, installation_key, external_principal, turn_id);
CREATE UNIQUE INDEX uk_semantic_task_turn ON semantic_task_runs(channel, installation_key, external_principal, turn_id);
