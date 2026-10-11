package com.cpclaw.metadata;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.common.exception.GlobalExceptionHandler;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import com.cpclaw.metadata.graph.MetadataGraphService;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.workflow.WorkflowCenterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class MetadataControllerSecurityTests {
    @Test
    void nonOperatorCannotExportOrRebuildGraphOrSyncMetadata() {
        MetadataService metadataService = mock(MetadataService.class);
        MetadataSearchService search = mock(MetadataSearchService.class);
        MetadataGraphService graph = mock(MetadataGraphService.class);
        WorkflowCenterService workflow = mock(WorkflowCenterService.class);
        MetadataSyncLogService syncLogs = mock(MetadataSyncLogService.class);
        PrincipalContextService principals = mock(PrincipalContextService.class);
        when(principals.current()).thenReturn(new PrincipalContext("user-a", "user-a", "User A", "", "tenant-a", true, false));
        MetadataController controller = new MetadataController(metadataService, search, graph, workflow, syncLogs, principals);

        assertThrows(ResponseStatusException.class, controller::graphExport);
        assertThrows(ResponseStatusException.class, controller::rebuildGraph);
        assertThrows(ResponseStatusException.class, controller::sync);
        verifyNoInteractions(graph, metadataService, syncLogs);
    }

    @Test
    void httpRequestsCannotElevateWithIdentityHeaders() throws Exception {
        MetadataService metadataService = mock(MetadataService.class);
        MetadataSearchService search = mock(MetadataSearchService.class);
        MetadataGraphService graph = mock(MetadataGraphService.class);
        WorkflowCenterService workflow = mock(WorkflowCenterService.class);
        MetadataSyncLogService syncLogs = mock(MetadataSyncLogService.class);
        PrincipalContextService principals = mock(PrincipalContextService.class);
        when(principals.current()).thenReturn(new PrincipalContext("user-a", "user-a", "User A", "", "tenant-a", true, false));
        MetadataController controller = new MetadataController(metadataService, search, graph, workflow, syncLogs, principals);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
                .build();

        mockMvc.perform(get("/api/metadata/graph/export"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("只有运营管理员可以导出或重建元数据图谱"));
        mockMvc.perform(get("/api/metadata/graph/export")
                        .header("X-CPClaw-Principal", "admin")
                        .header("X-Yunshu-External-Principal", "admin"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/metadata/graph/rebuild"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/metadata/sync"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(graph, metadataService, syncLogs);
    }
}
