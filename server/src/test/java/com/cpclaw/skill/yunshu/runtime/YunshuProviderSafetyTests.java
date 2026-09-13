package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import java.util.List;
import org.junit.jupiter.api.Test;

class YunshuProviderSafetyTests {
    private final YunshuProvider provider = new YunshuProvider() {
        @Override public CloudPivotMetadataSnapshot metadata(BoundCloudPivotConnection connection) { return null; }
        @Override public CloudPivotRuntimeQueryResult query(BoundCloudPivotConnection connection, String schemaCode, int pageSize, boolean details, int max) { return null; }
    };

    @Test
    void unverifiedWriteContractsCannotReportSuccess() {
        ProviderOperationResult create = provider.createRecord(null, new FormReference("app", "schema", "form"), List.of(), "key");
        assertFalse(create.contractVerified());
        assertFalse(create.completed());
        assertTrue(create.message().contains("not been verified"));
    }
}
