CREATE TABLE IF NOT EXISTS mcp_installations (
    id VARCHAR(36) PRIMARY KEY,
    installation_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMP NULL,
    UNIQUE KEY uk_mcp_installations_key (installation_key)
);

CREATE TABLE IF NOT EXISTS mcp_installation_bindings (
    id VARCHAR(36) PRIMARY KEY,
    installation_id VARCHAR(36) NOT NULL,
    cloudpivot_base_url VARCHAR(512),
    cloudpivot_username VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mcp_binding_installation (installation_id)
);

CREATE TABLE IF NOT EXISTS mcp_tool_call_audits (
    id VARCHAR(36) PRIMARY KEY,
    installation_id VARCHAR(36),
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    summary_masked VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mcp_tool_audit_installation (installation_id, created_at)
);
