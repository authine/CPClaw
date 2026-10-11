package com.cpclaw.skill.yunshu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cpclaw.skill.yunshu.runtime.DefaultYunshuIntentPlanner;
import org.junit.jupiter.api.Test;

class YunshuMcpTaskExecutorIntentTests {
    private final DefaultYunshuIntentPlanner planner = new DefaultYunshuIntentPlanner();

    @Test
    void explicitAnalysisCannotBeDowngradedToWriteByModelPlanning() {
        assertEquals("analyze_data", YunshuMcpTaskExecutor.mergeModelIntent(
                "analyze_data", "create_data", planner));
        assertEquals("analyze_data", YunshuMcpTaskExecutor.mergeModelIntent(
                "analyze_data", "update_data", planner));
    }

    @Test
    void explicitQueryCannotBeDowngradedToWriteByModelPlanning() {
        assertEquals("query_data", YunshuMcpTaskExecutor.mergeModelIntent(
                "query_data", "create_data", planner));
    }

    @Test
    void modelMayRefineReadShapeWithoutChangingSafetyClass() {
        assertEquals("analyze_data", YunshuMcpTaskExecutor.mergeModelIntent(
                "query_data", "analyze_data", planner));
        assertEquals("analyze_data", YunshuMcpTaskExecutor.mergeModelIntent(
                "analyze_data", "query_data", planner));
    }
}
