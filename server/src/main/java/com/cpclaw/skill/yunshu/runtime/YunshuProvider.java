package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;

/**
 * Provider boundary for the universal Yunshu Skill. It owns CloudPivot
 * transport details; semantic planning and business templates stay above it.
 */
public interface YunshuProvider {
    CloudPivotMetadataSnapshot metadata(BoundCloudPivotConnection connection);

    CloudPivotRuntimeQueryResult query(BoundCloudPivotConnection connection, String schemaCode,
            int pageSize, boolean enrichAllDetails, int maxRecords);

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
    }
}
