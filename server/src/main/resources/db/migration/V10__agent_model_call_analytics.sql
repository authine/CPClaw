ALTER TABLE agent_runs ADD COLUMN model_config_id VARCHAR(36) NULL;
ALTER TABLE agent_runs ADD COLUMN assistant_message_id VARCHAR(36) NULL;
ALTER TABLE agent_runs ADD COLUMN input_summary_masked LONGTEXT NULL;
ALTER TABLE agent_runs ADD COLUMN output_summary_masked LONGTEXT NULL;
ALTER TABLE agent_runs ADD COLUMN prompt_tokens BIGINT NULL;
ALTER TABLE agent_runs ADD COLUMN completion_tokens BIGINT NULL;
ALTER TABLE agent_runs ADD COLUMN total_tokens BIGINT NULL;
ALTER TABLE agent_runs ADD COLUMN duration_ms BIGINT NULL;
ALTER TABLE agent_runs ADD COLUMN tool_call_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS agent_model_calls (
    id VARCHAR(36) PRIMARY KEY,
    agent_run_id VARCHAR(36) NOT NULL,
    model_config_id VARCHAR(36),
    model_name VARCHAR(255),
    operation VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    input_summary_masked LONGTEXT,
    output_summary_masked LONGTEXT,
    error_message_masked LONGTEXT,
    prompt_tokens BIGINT,
    completion_tokens BIGINT,
    total_tokens BIGINT,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_agent_model_calls_created_at (created_at),
    INDEX idx_agent_model_calls_agent_run (agent_run_id),
    INDEX idx_agent_model_calls_model_status (model_config_id, status)
);

CREATE INDEX idx_agent_runs_created_at ON agent_runs (created_at);
CREATE INDEX idx_agent_runs_model_status ON agent_runs (model_config_id, status);
