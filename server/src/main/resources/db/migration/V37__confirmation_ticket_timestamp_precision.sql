ALTER TABLE yunshu_confirmation_tickets
    MODIFY COLUMN expires_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN created_at TIMESTAMP(6) NOT NULL,
    MODIFY COLUMN consumed_at TIMESTAMP(6) NULL;
