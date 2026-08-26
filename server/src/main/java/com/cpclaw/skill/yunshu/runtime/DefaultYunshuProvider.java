package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import org.springframework.stereotype.Component;

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
}
