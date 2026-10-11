ALTER TABLE user_feedback
    ADD COLUMN admin_note VARCHAR(2000),
    ADD COLUMN handled_by VARCHAR(128);
