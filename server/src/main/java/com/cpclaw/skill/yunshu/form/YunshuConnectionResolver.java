package com.cpclaw.skill.yunshu.form;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import java.util.Optional;

public interface YunshuConnectionResolver {
    Optional<BoundCloudPivotConnection> resolve(YunshuHostContext context);
}
