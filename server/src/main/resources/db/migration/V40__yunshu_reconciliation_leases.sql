ALTER TABLE yunshu_operation_executions
    ADD COLUMN reconciliation_owner VARCHAR(128) NULL,
    ADD COLUMN reconciliation_claimed_at TIMESTAMP(6) NULL,
    ADD KEY idx_yunshu_reconciliation_lease (status, reconciliation_claimed_at, updated_at);
