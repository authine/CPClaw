package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.agent.MetadataExecutionPlanner;
import com.cpclaw.agent.MetadataExecutionPlanner.MetadataExecutionPlan;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.search.MetadataSearchService;
import org.springframework.stereotype.Component;

/** Default discovery implementation backed by the synchronized Metadata Index. */
@Component
public final class DefaultYunshuMetadataDiscovery implements YunshuMetadataDiscovery {
    private final MetadataSearchService metadataSearchService;
    private final MetadataExecutionPlanner metadataExecutionPlanner;

    public DefaultYunshuMetadataDiscovery(MetadataSearchService metadataSearchService,
            MetadataExecutionPlanner metadataExecutionPlanner) {
        this.metadataSearchService = metadataSearchService;
        this.metadataExecutionPlanner = metadataExecutionPlanner;
    }

    @Override
    public YunshuDiscovery discover(String goal) {
        MetadataSearchResult candidate = metadataSearchService.bestMatch(goal);
        MetadataExecutionPlan plan = metadataExecutionPlanner.plan(goal, candidate);
        MetadataSearchResult executable = plan.executableMatch() == null ? candidate : plan.executableMatch();
        boolean usable = plan.executable() && executable != null && "entity".equals(executable.objectType())
            && executable.code() != null && !executable.code().isBlank();
        if (!usable) return YunshuDiscovery.unavailable(executable, plan);
        return new YunshuDiscovery(executable, plan, true,
            "已定位“" + executable.name() + "”，并读取其字段、关联和 API 能力用于确定安全执行范围。");
    }
}
