ALTER TABLE agent_runs ADD COLUMN business_intent VARCHAR(500) NULL;
CREATE INDEX idx_agent_runs_business_intent ON agent_runs (business_intent);
