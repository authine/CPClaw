package com.cpclaw.cloudpivot;

import java.util.List;
import java.util.Map;

public interface CloudPivotConnector {

    boolean testConnection(String baseUrl, String username, String password);

    CloudPivotMetadataSnapshot fetchMetadata(String baseUrl, String username, String password);

    CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize);

    default CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize, boolean enrichAllDetails) {
        return queryRecords(baseUrl, username, password, schemaCode, pageSize);
    }

    default CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize, boolean enrichAllDetails, int maxRecords) {
        return queryRecords(baseUrl, username, password, schemaCode, pageSize, enrichAllDetails);
    }

    default CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize, boolean enrichAllDetails, int maxRecords, List<RuntimeQueryFilter> filters) {
        return queryRecords(baseUrl, username, password, schemaCode, pageSize, enrichAllDetails, maxRecords);
    }

    default CloudPivotOperationResult createRecord(String baseUrl, String username, String password, String appCode,
            String schemaCode, Map<String, Object> data, String idempotencyKey) {
        throw new UnsupportedOperationException("CloudPivot create operation is not implemented");
    }

    /** Runtime form save. Payload shape is provider-owned and must be verified against the target tenant. */
    default CloudPivotOperationResult saveForm(String baseUrl, String username, String password,
            Map<String, Object> payload, String idempotencyKey) {
        throw new UnsupportedOperationException("CloudPivot runtime form save contract is not implemented");
    }

    /** Runtime form submit. Save and submit are separate CloudPivot operations. */
    default CloudPivotOperationResult submitForm(String baseUrl, String username, String password,
            Map<String, Object> payload, String idempotencyKey) {
        throw new UnsupportedOperationException("CloudPivot runtime form submit contract is not implemented");
    }

    default CloudPivotOperationResult updateRecord(String baseUrl, String username, String password, String appCode,
            String schemaCode, String bizObjectId, Map<String, Object> data, String idempotencyKey) {
        throw new UnsupportedOperationException("CloudPivot update operation is not implemented");
    }
    default CloudPivotOperationResult deleteRecord(String baseUrl, String username, String password, String appCode, String schemaCode, String bizObjectId) {
        throw new UnsupportedOperationException("CloudPivot delete operation is not implemented");
    }

    default WorkflowContractProbeResult probeWorkflowReadContracts(String baseUrl, String username, String password) {
        throw new UnsupportedOperationException("CloudPivot workflow contract probing is not implemented");
    }

    default WorkflowReadResult queryWorkflowRead(String baseUrl, String username, String password, String apiCode, String method, String path, int pageSize) {
        throw new UnsupportedOperationException("CloudPivot workflow read is not implemented");
    }

    default WorkflowReadResult queryWorkflowRead(String baseUrl, String username, String password, String apiCode, String method, String path, int pageSize, List<String> requestKeys) {
        return queryWorkflowRead(baseUrl, username, password, apiCode, method, path, pageSize);
    }
}



