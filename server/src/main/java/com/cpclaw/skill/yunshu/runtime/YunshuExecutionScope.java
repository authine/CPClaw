package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;

/** Authenticated, request-scoped capability passed to Yunshu providers. */
public record YunshuExecutionScope(BoundCloudPivotConnection connection, String channel,
        String principal, boolean allowWrites) {
    public YunshuExecutionScope {
        if (connection == null) throw new IllegalArgumentException("云枢执行范围不能为空");
        channel = channel == null ? "" : channel.trim();
        principal = principal == null ? "" : principal.trim();
    }

    public static YunshuExecutionScope readOnly(BoundCloudPivotConnection connection, String channel, String principal) {
        return new YunshuExecutionScope(connection, channel, principal, false);
    }
}
