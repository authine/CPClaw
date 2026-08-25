-- The default MCP service is product-neutral rather than bound to one client.
UPDATE mcp_installations
SET installation_key = 'CloudPivotMCP', display_name = '云枢MCP'
WHERE installation_key = 'local-default'
  AND NOT EXISTS (SELECT 1 FROM (SELECT installation_key FROM mcp_installations) AS existing WHERE existing.installation_key = 'CloudPivotMCP');

UPDATE mcp_installations
SET display_name = '云枢MCP'
WHERE installation_key = 'CloudPivotMCP';
