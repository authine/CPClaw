package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/** Spring adapter binding the universal Provider contract to CloudPivot. */
@Component
public final class DefaultYunshuProvider implements YunshuProvider {
    private final CloudPivotConnector connector;

    public DefaultYunshuProvider(CloudPivotConnector connector) {
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
            java.util.List<FormFieldSlot> draft, String idempotencyKey) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (draft != null) for (FormFieldSlot slot : draft) if (slot.value() != null) data.put(slot.fieldCode(), slot.value());
        CloudPivotOperationResult result = connector.createRecord(connection.baseUrl(), connection.username(), connection.password(), formRef.applicationCode(), formRef.schemaCode(), data, idempotencyKey);
        boolean identified = result.bizObjectId() != null && !result.bizObjectId().isBlank();
        return new ProviderOperationResult(identified, identified, result.success() && !identified,
                idempotencyKey, result.bizObjectId(), result.message(), result.responseSummary());
    }
}





