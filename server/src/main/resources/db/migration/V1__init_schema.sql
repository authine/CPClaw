CREATE TABLE IF NOT EXISTS system_settings (
    id VARCHAR(36) PRIMARY KEY,
    cloudpivot_base_url VARCHAR(512),
    cloudpivot_username VARCHAR(255),
    admin_cloudpivot_base_url VARCHAR(512),
    admin_cloudpivot_username VARCHAR(255),
    search_engine_type VARCHAR(64),
    search_engine_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS encrypted_credentials (
    id VARCHAR(36) PRIMARY KEY,
    credential_type VARCHAR(64) NOT NULL,
    credential_owner_type VARCHAR(64) NOT NULL,
    credential_owner_id VARCHAR(64) NOT NULL,
    encrypted_value TEXT NOT NULL,
    iv VARCHAR(128) NOT NULL,
    auth_tag VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_credentials_owner (credential_owner_type, credential_owner_id, credential_type)
);

CREATE TABLE IF NOT EXISTS model_configs (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    api_base_url VARCHAR(512) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    api_key_credential_id VARCHAR(36),
    supports_thinking BOOLEAN NOT NULL DEFAULT FALSE,
    default_thinking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    default_temperature DECIMAL(4,2),
    default_max_tokens INT,
    extra_body_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversations (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    default_model_config_id VARCHAR(36),
    default_thinking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conversations_updated_at (updated_at)
);

CREATE TABLE IF NOT EXISTS messages (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    model_config_id VARCHAR(36),
    thinking_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_messages_conversation (conversation_id, created_at)
);

CREATE TABLE IF NOT EXISTS conversation_contexts (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    message_id VARCHAR(36),
    reference_type VARCHAR(64),
    reference_key VARCHAR(128),
    app_id VARCHAR(36),
    entity_id VARCHAR(36),
    record_id VARCHAR(128),
    display_name VARCHAR(255),
    row_index INT,
    payload_json TEXT,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_contexts_conversation (conversation_id)
);

CREATE TABLE IF NOT EXISTS attachments (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    message_id VARCHAR(36),
    original_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attachments_conversation (conversation_id)
);

CREATE TABLE IF NOT EXISTS cloudpivot_apps (
    id VARCHAR(36) PRIMARY KEY,
    app_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    raw_json TEXT,
    sync_batch_id VARCHAR(36),
    synced_at TIMESTAMP NULL,
    UNIQUE KEY uk_cloudpivot_apps_code (app_code)
);

CREATE TABLE IF NOT EXISTS cloudpivot_entities (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    entity_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    entity_type VARCHAR(64),
    raw_json TEXT,
    synced_at TIMESTAMP NULL,
    INDEX idx_entities_app (app_id, entity_code)
);

CREATE TABLE IF NOT EXISTS cloudpivot_entity_fields (
    id VARCHAR(36) PRIMARY KEY,
    entity_id VARCHAR(36) NOT NULL,
    field_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    data_type VARCHAR(64),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    is_reference BOOLEAN NOT NULL DEFAULT FALSE,
    reference_entity_id VARCHAR(36),
    description TEXT,
    raw_json TEXT,
    synced_at TIMESTAMP NULL,
    INDEX idx_fields_entity (entity_id, field_code)
);

CREATE TABLE IF NOT EXISTS cloudpivot_entity_relations (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    source_entity_id VARCHAR(36) NOT NULL,
    source_field_id VARCHAR(36),
    target_entity_id VARCHAR(36) NOT NULL,
    relation_type VARCHAR(64),
    relation_name VARCHAR(255),
    raw_json TEXT,
    synced_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS cloudpivot_view_actions (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    view_code VARCHAR(128),
    action_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    raw_json TEXT,
    synced_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS cloudpivot_form_actions (
    id VARCHAR(36) PRIMARY KEY,
    app_id VARCHAR(36) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    form_id VARCHAR(36),
    action_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    raw_json TEXT,
    synced_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS metadata_search_documents (
    id VARCHAR(36) PRIMARY KEY,
    object_type VARCHAR(64) NOT NULL,
    object_id VARCHAR(36) NOT NULL,
    app_id VARCHAR(36),
    entity_id VARCHAR(36),
    form_id VARCHAR(36),
    name VARCHAR(255) NOT NULL,
    code VARCHAR(128),
    aliases_json TEXT,
    search_text TEXT NOT NULL,
    embedding_text TEXT,
    graph_path VARCHAR(1024),
    risk_level VARCHAR(32),
    sync_batch_id VARCHAR(36),
    indexed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_search_app_type (app_id, object_type)
);

CREATE TABLE IF NOT EXISTS agent_runs (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    user_message_id VARCHAR(36),
    intent_summary VARCHAR(512),
    status VARCHAR(64) NOT NULL,
    plan_json TEXT,
    reflection_json TEXT,
    risk_level VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_agent_runs_conversation (conversation_id)
);

CREATE TABLE IF NOT EXISTS tool_calls (
    id VARCHAR(36) PRIMARY KEY,
    agent_run_id VARCHAR(36) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    input_json_masked TEXT,
    output_json_masked TEXT,
    status VARCHAR(64) NOT NULL,
    error_message_masked TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_tool_calls_agent_run (agent_run_id)
);

CREATE TABLE IF NOT EXISTS confirmations (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36),
    agent_run_id VARCHAR(36),
    plan_id VARCHAR(36),
    risk_level VARCHAR(32) NOT NULL,
    summary TEXT NOT NULL,
    affected_objects_json TEXT,
    changes_json_masked TEXT,
    attachments_json TEXT,
    status VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL,
    INDEX idx_confirmations_conversation (conversation_id)
);
