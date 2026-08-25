ALTER TABLE semantic_task_runs ADD COLUMN parent_task_id VARCHAR(36) NULL;
ALTER TABLE semantic_task_runs ADD COLUMN continuation_consumed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_semantic_task_parent ON semantic_task_runs(parent_task_id);
