package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cpclaw.skill.yunshu.runtime.MetadataExecutionPlanner.MetadataExecutionPlan;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class YunshuPlanValidatorTests {
    private final YunshuPlanValidator validator = new DefaultYunshuPlanValidator();
    private final BoundCloudPivotConnection connection = new BoundCloudPivotConnection("installation", "https://yunshu", "user", "password");
    private final MetadataSearchResult candidate = new MetadataSearchResult("entity", "id", "对象", "entity_code", "应用/对象", "low", "verified");

    @Test
    void readPlanIsValidWhenMetadataAndScopeArePresent() {
        MetadataExecutionPlan plan = MetadataExecutionPlan.of(candidate, "对象", "entity_code", List.of(), List.of(), List.of(), List.of());
        YunshuPlanValidation result = validator.validate(plan, "query_data", YunshuExecutionScope.readOnly(connection, "mcp", "principal"));
        assertEquals("valid", result.state());
    }

    @Test
    void writePlanRequiresTrustedConfirmationInReadOnlyScope() {
        MetadataExecutionPlan plan = MetadataExecutionPlan.of(candidate, "对象", "entity_code", List.of(), List.of(), List.of(), List.of());
        YunshuPlanValidation result = validator.validate(plan, "update_data", YunshuExecutionScope.readOnly(connection, "mcp", "principal"));
        assertEquals("confirmation_required", result.state());
        assertEquals(true, result.requiresConfirmation());
    }

    @Test
    void missingPlanIsBlocked() {
        YunshuPlanValidation result = validator.validate(MetadataExecutionPlan.empty(candidate, "missing"), "query_data", YunshuExecutionScope.readOnly(connection, "mcp", "principal"));
        assertEquals("blocked", result.state());
    }
}
