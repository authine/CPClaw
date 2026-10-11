package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudPivotRuntimeFormContractTests {
    @Test
    void defaultConnectorKeepsRuntimeFormWritesFailClosedUntilPayloadIsVerified() {
        CloudPivotConnector connector = new CloudPivotConnector() {
            @Override public boolean testConnection(String baseUrl, String username, String password) { return true; }
            @Override public com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot fetchMetadata(String baseUrl, String username, String password) { return null; }
            @Override public com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize) { return null; }
        };

        boolean saveBlocked;
        try {
            connector.saveForm("https://yunshu", "user", "password", Map.of("schemaCode", "sql_line"), "op-1");
            saveBlocked = false;
        } catch (UnsupportedOperationException expected) {
            saveBlocked = true;
        }
        boolean submitBlocked;
        try {
            connector.submitForm("https://yunshu", "user", "password", Map.of("schemaCode", "sql_line"), "op-1");
            submitBlocked = false;
        } catch (UnsupportedOperationException expected) {
            submitBlocked = true;
        }
        assertTrue(saveBlocked);
        assertTrue(submitBlocked);
    }

    @Test
    void aSuccessfulTransportWithoutRecordIdentityCannotBeReportedAsCompleted() {
        CloudPivotOperationResult result = new CloudPivotOperationResult(true, "save", "zlcsstcrm", "sql_line", null,
                "/api/runtime/form/save", "ok", Map.of());
        ProviderOperationResult mapped = new ProviderOperationResult(
                result.bizObjectId() != null && !result.bizObjectId().isBlank(),
                result.bizObjectId() != null && !result.bizObjectId().isBlank(),
                result.success() && (result.bizObjectId() == null || result.bizObjectId().isBlank()),
                "op-1", result.bizObjectId(), result.message(), result.responseSummary());
        assertFalse(mapped.completed());
        assertTrue(mapped.unknown());
    }
}
