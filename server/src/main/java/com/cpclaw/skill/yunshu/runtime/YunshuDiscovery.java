package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.agent.MetadataExecutionPlanner.MetadataExecutionPlan;
import com.cpclaw.metadata.dto.MetadataSearchResult;

/** Immutable discovery output shared by every host adapter. */
public record YunshuDiscovery(MetadataSearchResult match, MetadataExecutionPlan plan,
        boolean executable, String summary) {
    public static YunshuDiscovery unavailable(MetadataSearchResult match, MetadataExecutionPlan plan) {
        return new YunshuDiscovery(match, plan, false,
            "尚未唯一定位到可执行业务对象，将先请求补充信息。");
    }
}
