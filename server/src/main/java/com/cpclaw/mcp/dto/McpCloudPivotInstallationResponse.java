package com.cpclaw.mcp.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record McpCloudPivotInstallationResponse(
    String installationId,
    String displayName,
    String status,
    boolean environmentConfigured,
    String environmentSource,
    Instant enabledAt,
    Map<String, Object> mcpClientConfig,
    List<Map<String, String>> capabilities
) {}
