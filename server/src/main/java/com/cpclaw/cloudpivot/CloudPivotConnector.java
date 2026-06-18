package com.cpclaw.cloudpivot;

public interface CloudPivotConnector {

    boolean testConnection(String baseUrl, String username, String password);

    CloudPivotMetadataSnapshot fetchMetadata(String baseUrl, String username, String password);

    CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize);
}
