package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import org.springframework.stereotype.Component;
import java.util.List;
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
    public ProviderOperationResult reconcileRecord(BoundCloudPivotConnection connection, FormReference formRef,
            List<FormFieldSlot> draft, String expectedRecordRef) {
        if (expectedRecordRef == null || expectedRecordRef.isBlank()) {
            return new ProviderOperationResult(true, false, true, "reconcile", null,
                    "缺少原始云枢记录标识，禁止仅凭字段值认定本次写入成功",
                    Map.of("remoteReconciled", false, "reconciliationRequiresRecordRef", true));
        }
        CloudPivotRuntimeQueryResult result = connector.queryRecords(connection.baseUrl(), connection.username(),
                connection.password(), formRef.schemaCode(), 200, true, 500, List.of());
        for (Map<String, Object> record : result.records()) {
            String recordRef = recordReference(record);
            if (recordRef != null && expectedRecordRef.equals(recordRef)) {
                return new ProviderOperationResult(true, true, false, "reconcile:" + recordRef, recordRef,
                        "云枢记录已通过查询回查确认", Map.of("remoteReconciled", true, "queryTotal", result.total()));
            }
        }
        return new ProviderOperationResult(true, false, true, "reconcile", expectedRecordRef,
                "云枢查询未找到匹配记录，结果仍未知", Map.of("remoteReconciled", false, "queryTotal", result.total()));
    }

    private String recordReference(Map<String, Object> record) {
        if (record == null) return null;
        for (String key : List.of("bizObjectId", "objectId", "id", "dataId")) {
            Object value = record.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        Object data = record.get("data");
        if (data instanceof Map<?, ?> nested) {
            for (String key : List.of("bizObjectId", "objectId", "id", "dataId")) {
                Object value = nested.get(key);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            }
        }
        return null;
    }

    @Override
    public ProviderOperationResult createRecord(BoundCloudPivotConnection connection, FormReference formRef,
            java.util.List<FormFieldSlot> draft, String idempotencyKey) {
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        if (draft != null) for (FormFieldSlot slot : draft) if (slot.value() != null) data.put(slot.fieldCode(), slot.value());
        java.util.Map<String, Object> bizObject = new java.util.LinkedHashMap<>();
        bizObject.put("id", ""); bizObject.put("data", data); bizObject.put("schemaCode", formRef.schemaCode()); bizObject.put("sheetCode", formRef.formCode());
        String version = connector.getNewFormVersion(connection.baseUrl(), connection.username(), connection.password(), formRef.schemaCode(), formRef.formCode());
        if (version != null && !version.isBlank()) data.put("version", version);
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>(); payload.put("bizObject", bizObject); payload.put("workflowCode", ""); payload.put("workItemId", ""); payload.put("workflowInstanceId", ""); payload.put("queryId", ""); payload.put("formPath", formRef.formCode());
        CloudPivotOperationResult saved = connector.saveForm(connection.baseUrl(), connection.username(), connection.password(), payload, idempotencyKey);
        String savedRef = saved.bizObjectId();
        if (!saved.success() || savedRef == null || savedRef.isBlank()) return new ProviderOperationResult(false, false, true, idempotencyKey, savedRef, saved.message(), saved.responseSummary());
        Map<String, Object> submitPayload = new LinkedHashMap<>(payload);
        Map<String, Object> submitObject = new LinkedHashMap<>((Map<String, Object>) payload.get("bizObject"));
        submitObject.put("id", savedRef); submitPayload.put("bizObject", submitObject); submitPayload.put("actionCode", "submit"); submitPayload.put("agree", true); submitPayload.put("formType", "2");
        CloudPivotOperationResult submitted = connector.submitForm(connection.baseUrl(), connection.username(), connection.password(), submitPayload, idempotencyKey + ":submit");
        boolean identified = submitted.success() && submitted.bizObjectId() != null && !submitted.bizObjectId().isBlank();
        return new ProviderOperationResult(identified, identified, !identified, idempotencyKey, identified ? submitted.bizObjectId() : savedRef, submitted.message(), submitted.responseSummary());
    }
}





