ALTER TABLE confirmations ADD COLUMN plan_hash VARCHAR(64) NULL;
ALTER TABLE confirmations ADD COLUMN execution_started_at TIMESTAMP NULL;
CREATE INDEX idx_confirmations_status ON confirmations (status, expires_at);
