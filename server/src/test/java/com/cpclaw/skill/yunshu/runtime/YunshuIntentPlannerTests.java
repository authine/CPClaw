package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class YunshuIntentPlannerTests {
    private final YunshuIntentPlanner planner = new DefaultYunshuIntentPlanner();

    @Test
    void classifiesInteractionShapeWithoutObjectVocabulary() {
        assertEquals("query_data", planner.classify("查询数据列表"));
        assertEquals("analyze_data", planner.classify("分析整体趋势"));
        assertEquals("workflow_query", planner.classify("查看待办"));
        assertEquals("update_data", planner.classify("修改这条记录"));
        assertEquals("clarify_intent", planner.classify("你好"));
    }

    @Test
    void normalizesOnlySupportedOperationKinds() {
        assertEquals("analyze_data", planner.normalizeModelIntent("analyze_data", "query_data"));
        assertEquals("query_data", planner.normalizeModelIntent("unknown-domain-intent", "query_data"));
        assertEquals("workflow_list_finished", planner.workflowApiCode("查看已处理流程"));
        assertEquals("workflow_list_started", planner.workflowApiCode("查看我发起的流程"));
        assertEquals("workflow_list_pending", planner.workflowApiCode("查看流程"));
    }
}
