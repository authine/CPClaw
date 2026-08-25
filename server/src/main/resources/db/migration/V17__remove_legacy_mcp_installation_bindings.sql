-- WorkBuddy credentials are injected only at invocation time. This legacy table
-- has no active consumer and must not leave a false persistence path behind.
DROP TABLE IF EXISTS mcp_installation_bindings;
