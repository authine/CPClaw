package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Focused regression matrix for the universal Yunshu operation shapes.
 * It deliberately uses neutral object metadata; business names belong to
 * synchronized metadata or optional templates, not this framework test.
 */
class YunshuScenarioValidationTests {
    private final YunshuIntentPlanner planner = new DefaultYunshuIntentPlanner();
    private final DefaultYunshuQuestionSemantics semantics = new DefaultYunshuQuestionSemantics();

    @Test
    void classifiesReadWorkflowAndWriteScenarios() {
        assertEquals("query_data", planner.classify("查询业务对象数据"));
        assertEquals("analyze_data", planner.classify("分析整体趋势和分布"));
        assertEquals("workflow_query", planner.classify("查询我的待办"));
        assertEquals("workflow_query", planner.classify("查看已办流程"));
        assertEquals("workflow_query", planner.classify("查看我发起的流程"));
        assertEquals("workflow_action", planner.classify("发起流程"));
        assertEquals("workflow_action", planner.classify("审核第一条待办"));
        assertEquals("create_data", planner.classify("填单创建一条记录"));
        assertEquals("update_data", planner.classify("修改这条记录"));
        assertEquals("delete_data", planner.classify("删除这条记录"));
    }

    @Test
    void legacyQuestionSemanticsDoesNotDowngradeWorkflowActionsToReads() {
        assertEquals("workflow_action", semantics.detectIntent("发起流程"));
        assertEquals("workflow_action", semantics.detectIntent("审核第一条待办"));
        assertEquals("query_workflow", semantics.detectIntent("查看我发起的流程"));
    }

    @Test
    void readPlanIsExecutableButWritesRequireTrustedConfirmation() {
        MetadataSearchResult candidate = new MetadataSearchResult(
            "entity", "entity-1", "业务对象", "schema_1", "应用/业务对象", "low", "metadata");
        MetadataExecutionPlanner.MetadataExecutionPlan plan = MetadataExecutionPlanner.MetadataExecutionPlan.of(
            candidate, candidate.name(), candidate.code(), List.of(), List.of(), List.of(), List.of("metadata"));
        YunshuExecutionScope scope = YunshuExecutionScope.readOnly(
            new BoundCloudPivotConnection("https://cloudpivot.example", "huangj", "", ""), "test", "huangj");
        DefaultYunshuPlanValidator validator = new DefaultYunshuPlanValidator();

        assertEquals("valid", validator.validate(plan, "query_data", scope).state());
        assertEquals("valid", validator.validate(plan, "analyze_data", scope).state());
        YunshuPlanValidation create = validator.validate(plan, "create_data", scope);
        YunshuPlanValidation update = validator.validate(plan, "update_data", scope);
        YunshuPlanValidation delete = validator.validate(plan, "delete_data", scope);
        assertEquals("confirmation_required", create.state());
        assertEquals("confirmation_required", update.state());
        assertEquals("confirmation_required", delete.state());
        assertTrue(create.requiresConfirmation() && update.requiresConfirmation() && delete.requiresConfirmation());
    }
}
