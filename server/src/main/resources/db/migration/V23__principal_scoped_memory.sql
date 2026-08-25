ALTER TABLE agent_memories ADD COLUMN memory_scope VARCHAR(16) NOT NULL DEFAULT 'SESSION';
ALTER TABLE agent_memories ADD COLUMN owner_principal VARCHAR(128) NULL;
ALTER TABLE agent_memories ADD COLUMN tenant_id VARCHAR(128) NOT NULL DEFAULT 'default';
ALTER TABLE agent_memories ADD COLUMN priority INT NOT NULL DEFAULT 0;
CREATE INDEX idx_agent_memories_scope_owner ON agent_memories(memory_scope, owner_principal, tenant_id, expires_at);
ALTER TABLE agent_memories MODIFY COLUMN conversation_id VARCHAR(36) NULL;
UPDATE agent_memories SET owner_principal = 'huangj', tenant_id = 'default'
WHERE memory_scope = 'SESSION' AND owner_principal IS NULL;
