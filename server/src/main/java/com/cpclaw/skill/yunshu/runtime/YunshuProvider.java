package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import java.util.List;

/**
 * Provider boundary for the universal Yunshu Skill. It owns CloudPivot
 * transport details; semantic planning and business templates stay above it.
 */
public interface YunshuProvider {
    CloudPivotMetadataSnapshot metadata(BoundCloudPivotConnection connection);

    CloudPivotRuntimeQueryResult query(BoundCloudPivotConnection connection, String schemaCode,
            int pageSize, boolean enrichAllDetails, int maxRecords);

    /**
     * Write-related methods intentionally default to CONTRACT_UNVERIFIED. They
     * must not be wired to a guessed CloudPivot HTTP endpoint.
     */
    default ProviderFormSchema loadFormSchema(BoundCloudPivotConnection connection, FormReference formRef) {
        return new ProviderFormSchema(formRef, null, List.of());
    }

    default ProviderDraftValidation validateDraft(BoundCloudPivotConnection connection, FormReference formRef,
            List<FormFieldSlot> draft, String metadataVersion) {
        return ProviderDraftValidation.unverified("CloudPivot form validation contract has not been verified");
    }

    default ProviderOperationResult createRecord(BoundCloudPivotConnection connection, FormReference formRef,
            List<FormFieldSlot> draft, String idempotencyKey) {
        return ProviderOperationResult.unverified("createRecord", "CloudPivot create contract has not been verified");
    }

    default ProviderOperationResult updateRecord(BoundCloudPivotConnection connection, String recordRef,
            List<FormFieldSlot> patch, String idempotencyKey) {
        return ProviderOperationResult.unverified("updateRecord", "CloudPivot update contract has not been verified");
    }

    default ProviderOperationResult submitAction(BoundCloudPivotConnection connection, String actionRef,
            java.util.Map<String, Object> payload, String idempotencyKey) {
        return ProviderOperationResult.unverified("submitAction", "CloudPivot submit contract has not been verified");
    }

    default ProviderOperationResult getOperationResult(BoundCloudPivotConnection connection, String idempotencyKey) {
        return ProviderOperationResult.unverified("getOperationResult", "CloudPivot operation lookup contract has not been verified");
    }

    static YunshuProvider from(CloudPivotConnector connector) {
        return new ConnectorYunshuProvider(connector);
    }

    final class ConnectorYunshuProvider implements YunshuProvider {
        private final CloudPivotConnector connector;

        public ConnectorYunshuProvider(CloudPivotConnector connector) {
            this.connector = connector;
        }

        @Override
        public CloudPivotMetadataSnapshot metadata(BoundCloudPivotConnection connection) {
            return connector.fetchMetadata(connection.baseUrl(), connection.username(), connection.password());
        }

        @Override
        public CloudPivotRuntimeQueryResult query(BoundCloudPivotConnection connection, String schemaCode,
                int pageSize, boolean enrichAllDetails, int maxRecords) {
            return connector.queryRecords(connection.baseUrl(), connection.username(), connection.password(),
                    schemaCode, pageSize, enrichAllDetails, maxRecords);
        }

        @Override
        public ProviderOperationResult createRecord(BoundCloudPivotConnection connection, FormReference formRef,
                List<FormFieldSlot> draft, String idempotencyKey) {
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            if (draft != null) for (FormFieldSlot slot : draft) if (slot.value() != null) data.put(slot.fieldCode(), slot.value());
            CloudPivotOperationResult result = connector.createRecord(connection.baseUrl(), connection.username(), connection.password(),
                    formRef.applicationCode(), formRef.schemaCode(), data, idempotencyKey);
            boolean identified = result.bizObjectId() != null && !result.bizObjectId().isBlank();
            return new ProviderOperationResult(identified, identified, result.success() && !identified, idempotencyKey,
                    result.bizObjectId(), result.message(), result.responseSummary());
        }
    }
}
