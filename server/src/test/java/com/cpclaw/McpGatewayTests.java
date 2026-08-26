package com.cpclaw;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.credential.repository.EncryptedCredentialRepository;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-mcp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.persistence.runtime-guard-enabled=false",
    "cpclaw.templates.scenario.enabled=true"
})
@AutoConfigureMockMvc
class McpGatewayTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private SystemSettingsRepository settingsRepository;
    @Autowired private EncryptedCredentialRepository credentialRepository;
    @Autowired private CloudPivotEntityRepository entityRepository;
    @Autowired private MetadataSearchDocumentRepository metadataSearchDocumentRepository;
    @MockBean private CloudPivotConnector cloudPivotConnector;

    @Test
    void exposesStableInstallationAndNeverReturnsSecrets() throws Exception {
        mockMvc.perform(get("/api/settings/mcp/cloudpivot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.installationId").value("CloudPivotMCP"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.mcpClientConfig.type").value("sse"))
            .andExpect(jsonPath("$.data.mcpClientConfig.url").value(org.hamcrest.Matchers.endsWith("/api/mcp/cloudpivot/sse")))
            .andExpect(jsonPath("$.data.mcpClientConfig.headers.x-cpclaw-installation-id").value("CloudPivotMCP"))
            .andExpect(jsonPath("$.data.mcpClientConfig.headers.x-cpclaw-cloudpivot-username").isNotEmpty())
            .andExpect(jsonPath("$.data.mcpClientConfig.headers.x-cpclaw-cloudpivot-password").isNotEmpty())
            .andExpect(jsonPath("$.data.mcpClientConfig.args").doesNotExist())
            .andExpect(jsonPath("$.data.cloudPivotBaseUrl").doesNotExist())
            .andExpect(jsonPath("$.data.cloudPivotUsername").doesNotExist())
            .andExpect(jsonPath("$.data.cloudPivotPassword").doesNotExist())
            .andExpect(jsonPath("$.data.credentialStatus").doesNotExist());
    }

    @Test
    void exposesSseDiscoveryWithoutLocalAdapterPath() throws Exception {
        mockMvc.perform(get("/api/mcp/cloudpivot/sse")
                .header("X-CPClaw-Installation-Id", "CloudPivotMCP"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("event:endpoint")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("/api/mcp/cloudpivot/message?sessionId=")));
    }

    @Test
    void supportsMcpInitializeAndToolsListWithoutCloudPivotCall() throws Exception {
        mockMvc.perform(post("/api/mcp/cloudpivot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"initialize\",\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.serverInfo.name").value("cpclaw-yunshu"));

        mockMvc.perform(post("/api/mcp/cloudpivot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"tools/list\",\"params\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.tools.length()").value(2))
            .andExpect(jsonPath("$.result.tools[0].name").value("cpclaw_cloudpivot_agent"))
            .andExpect(jsonPath("$.result.tools[0].inputSchema.required[0]").value("request"))
            .andExpect(jsonPath("$.result.tools[0].inputSchema.properties.schemaCode").doesNotExist())
            .andExpect(jsonPath("$.result.tools[0].description").value(org.hamcrest.Matchers.containsString("唯一业务工具")))
            .andExpect(jsonPath("$.result.tools[0].description").value(org.hamcrest.Matchers.containsString("不能在本轮自行补查字段")))
            .andExpect(jsonPath("$.result.tools[0].outputSchema.required").value(org.hamcrest.Matchers.hasItems("task", "hostAction", "output")))
            .andExpect(jsonPath("$.result.tools[0].outputSchema.properties.hostAction.properties.type.enum").value(org.hamcrest.Matchers.hasItem("compose_answer")));

        verifyNoInteractions(cloudPivotConnector);
    }

    @Test
    void sseMessageEndpointAcceptsJsonRpcAndReturnsThroughEventChannel() throws Exception {
        String sse = mockMvc.perform(get("/api/mcp/cloudpivot/sse")
                .header("X-CPClaw-Installation-Id", "CloudPivotMCP"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(sse.contains("sessionId="));
        String sessionId = sse.replaceAll("(?s).*sessionId=([a-f0-9-]+).*", "$1");
        mockMvc.perform(post("/api/mcp/cloudpivot/message?sessionId=" + sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"sse-1\",\"method\":\"tools/list\",\"params\":{}}"))
            .andExpect(status().isAccepted());
    }

    @Test
    void enablesOnlyWhenCpClawEnvironmentExistsAndNeverPersistsClientCredentials() throws Exception {
        // This case verifies the missing-environment guard; other tests may
        // create the shared default settings row in the same Spring context.
        settingsRepository.deleteAll();
        mockMvc.perform(post("/api/settings/mcp/cloudpivot/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\":\"client-alice\",\"displayName\":\"终端客户端\"}"))
            .andExpect(status().is5xxServerError());

        SystemSettings settings = new SystemSettings();
        settings.setId("default");
        settings.setAdminCloudPivotBaseUrl("https://cloudpivot.example");
        settings.setCreatedAt(Instant.now());
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        CloudPivotEntity entity = new CloudPivotEntity();
        entity.setId("mcp-opportunity");
        entity.setAppId("mcp-test-app");
        entity.setEntityCode("Opportunity");
        entity.setName("商机");
        entity.setEntityType("biz");
        entity.setSyncedAt(Instant.now());
        entityRepository.save(entity);
        MetadataSearchDocument document = new MetadataSearchDocument();
        document.setId("mcp-opportunity-search");
        document.setObjectType("entity");
        document.setObjectId("mcp-opportunity");
        document.setAppId("mcp-test-app");
        document.setEntityId("mcp-opportunity");
        document.setName("商机");
        document.setCode("Opportunity");
        document.setSearchText("商机 Opportunity 销售机会");
        document.setEmbeddingText("商机");
        document.setGraphPath("CRM / 商机");
        document.setRiskLevel("low");
        document.setIndexedAt(Instant.now());
        document.setCreatedAt(Instant.now());
        metadataSearchDocumentRepository.save(document);

        mockMvc.perform(post("/api/settings/mcp/cloudpivot/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\":\"client-alice\",\"displayName\":\"终端客户端\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ENABLED"))
            .andExpect(jsonPath("$.data.environmentConfigured").value(true));

        mockMvc.perform(post("/api/mcp/cloudpivot")
                .header("X-CPClaw-Installation-Id", "client-alice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"tools/call\",\"params\":{\"name\":\"yunshu_query_data\",\"arguments\":{\"schemaCode\":\"Opportunity\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("CPC_CLOUDPIVOT_USERNAME")));
        verifyNoInteractions(cloudPivotConnector);

        when(cloudPivotConnector.queryRecords("https://cloudpivot.example", "alice", "secret", "Opportunity", 20, true, 20))
            .thenReturn(new CloudPivotRuntimeQueryResult("Opportunity", 1, List.of(java.util.Map.<String, Object>of("data", java.util.Map.of("name", "Alpha", "password", "secret"))), "/api/list"));
        long credentialsBefore = credentialRepository.count();
        mockMvc.perform(post("/api/mcp/cloudpivot")
                .header("X-CPClaw-Installation-Id", "client-alice")
                .header("X-CPClaw-CloudPivot-Username", "alice")
                .header("X-CPClaw-CloudPivot-Password", "secret")
                .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"method\":\"tools/call\",\"params\":{\"name\":\"yunshu_query_data\",\"arguments\":{\"schemaCode\":\"Opportunity\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error.code").value(-32602))
            .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("只公开 yunshu_handle_request 或 cpclaw_cloudpivot_agent")));
        verifyNoInteractions(cloudPivotConnector);

        mockMvc.perform(post("/api/mcp/cloudpivot")
                .header("X-CPClaw-Installation-Id", "client-alice")
                .header("X-CPClaw-CloudPivot-Username", "alice")
                .header("X-CPClaw-CloudPivot-Password", "secret")
                .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":\"5\",\"method\":\"tools/call\",\"params\":{\"name\":\"yunshu_handle_request\",\"arguments\":{\"request\":\"查询商机情况\",\"conversationId\":\"conv-mcp-1\",\"turnId\":\"turn-mcp-1\",\"clientRequestId\":\"req-mcp-1\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.structuredContent.status").value("completed"))
            .andExpect(jsonPath("$.result.structuredContent.result.entityName").value("商机"))
            .andExpect(jsonPath("$.result.structuredContent.result.records[0].summary").value(org.hamcrest.Matchers.containsString("Alpha")))
            .andExpect(jsonPath("$.result.structuredContent.result.records[0].summary").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
            .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("### 查询结果")))
            .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("不要再次调用"))))
            .andExpect(jsonPath("$.result.structuredContent.result.schemaCode").doesNotExist())
            .andExpect(jsonPath("$.result.structuredContent.matchedMetadata.code").doesNotExist());
        mockMvc.perform(post("/api/mcp/cloudpivot")
                .header("X-CPClaw-Installation-Id", "client-alice")
                .header("X-CPClaw-CloudPivot-Username", "alice")
                .header("X-CPClaw-CloudPivot-Password", "secret")
                .contentType(MediaType.APPLICATION_JSON)
            .content("{\"jsonrpc\":\"2.0\",\"id\":\"6\",\"method\":\"tools/call\",\"params\":{\"name\":\"cpclaw_cloudpivot_agent\",\"arguments\":{\"request\":\"查询商机情况\",\"conversationId\":\"conv-mcp-1\",\"turnId\":\"turn-mcp-1\",\"clientRequestId\":\"req-mcp-1\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.structuredContent.task.id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())))
            .andExpect(jsonPath("$.result.structuredContent.completion.state").value("completed"));
        org.mockito.Mockito.verify(cloudPivotConnector).queryRecords("https://cloudpivot.example", "alice", "secret", "Opportunity", 20, true, 20);
        org.junit.jupiter.api.Assertions.assertEquals(credentialsBefore, credentialRepository.count());
    }

    @Test
    void returnsInsightArtifactAndCompleteMarkdownForNaturalLanguageAnalysis() throws Exception {
        SystemSettings settings = new SystemSettings();
        settings.setId("default");
        settings.setAdminCloudPivotBaseUrl("https://cloudpivot.example");
        settings.setCreatedAt(Instant.now());
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
        CloudPivotEntity entity = new CloudPivotEntity();
        entity.setId("mcp-insight-project");
        entity.setAppId("mcp-test-app");
        entity.setEntityCode("Project");
        entity.setName("项目");
        entity.setEntityType("biz");
        entity.setSyncedAt(Instant.now());
        entityRepository.save(entity);
        MetadataSearchDocument document = new MetadataSearchDocument();
        document.setId("mcp-insight-project-search");
        document.setObjectType("entity");
        document.setObjectId(entity.getId());
        document.setAppId("mcp-test-app");
        document.setEntityId(entity.getId());
        document.setName("项目");
        document.setCode("Project");
        document.setSearchText("项目 Project 在建项目");
        document.setEmbeddingText("项目");
        document.setGraphPath("项目管理 / 项目");
        document.setRiskLevel("low");
        document.setIndexedAt(Instant.now());
        document.setCreatedAt(Instant.now());
        metadataSearchDocumentRepository.save(document);
        mockMvc.perform(post("/api/settings/mcp/cloudpivot/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\":\"client-insight\",\"displayName\":\"分析终端\"}"))
            .andExpect(status().isOk());
        when(cloudPivotConnector.queryRecords("https://cloudpivot.example", "alice", "secret", "Project", 200, true, 20_000, List.of()))
            .thenReturn(new CloudPivotRuntimeQueryResult("Project", 2, List.of(
                java.util.Map.of("data", java.util.Map.of("name", "项目A")),
                java.util.Map.of("data", java.util.Map.of("name", "项目B"))
            ), "/api/project/list"));

        mockMvc.perform(post("/api/mcp/cloudpivot")
                .header("X-CPClaw-Installation-Id", "client-insight")
                .header("X-CPClaw-CloudPivot-Username", "alice")
                .header("X-CPClaw-CloudPivot-Password", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jsonrpc\":\"2.0\",\"id\":\"analysis-1\",\"method\":\"tools/call\",\"params\":{\"name\":\"yunshu_handle_request\",\"arguments\":{\"request\":\"分析项目整体情况\",\"conversationId\":\"host-conversation-1\",\"clientRequestId\":\"request-1\"}}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.structuredContent.status").value("completed"))
            .andExpect(jsonPath("$.result.structuredContent.output.artifact.type").value("data_insight"))
            .andExpect(jsonPath("$.result.structuredContent.output.artifact.kpis[0].label").value("项目数"))
            .andExpect(jsonPath("$.result.structuredContent.hostAction.type").value("compose_answer"))
            .andExpect(jsonPath("$.result.structuredContent.hostAction.allowAnotherMcpCallThisTurn").value(false))
            .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.containsString("### 数据范围与口径")))
            .andExpect(jsonPath("$.result.content[0].text").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Project"))));
        org.junit.jupiter.api.Assertions.assertEquals(0, credentialRepository.count());
    }
}
