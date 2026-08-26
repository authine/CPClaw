package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import org.junit.jupiter.api.Test;

class YunshuProviderTests {
    @Test
    void delegatesMetadataAndQueryThroughAuthenticatedConnection() {
        CloudPivotConnector connector = mock(CloudPivotConnector.class);
        BoundCloudPivotConnection connection = new BoundCloudPivotConnection("installation", "https://yunshu", "user", "password");
        CloudPivotMetadataSnapshot metadata = mock(CloudPivotMetadataSnapshot.class);
        CloudPivotRuntimeQueryResult result = mock(CloudPivotRuntimeQueryResult.class);
        when(connector.fetchMetadata("https://yunshu", "user", "password")).thenReturn(metadata);
        when(connector.queryRecords("https://yunshu", "user", "password", "entity-code", 20, true, 20)).thenReturn(result);

        YunshuProvider provider = new DefaultYunshuProvider(connector);

        assertSame(metadata, provider.metadata(connection));
        assertSame(result, provider.query(connection, "entity-code", 20, true, 20));
        verify(connector).fetchMetadata("https://yunshu", "user", "password");
        verify(connector).queryRecords("https://yunshu", "user", "password", "entity-code", 20, true, 20);
    }
}
