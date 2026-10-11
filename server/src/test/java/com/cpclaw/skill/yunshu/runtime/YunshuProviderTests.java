package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import java.util.List;
import java.util.Map;
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

    @Test
    void createsRuntimeFormPayloadWithBizObjectDataAndIdempotencyKey() {
        CloudPivotConnector connector = mock(CloudPivotConnector.class);
        BoundCloudPivotConnection connection = new BoundCloudPivotConnection("installation", "https://yunshu", "user", "password");
        when(connector.saveForm(any(), any(), any(), any(), any())).thenReturn(
                new com.cpclaw.cloudpivot.CloudPivotOperationResult(true, "save", "zlcsstcrm", "sql_line", "record-1", "/api/runtime/form/save", "ok", Map.of()));
        when(connector.submitForm(any(), any(), any(), any(), any())).thenReturn(
                new com.cpclaw.cloudpivot.CloudPivotOperationResult(true, "submit", "zlcsstcrm", "sql_line", "record-1", "/api/runtime/form/submit", "ok", Map.of()));
        YunshuProvider provider = new DefaultYunshuProvider(connector);
        ProviderOperationResult result = provider.createRecord(connection, new FormReference("zlcsstcrm", "sql_line", "sql_line"),
                List.of(new FormFieldSlot("sql_line_name", "名称", "text", "测试客户", "user", 1d, true, true, "valid", null, List.of())), "op-1");
        assertSame(true, result.completed());
        assertSame("record-1", result.recordRef());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(connector).saveForm(any(), any(), any(), payload.capture(), org.mockito.ArgumentMatchers.eq("op-1"));
        Map<?, ?> bizObject = (Map<?, ?>) payload.getValue().get("bizObject");
        assertSame("sql_line", bizObject.get("schemaCode"));
        assertEquals("测试客户", ((Map<?, ?>) bizObject.get("data")).get("sql_line_name"));
    }

    @Test
    void reconciliationWithoutOriginalRecordRefCannotPromoteFieldMatchToSuccess() {
        CloudPivotConnector connector = mock(CloudPivotConnector.class);
        YunshuProvider provider = new DefaultYunshuProvider(connector);
        BoundCloudPivotConnection connection = new BoundCloudPivotConnection("installation", "https://yunshu", "user", "password");

        ProviderOperationResult result = provider.reconcileRecord(connection,
                new FormReference("test", "test_multi_branch", "test_multi_branch"),
                List.of(new FormFieldSlot("ShortText1784110275677", "所走分支", "text", "历史同值", "user", 1d, false, true, "valid", null, List.of())),
                "");

        assertFalse(result.completed());
        assertEquals(true, result.unknown());
        verify(connector, org.mockito.Mockito.never()).queryRecords(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
