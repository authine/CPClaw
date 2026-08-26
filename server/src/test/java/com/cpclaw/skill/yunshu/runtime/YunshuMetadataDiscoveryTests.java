package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cpclaw.skill.yunshu.runtime.MetadataExecutionPlanner;
import com.cpclaw.skill.yunshu.runtime.MetadataExecutionPlanner.MetadataExecutionPlan;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.search.MetadataSearchService;
import java.util.List;
import org.junit.jupiter.api.Test;

class YunshuMetadataDiscoveryTests {
    @Test
    void returnsExecutableDiscoveryOnlyWhenPlanHasVerifiedEntity() {
        MetadataSearchService search = mock(MetadataSearchService.class);
        MetadataExecutionPlanner planner = mock(MetadataExecutionPlanner.class);
        MetadataSearchResult candidate = new MetadataSearchResult("entity", "id", "对象", "entity_code", "应用/对象", "low", "verified");
        MetadataExecutionPlan executable = MetadataExecutionPlan.of(candidate, "对象", "entity_code", List.of(), List.of(), List.of(), List.of());
        when(search.bestMatch("查询对象")).thenReturn(candidate);
        when(planner.plan("查询对象", candidate)).thenReturn(executable);
        YunshuDiscovery result = new DefaultYunshuMetadataDiscovery(search, planner).discover("查询对象");
        assertTrue(result.executable());
        assertTrue(result.match().code().equals("entity_code"));
    }

    @Test
    void marksUnknownCandidateAsUnavailable() {
        MetadataSearchService search = mock(MetadataSearchService.class);
        MetadataExecutionPlanner planner = mock(MetadataExecutionPlanner.class);
        MetadataSearchResult candidate = new MetadataSearchResult("unknown", "", "未匹配", "", "", "low", "none");
        MetadataExecutionPlan plan = MetadataExecutionPlan.empty(candidate, "missing");
        when(search.bestMatch("不明确")).thenReturn(candidate);
        when(planner.plan("不明确", candidate)).thenReturn(plan);
        YunshuDiscovery result = new DefaultYunshuMetadataDiscovery(search, planner).discover("不明确");
        assertFalse(result.executable());
        assertTrue(result.summary().contains("补充"));
    }
}
