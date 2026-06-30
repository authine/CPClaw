ALTER TABLE messages MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE messages MODIFY COLUMN metadata_json LONGTEXT;

ALTER TABLE agent_runs MODIFY COLUMN plan_json LONGTEXT;
ALTER TABLE agent_runs MODIFY COLUMN reflection_json LONGTEXT;

ALTER TABLE tool_calls MODIFY COLUMN input_json_masked LONGTEXT;
ALTER TABLE tool_calls MODIFY COLUMN output_json_masked LONGTEXT;
ALTER TABLE tool_calls MODIFY COLUMN error_message_masked LONGTEXT;

ALTER TABLE confirmations MODIFY COLUMN summary LONGTEXT NOT NULL;
ALTER TABLE confirmations MODIFY COLUMN affected_objects_json LONGTEXT;
ALTER TABLE confirmations MODIFY COLUMN changes_json_masked LONGTEXT;
ALTER TABLE confirmations MODIFY COLUMN attachments_json LONGTEXT;
