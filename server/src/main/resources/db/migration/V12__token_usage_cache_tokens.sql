ALTER TABLE agent_runs ADD COLUMN cached_tokens BIGINT NULL;
ALTER TABLE agent_model_calls ADD COLUMN cached_tokens BIGINT NULL;
