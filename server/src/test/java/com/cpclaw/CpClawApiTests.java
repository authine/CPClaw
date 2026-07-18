package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.vector.MetadataVectorSearch;
import com.cpclaw.vector.VectorSearchCandidate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.cloudpivot.allow-metadata-fallback=true",
    "cpclaw.metadata.graphify.output-directory=${java.io.tmpdir}/cpclaw-test-graphify"
})
@AutoConfigureMockMvc
class CpClawApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudPivotConnector cloudPivotConnector;

    @Autowired
    private FakeMetadataVectorSearch fakeMetadataVectorSearch;

    @Autowired
    private CloudPivotDataItemRepository dataItemRepository;

    @Autowired
    private CloudPivotEntityRelationRepository relationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mvpApiFlowWorks() throws Exception {
        configureCloudPivotMock();

        mockMvc.perform(post("/api/settings/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cloudPivotBaseUrl":"https://cloudpivot.example.local",
                      "cloudPivotUsername":"demo-user",
                      "cloudPivotPassword":"test-value",
                      "modelName":"openai-compatible-demo",
                      "modelApiBaseUrl":"https://model.example.local",
                      "modelApiKey":"test-value",
                      "modelDisplayName":"演示模型",
                      "supportsThinking":true,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userCloudPivot.hasPassword").value(true))
            .andExpect(jsonPath("$.data.models[0].hasApiKey").value(true));

        mockMvc.perform(post("/api/settings/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetBaseUrl":"https://cloudpivot-admin.example.local",
                      "username":"admin-user",
                      "password":"test-value",
                      "searchEngineType":"mysql",
                      "searchEndpoint":""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMetadata.hasPassword").value(true));

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userCloudPivot.hasPassword").value(true))
            .andExpect(jsonPath("$.data.adminMetadata.hasPassword").value(true));

        mockMvc.perform(get("/api/settings/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/settings/cloudpivot/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/api/settings/metadata-cloudpivot/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/api/metadata/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("cloudpivot-metadata-initialized"))
            .andExpect(jsonPath("$.data.entityCount").value(6))
            .andExpect(jsonPath("$.data.dataItemCount").value(10))
            .andExpect(jsonPath("$.data.relationCount").value(1))
            .andExpect(jsonPath("$.data.graphApplicationCount").value(4))
            .andExpect(jsonPath("$.data.graphNodeCount").value(22))
            .andExpect(jsonPath("$.data.graphEdgeCount").value(30))
            .andExpect(jsonPath("$.data.graphCoverageRate").value(1.0));

        assertEquals(10, dataItemRepository.count());
        assertEquals(1, relationRepository.count());
        CloudPivotEntityRelation relation = relationRepository.findAll().getFirst();
        assertNotNull(relation.getSourceDataItemId());
        CloudPivotDataItem relationDataItem = dataItemRepository.findById(relation.getSourceDataItemId()).orElseThrow();
        assertEquals("opportunityCustomer", relationDataItem.getDataItemCode());

        mockMvc.perform(get("/api/metadata/apps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(4));

        mockMvc.perform(get("/api/metadata/model"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.apps.length()").value(4))
            .andExpect(jsonPath("$.data.apiActions[0].apiCode").value("runtime_query_list"))
            .andExpect(jsonPath("$.data.apps[0].entities.length()").value(2))
            .andExpect(jsonPath("$.data.apps[0].entities[0].code").value("int_bu_oppor"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].dataItems.length()").value(4))
            .andExpect(jsonPath("$.data.apps[0].entities[0].dataItems[0].code").value("opportunityCustomer"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].relations[0].targetEntityCode").value("crm_customer"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions.length()").value(5))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[0].apiCode").value("business_rule_create"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[0].method").value("POST"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[0].path").value("/api/api/runtime/business_rule/zlcsstcrm/int_bu_oppor/Create"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[1].method").value("DELETE"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[2].operationType").value("query_collection"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[2].method").value("POST"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[2].path").value("/api/api/runtime/business_rule/zlcsstcrm/int_bu_oppor/GetList"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[3].method").value("PUT"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[4].method").value("GET"))
            .andExpect(jsonPath("$.data.apps[0].entities[0].apiActions[4].path").value("/api/api/runtime/business_rule/zlcsstcrm/int_bu_oppor/Load/{bizObjectId}"));

        mockMvc.perform(get("/api/metadata/graph/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.provider").value("graphify-v8-compatible"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.applicationCount").value(4))
            .andExpect(jsonPath("$.data.coveredApplicationCount").value(4))
            .andExpect(jsonPath("$.data.coverageRate").value(1.0))
            .andExpect(jsonPath("$.data.nodeCount").value(22))
            .andExpect(jsonPath("$.data.edgeCount").value(30))
            .andExpect(jsonPath("$.data.nodesByType.application").value(4))
            .andExpect(jsonPath("$.data.nodesByType.entity").value(6))
            .andExpect(jsonPath("$.data.nodesByType.data_item").value(10))
            .andExpect(jsonPath("$.data.edgesByType.APP_CONTAINS_ENTITY").value(6))
            .andExpect(jsonPath("$.data.edgesByType.ENTITY_HAS_DATA_ITEM").value(10))
            .andExpect(jsonPath("$.data.applications.length()").value(4));

        mockMvc.perform(get("/api/metadata/graph/neighborhood")
                .param("nodeId", "entity:zlcsstcrm:int_bu_oppor")
                .param("depth", "1")
                .param("limit", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.center.code").value("int_bu_oppor"))
            .andExpect(jsonPath("$.data.center.name").value("商机"))
            .andExpect(jsonPath("$.data.nodes.length()").value(9))
            .andExpect(jsonPath("$.data.edges[*].type", hasItem("APP_CONTAINS_ENTITY")))
            .andExpect(jsonPath("$.data.edges[*].type", hasItem("ENTITY_HAS_DATA_ITEM")))
            .andExpect(jsonPath("$.data.edges[*].type", hasItem("ENTITY_RELATES_TO_ENTITY")))
            .andExpect(jsonPath("$.data.edges[*].type", hasItem("API_OPERATES_ON_ENTITY")));

        mockMvc.perform(get("/api/metadata/graph/neighborhood")
                .param("nodeId", "entity:zlcsstcrm:int_bu_oppor")
                .param("depth", "2")
                .param("limit", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nodes.length()").value(11))
            .andExpect(jsonPath("$.data.truncated").value(false))
            .andExpect(jsonPath("$.data.nodes[*].code", not(hasItem("pm_project"))))
            .andExpect(jsonPath("$.data.nodes[*].code", not(hasItem("business_opportunity"))));

        mockMvc.perform(get("/api/metadata/graph/export"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.directed").value(true))
            .andExpect(jsonPath("$.multigraph").value(false))
            .andExpect(jsonPath("$.graph.provider").value("graphify-v8-compatible"))
            .andExpect(jsonPath("$.nodes.length()").value(22))
            .andExpect(jsonPath("$.links.length()").value(30))
            .andExpect(jsonPath("$.nodes[0].file_type").value("cloudpivot_metadata"));

        List<String> graphNodeKeysBefore = jdbcTemplate.queryForList(
            "SELECT stable_key FROM metadata_graph_nodes WHERE snapshot_id = (SELECT id FROM metadata_graph_snapshots WHERE status = 'ACTIVE') ORDER BY stable_key",
            String.class
        );
        List<String> graphEdgeKeysBefore = jdbcTemplate.queryForList(
            "SELECT stable_key FROM metadata_graph_edges WHERE snapshot_id = (SELECT id FROM metadata_graph_snapshots WHERE status = 'ACTIVE') ORDER BY stable_key",
            String.class
        );
        mockMvc.perform(post("/api/metadata/graph/rebuild"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nodeCount").value(22))
            .andExpect(jsonPath("$.data.edgeCount").value(30))
            .andExpect(jsonPath("$.data.coverageRate").value(1.0));
        List<String> graphNodeKeysAfter = jdbcTemplate.queryForList(
            "SELECT stable_key FROM metadata_graph_nodes WHERE snapshot_id = (SELECT id FROM metadata_graph_snapshots WHERE status = 'ACTIVE') ORDER BY stable_key",
            String.class
        );
        List<String> graphEdgeKeysAfter = jdbcTemplate.queryForList(
            "SELECT stable_key FROM metadata_graph_edges WHERE snapshot_id = (SELECT id FROM metadata_graph_snapshots WHERE status = 'ACTIVE') ORDER BY stable_key",
            String.class
        );
        assertEquals(graphNodeKeysBefore, graphNodeKeysAfter);
        assertEquals(graphEdgeKeysBefore, graphEdgeKeysAfter);
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM metadata_graph_snapshots", Integer.class));

        mockMvc.perform(get("/api/metadata/search").param("query", "元数据对象"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("元数据对象"));

        mockMvc.perform(get("/api/metadata/search").param("query", "查询业务对象数据集合"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].objectType").value("api_endpoint"))
            .andExpect(jsonPath("$.data[0].code").value("runtime_query_list"));

        mockMvc.perform(get("/api/metadata/search").param("query", "销售机会有多少？"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("int_bu_oppor"));

        mockMvc.perform(get("/api/metadata/search").param("query", "int_bu_oppor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("int_bu_oppor"));

        mockMvc.perform(get("/api/metadata/search").param("query", "分析客户下面的商机"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("int_bu_oppor"));

        mockMvc.perform(get("/api/metadata/search").param("query", "项目基础数据里的商机"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("business_opportunity"));

        mockMvc.perform(get("/api/metadata/search").param("query", "CRM客户数量"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("客户"))
            .andExpect(jsonPath("$.data[0].code").value("crm_customer"));

        fakeMetadataVectorSearch.setEnabled(true);
        fakeMetadataVectorSearch.setQuery("帮我看看销售进展池子");
        fakeMetadataVectorSearch.setCode("int_bu_oppor");
        mockMvc.perform(get("/api/metadata/search").param("query", "帮我看看销售进展池子"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("int_bu_oppor"))
            .andExpect(jsonPath("$.data[0].reason").value(containsString("向量语义召回")));

        fakeMetadataVectorSearch.setQuery("int_bu_oppor");
        fakeMetadataVectorSearch.setCode("crm_customer");
        mockMvc.perform(get("/api/metadata/search").param("query", "int_bu_oppor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("商机"))
            .andExpect(jsonPath("$.data[0].code").value("int_bu_oppor"));

        mockMvc.perform(get("/api/metadata/search").param("query", "customerType"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].objectType").value("data_item"))
            .andExpect(jsonPath("$.data[0].code").value("customerType"));

        mockMvc.perform(get("/api/metadata/search").param("query", "opportunityCustomer"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].objectType").value("data_item"))
            .andExpect(jsonPath("$.data[0].code").value("opportunityCustomer"));
        fakeMetadataVectorSearch.reset();

        MvcResult conversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"MVP 测试会话\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();

        String body = conversation.getResponse().getContentAsString();
        String conversationId = body.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        MvcResult streamRequest = mockMvc.perform(post("/api/conversations/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "conversationId":"",
                      "content":"系统有多少商机？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        MvcResult streamResponse = mockMvc.perform(asyncDispatch(streamRequest))
            .andExpect(status().isOk())
            .andReturn();
        String streamBody = streamResponse.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(streamBody.contains("event:thought"));
        assertTrue(streamBody.contains("event:execution"));
        assertTrue(streamBody.contains("event:answer_start"));
        assertTrue(streamBody.contains("event:answer_delta"));
        assertTrue(streamBody.contains("event:answer_end"));
        assertTrue(streamBody.contains("event:final"));
        assertTrue(streamBody.indexOf("event:thought") < streamBody.indexOf("event:execution"));
        assertTrue(streamBody.indexOf("event:execution") < streamBody.indexOf("event:answer_start"));
        assertTrue(streamBody.indexOf("event:answer_start") < streamBody.indexOf("event:answer_delta"));
        assertTrue(streamBody.indexOf("event:answer_delta") < streamBody.indexOf("event:final"));

        MvcResult modelStreamRequest = mockMvc.perform(post("/api/conversations/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "conversationId":"",
                      "content":"商机都是什么情况？",
                      "thinkingEnabled":true,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        MvcResult modelStreamResponse = mockMvc.perform(asyncDispatch(modelStreamRequest))
            .andExpect(status().isOk())
            .andReturn();
        String modelStreamBody = modelStreamResponse.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(modelStreamBody.contains("event:answer_start"));
        assertTrue(modelStreamBody.contains("\"mode\":\"model\""));
        assertTrue(modelStreamBody.split("event:answer_delta", -1).length - 1 >= 2);
        assertTrue(modelStreamBody.contains("event:answer_end"));
        assertFalse(modelStreamBody.contains("event:answer_reset"));
        assertTrue(modelStreamBody.indexOf("event:answer_delta") < modelStreamBody.indexOf("event:final"));

        MvcResult insightStreamRequest = mockMvc.perform(post("/api/conversations/messages/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .content("""
                    {
                      "conversationId":"",
                      "content":"商机整体经营情况怎么样？",
                      "thinkingEnabled":true,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(request().asyncStarted())
            .andReturn();
        MvcResult insightStreamResponse = mockMvc.perform(asyncDispatch(insightStreamRequest))
            .andExpect(status().isOk())
            .andReturn();
        String insightStreamBody = insightStreamResponse.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(insightStreamBody.contains("event:thought"));
        assertTrue(insightStreamBody.contains("构建业务元数据图"));
        assertTrue(insightStreamBody.contains("event:execution"));
        assertTrue(insightStreamBody.contains("cloudpivot_insight_report") || insightStreamBody.contains("insightReport"));
        assertTrue(insightStreamBody.contains("stage-distribution"));
        assertTrue(insightStreamBody.contains("relatedQuestions"));
        assertTrue(insightStreamBody.indexOf("event:answer_delta") < insightStreamBody.indexOf("event:final"));

        MvcResult deletedConversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"待删除会话\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
        String deletedConversationBody = deletedConversation.getResponse().getContentAsString();
        String deletedConversationId = deletedConversationBody.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"查询元数据对象",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(deletedConversationId)))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/conversations/" + deletedConversationId))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/conversations/" + deletedConversationId))
            .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/conversations"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("会话ID缺失，无法删除"));

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"   ",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(conversationId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("请输入要处理的内容"));

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"查询元数据对象",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"));

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"查询系统商机情况",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false));

        MvcResult countOpportunityResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"系统有多少商机？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps").isArray())
            .andExpect(jsonPath("$.data.steps[0].kind").value("thought"))
            .andExpect(jsonPath("$.data.steps[?(@.kind == 'thought')]").isNotEmpty())
            .andExpect(jsonPath("$.data.steps[?(@.kind == 'execution')]").isNotEmpty())
            .andExpect(jsonPath("$.data.steps[?(@.title == '云枢数据返回')]").isNotEmpty())
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("总计 **237** 条")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("### 执行过程"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("schemaCode=`int_bu_oppor`"))))
            .andReturn();
        String countOpportunityBody = countOpportunityResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(countOpportunityBody.contains("总计 **237** 条"));
        assertFalse(countOpportunityBody.contains("### 执行过程"));
        assertTrue(countOpportunityBody.contains("schemaCode=int_bu_oppor"));
        assertTrue(countOpportunityBody.contains("/api/runtime/query/listSkipQueryListV2"));
        assertFalse(countOpportunityBody.contains("system_opportunity"));
        assertFalse(countOpportunityBody.contains("local-fallback"));
        assertFalse(countOpportunityBody.contains("演示编码"));

        MvcResult currentOpportunityCountResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"系统现在有多少商机？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("总计 **237** 条")))
            .andReturn();
        String currentOpportunityCountBody = currentOpportunityCountResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(currentOpportunityCountBody.contains("负责人/销售=现在"));

        MvcResult ownerFilteredCountResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"张三有多少商机？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("负责人/销售=张三"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("张三")))
            .andReturn();
        String ownerFilteredCountBody = ownerFilteredCountResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(ownerFilteredCountBody.contains("共 **1** 条"));
        assertTrue(ownerFilteredCountBody.contains("owner"));
        assertFalse(ownerFilteredCountBody.contains("总计 **237** 条"));

        MvcResult multiTurnConversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"多轮上下文测试\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
        String multiTurnConversationBody = multiTurnConversation.getResponse().getContentAsString();
        String multiTurnConversationId = multiTurnConversationBody.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"系统有多少商机？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(multiTurnConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("总计 **237** 条")))
            .andExpect(jsonPath("$.data.assistantMessage.metadataJson").value(containsString("agentRunId")));

        mockMvc.perform(get("/api/conversations/" + multiTurnConversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.messages[1].metadataJson").value(containsString("agentRunId")))
            .andExpect(jsonPath("$.data.messages[1].metadataJson").value(containsString("runtime-query")));

        MvcResult stageFollowUpResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"都处于什么阶段？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(multiTurnConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("本轮承接上一轮业务对象"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("按阶段分布")))
            .andReturn();
        String stageFollowUpBody = stageFollowUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(stageFollowUpBody.contains("方案确认"));
        assertTrue(stageFollowUpBody.contains("需求沟通"));
        assertTrue(stageFollowUpBody.contains("合同审批"));
        assertTrue(stageFollowUpBody.contains("schemaCode=int_bu_oppor"));
        assertFalse(stageFollowUpBody.contains("schemaCode=crm_customer"));

        MvcResult amountRankingResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"哪些商机金额比较高？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(multiTurnConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("本轮承接上一轮业务对象"))))
            .andReturn();
        String amountRankingBody = amountRankingResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(amountRankingBody.contains("金额最高的商机"));
        assertTrue(amountRankingBody.contains("金额较高的商机"));
        assertTrue(amountRankingBody.contains("北京菲斯曼供热"));
        assertTrue(amountRankingBody.contains("860000"));
        assertFalse(amountRankingBody.contains("前 10 条记录摘要"));
        assertFalse(amountRankingBody.contains("名称：北京菲斯曼供热，名称：北京菲斯曼供热"));

        MvcResult explicitPathConversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"显式路径上下文测试\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
        String explicitPathConversationBody = explicitPathConversation.getResponse().getContentAsString();
        String explicitPathConversationId = explicitPathConversationBody.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"项目基础数据里的商机有多少？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(explicitPathConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("总计 **12** 条")));

        MvcResult explicitPathFollowUpResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"分别在什么阶段？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(explicitPathConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("本轮承接上一轮业务对象"))))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("business_opportunity"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("按阶段分布")))
            .andReturn();
        String explicitPathFollowUpBody = explicitPathFollowUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(explicitPathFollowUpBody.contains("项目立项"));
        assertTrue(explicitPathFollowUpBody.contains("资源确认"));
        assertTrue(explicitPathFollowUpBody.contains("schemaCode=business_opportunity"));
        assertFalse(explicitPathFollowUpBody.contains("schemaCode=int_bu_oppor"));

        MvcResult projectConversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"project follow-up\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
        String projectConversationBody = projectConversation.getResponse().getContentAsString();
        String projectConversationId = projectConversationBody.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"\u7cfb\u7edf\u73b0\u5728\u6709\u591a\u5c11\u4e2a\u9879\u76ee\uff1f",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(projectConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("\u9879\u76ee"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("7057")));

        MvcResult projectAmountFollowUp = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"\u8fdb\u884c\u4e2d\u7684\u9879\u76ee\u6709\u591a\u5c11\uff0c\u9879\u76ee\u91d1\u989d\u6709\u591a\u5c11\uff1f",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(projectConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("\u9879\u76ee"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("pm_project"))))
            .andReturn();
        String projectAmountBody = projectAmountFollowUp.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(projectAmountBody.contains("schemaCode=pm_project"));
        assertTrue(projectAmountBody.contains("2100000") || projectAmountBody.contains("2,100,000") || projectAmountBody.contains("2100000.0"));
        assertFalse(projectAmountBody.contains("schemaCode=int_bu_oppor"));
        assertFalse(projectAmountBody.contains("schemaCode=crm_customer"));

        MvcResult colloquialOpportunityResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"CRM下面有哪些机会",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.candidates[0].type").value("entity"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("237")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("北京菲斯曼供热")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("schemaCode=`int_bu_oppor`"))))
            .andReturn();
        String colloquialOpportunityBody = colloquialOpportunityResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(colloquialOpportunityBody.contains("237"));
        assertTrue(colloquialOpportunityBody.contains("北京菲斯曼供热"));
        assertTrue(colloquialOpportunityBody.contains("schemaCode=int_bu_oppor"));
        assertFalse(colloquialOpportunityBody.contains("前 10 条记录摘要"));

        MvcResult opportunitySituationResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"商机都是什么情况？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("结论摘要")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("前 10 条记录摘要"))))
            .andReturn();
        String opportunitySituationBody = opportunitySituationResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(opportunitySituationBody.contains("schemaCode=int_bu_oppor"));
        assertFalse(opportunitySituationBody.contains("名称：北京菲斯曼供热，名称：北京菲斯曼供热"));

        MvcResult countCustomerResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"系统有多少个客户？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("客户"))
            .andExpect(jsonPath("$.data.steps[*].title").value(hasItem("校验执行结果")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("总计 **58** 条")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("### 执行过程"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("schemaCode=`crm_customer`"))))
            .andReturn();
        String countCustomerBody = countCustomerResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(countCustomerBody.contains("总计 **58** 条"));
        assertFalse(countCustomerBody.contains("### 执行过程"));
        assertTrue(countCustomerBody.contains("schemaCode=crm_customer"));
        assertFalse(countCustomerBody.contains("system_customer"));
        assertFalse(countCustomerBody.contains("local-fallback"));
        assertFalse(countCustomerBody.contains("总计 **237** 条"));

        MvcResult customerFollowUpConversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"客户省份追问测试\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();
        String customerFollowUpConversationBody = customerFollowUpConversation.getResponse().getContentAsString();
        String customerFollowUpConversationId = customerFollowUpConversationBody.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"系统有多少个客户？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(customerFollowUpConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].name").value("客户"));

        MvcResult provinceFollowUpResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"这些客户都属于哪些省份？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(customerFollowUpConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("客户"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("本轮承接上一轮业务对象"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("按省份分布")))
            .andReturn();
        String provinceFollowUpBody = provinceFollowUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(provinceFollowUpBody.contains("schemaCode=crm_customer"));
        assertTrue(provinceFollowUpBody.contains("广东省：2 个客户"));
        assertTrue(provinceFollowUpBody.contains("上海市：1 个客户"));
        assertFalse(provinceFollowUpBody.contains("schemaCode=system_customer"));
        assertFalse(provinceFollowUpBody.contains("schemaCode=Test009"));

        MvcResult customerLifecycleFollowUpResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"新客户多还是老客户多",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(customerFollowUpConversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("客户"))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("本轮承接上一轮业务对象"))))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("分析维度"))))
            .andExpect(jsonPath("$.data.steps[*].status").value(hasItem(containsString("新老客户"))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("新老客户对比")))
            .andReturn();
        String customerLifecycleFollowUpBody = customerLifecycleFollowUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(customerLifecycleFollowUpBody.contains("schemaCode=crm_customer"));
        assertTrue(customerLifecycleFollowUpBody.contains("新客户：2 个客户"));
        assertTrue(customerLifecycleFollowUpBody.contains("老客户：2 个客户"));
        assertFalse(customerLifecycleFollowUpBody.contains("schemaCode=system_customer"));
        assertFalse(customerLifecycleFollowUpBody.contains("schemaCode=Test009"));

        MvcResult yearlyCustomerResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"每年的客户量情况怎么样？",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("客户"))
            .andExpect(jsonPath("$.data.steps[*].title").value(hasItem("理解用户问题")))
            .andExpect(jsonPath("$.data.steps[*].title").value(hasItem("校验执行结果")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(containsString("按年客户量分析")))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("原始数据摘要"))))
            .andReturn();
        String yearlyCustomerBody = yearlyCustomerResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(yearlyCustomerBody.contains("按年客户量分析"));
        assertTrue(yearlyCustomerBody.contains("2023 年：2 个客户"));
        assertTrue(yearlyCustomerBody.contains("趋势判断"));
        assertTrue(yearlyCustomerBody.contains("schemaCode=crm_customer"));

        MvcResult analysisResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"分析系统中的商机信息",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("analyze_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andReturn();
        String analysisBody = analysisResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(analysisBody.contains("结论摘要"));
        assertTrue(analysisBody.contains("商机"));
        assertFalse(analysisBody.contains("excelType="));
        assertFalse(analysisBody.contains("{id="));

        MvcResult opportunityDetailResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"\u7b2c\u4e00\u6761\u5546\u673a\u7684\u8be6\u60c5\u8bf4\u4e00\u4e0b",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.candidates[0].type").value("entity"))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("schemaCode="))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("excelType="))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("propertyType="))))
            .andExpect(jsonPath("$.data.assistantMessage.content").value(not(containsString("{id="))))
            .andReturn();
        String opportunityDetailBody = opportunityDetailResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(opportunityDetailBody.contains("\u5f20\u4e09"));
        assertTrue(opportunityDetailBody.contains("\u5317\u4eac\u83f2\u65af\u66fc\u4f9b\u70ed\u6280\u672f\u6709\u9650\u516c\u53f8"));
        assertFalse(opportunityDetailBody.contains("QL20250324009026"));
        assertFalse(opportunityDetailBody.contains("operator"));

        MvcResult clarificationResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"帮我处理一下",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andReturn();
        String clarificationBody = clarificationResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(clarificationBody.contains("我需要再确认一下你的意图"));
        assertTrue(clarificationBody.contains("你想做什么动作"));

        MvcResult agentResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"查询元数据对象 password=plain-text-secret {\\\"apiKey\\\":\\\"json-secret\\\"}",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(conversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("query_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("元数据对象"))
            .andReturn();

        String agentBody = agentResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(agentBody.contains("元数据对象"));
        assertTrue(agentBody.contains("结论摘要"));
        assertFalse(agentBody.contains("plain-text-secret"));
        assertFalse(agentBody.contains("\"json-secret\""));
        assertFalse(agentBody.contains("已生成结果预览"));
        String queryAgentRunId = agentBody.replaceAll(".*\\\"agentRunId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        MvcResult queryAuditResult = mockMvc.perform(get("/api/audit/agent-runs/" + queryAgentRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tools.length()").value(2))
            .andExpect(jsonPath("$.data.tools[1].toolName").value("cloudpivot_runtime_query"))
            .andReturn();
        String queryAuditBody = queryAuditResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(queryAuditBody.contains("react-reflection-mvp"));
        assertTrue(queryAuditBody.contains("reflectionJson"));
        assertFalse(queryAuditBody.contains("plain-text-secret"));
        assertFalse(queryAuditBody.contains("\\\"json-secret\\\""));

        mockMvc.perform(get("/api/conversations/" + conversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.messages.length()").value(2));

        MvcResult writeResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"修改元数据对象",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(conversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("update_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
            .andReturn();

        String writeBody = writeResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String agentRunId = writeBody.replaceAll(".*\\\"agentRunId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        String confirmationId = writeBody.replaceAll(".*\\\"confirmationId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(post("/api/audit/confirmations/" + confirmationId + "/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("confirmed"));

        MvcResult writeAuditResult = mockMvc.perform(get("/api/audit/agent-runs/" + agentRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("completed"))
            .andExpect(jsonPath("$.data.tools.length()").value(1))
            .andReturn();
        String writeAuditBody = writeAuditResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(writeAuditBody.contains("pending_confirmation"));

        MvcResult followUpResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"给第一条商机写一条跟进记录",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(conversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates[0].name").value("商机"))
            .andExpect(jsonPath("$.data.steps[*].title").value(hasItem("生成澄清问题")))
            .andReturn();
        String followUpBody = followUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(followUpBody.contains("跟进内容"));
        assertTrue(followUpBody.contains("理解用户问题"));
        MvcResult deleteResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"\u5220\u9664\u7b2c\u4e00\u6761\u5546\u673a",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """.formatted(conversationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("delete_data"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true))
            .andReturn();
        String deleteBody = deleteResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String deleteConfirmationId = deleteBody.replaceAll(".*\\\"confirmationId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(post("/api/audit/confirmations/" + deleteConfirmationId + "/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("executed"))
            .andExpect(jsonPath("$.data.executed").value(true));
        verify(cloudPivotConnector).deleteRecord(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private void configureCloudPivotMock() {
        when(cloudPivotConnector.testConnection(anyString(), anyString(), anyString())).thenReturn(true);
        when(cloudPivotConnector.fetchMetadata(anyString(), anyString(), anyString())).thenReturn(new CloudPivotMetadataSnapshot(
            List.of(
                new CloudPivotMetadataSnapshot.AppMetadata("zlcsstcrm", "CRM", "真实云枢 CRM 应用"),
                new CloudPivotMetadataSnapshot.AppMetadata("customer_management", "项目基础数据", "用于验证同名商机候选排序"),
                new CloudPivotMetadataSnapshot.AppMetadata("project_management", "项目管理", "用于验证同名客户候选排序"),
                new CloudPivotMetadataSnapshot.AppMetadata("metadata_app", "元数据应用", "真实云枢元数据应用")
            ),
            List.of(
                new CloudPivotMetadataSnapshot.EntityMetadata("zlcsstcrm", "int_bu_oppor", "商机", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("zlcsstcrm", "crm_customer", "客户", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("customer_management", "business_opportunity", "商机", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("project_management", "pm_project", "项目", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("project_management", "Test009", "客户", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_object", "元数据对象", "data", "low")
            ),
            mockDataItems(),
            mockRelations(),
            mockApiEndpoints()
        ));
        when(cloudPivotConnector.queryRecords(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean()))
            .thenAnswer(invocation -> runtimeResult(invocation.getArgument(3), invocation.getArgument(4)));
        when(cloudPivotConnector.queryRecords(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyInt()))
            .thenAnswer(invocation -> runtimeResult(invocation.getArgument(3), Math.min(invocation.getArgument(4), invocation.getArgument(6))));
        when(cloudPivotConnector.queryRecords(anyString(), anyString(), anyString(), anyString(), anyInt(), anyBoolean(), anyInt(), anyList()))
            .thenAnswer(invocation -> runtimeResult(invocation.getArgument(3), Math.min(invocation.getArgument(4), invocation.getArgument(6)), invocation.getArgument(7)));
        when(cloudPivotConnector.deleteRecord(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenAnswer(invocation -> new CloudPivotOperationResult(
                true,
                "delete",
                invocation.getArgument(3),
                invocation.getArgument(4),
                invocation.getArgument(5),
                "/api/api/runtime/business_rule/" + invocation.getArgument(3) + "/" + invocation.getArgument(4) + "/Delete/" + invocation.getArgument(5),
                "删除接口已执行",
                Map.of("mock", true)
            ));
    }

    private List<CloudPivotMetadataSnapshot.DataItemMetadata> mockDataItems() {
        return List.of(
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "int_bu_oppor", "opportunityCustomer", "客户", "关联表单", true, true, "crm_customer", "商机关联客户", "{\"targetSchemaCode\":\"crm_customer\"}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "int_bu_oppor", "stage", "阶段", "TEXT", false, false, "", "商机阶段", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "int_bu_oppor", "amount", "金额", "NUMBER", false, false, "", "商机金额", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "int_bu_oppor", "owner", "负责人", "STAFF_SELECTOR", false, false, "", "商机负责人/销售", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "crm_customer", "customerType", "客户类型", "TEXT", false, false, "", "新老客户分类", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("zlcsstcrm", "crm_customer", "province", "省份", "TEXT", false, false, "", "客户所在省份", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("customer_management", "business_opportunity", "stage", "阶段", "TEXT", false, false, "", "项目基础数据商机阶段", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("project_management", "pm_project", "projectStatus", "项目状态", "TEXT", false, false, "", "项目当前推进状态", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("project_management", "pm_project", "projectAmount", "项目金额", "NUMBER", false, false, "", "项目金额", "{}"),
            new CloudPivotMetadataSnapshot.DataItemMetadata("metadata_app", "metadata_object", "name", "名称", "TEXT", true, false, "", "元数据对象名称", "{}")
        );
    }

    private List<CloudPivotMetadataSnapshot.ApiEndpointMetadata> mockApiEndpoints() {
        return List.of(
            new CloudPivotMetadataSnapshot.ApiEndpointMetadata(
                "runtime_query_list",
                "查询业务对象数据集合",
                "POST",
                "/api/runtime/query/listSkipQueryListV2",
                "runtime_data",
                "query_collection",
                "low",
                false,
                "{\"required\":[\"schemaCode\"],\"properties\":{\"schemaCode\":\"业务对象模型编码\",\"page\":\"页码\",\"size\":\"分页大小\"}}",
                "{\"properties\":{\"totalElements\":\"总数\",\"content\":\"记录集合\"}}",
                "查询指定业务对象的数据集合、分页列表和总数",
                "entity",
                "{\"verified\":true}"
            ),
            new CloudPivotMetadataSnapshot.ApiEndpointMetadata(
                "runtime_form_load_new",
                "查询单个业务对象详情",
                "GET",
                "/api/runtime/form/loadNew",
                "runtime_data",
                "query_detail",
                "low",
                false,
                "{\"required\":[\"schemaCode\",\"objectId\"],\"properties\":{\"schemaCode\":\"业务对象模型编码\",\"objectId\":\"记录ID\"}}",
                "{\"properties\":{\"bizObject\":\"业务对象详情\",\"data\":\"业务字段\"}}",
                "查询单个业务对象完整详情",
                "entity",
                "{\"verified\":true}"
            )
        );
    }

    private List<CloudPivotMetadataSnapshot.EntityRelationMetadata> mockRelations() {
        return List.of(new CloudPivotMetadataSnapshot.EntityRelationMetadata(
            "zlcsstcrm",
            "int_bu_oppor",
            "opportunityCustomer",
            "crm_customer",
            "relevance_form",
            "商机关联客户",
            "{\"targetSchemaCode\":\"crm_customer\"}"
        ));
    }

    private CloudPivotRuntimeQueryResult runtimeResult(String schemaCode, int pageSize) {
        if ("int_bu_oppor".equals(schemaCode)) {
            List<Map<String, Object>> records = List.of(
                Map.of("id", "opp-001", "data", Map.ofEntries(
                    Map.entry("instanceName", "北京菲斯曼供热"),
                    Map.entry("stage", "方案确认"),
                    Map.entry("amount", 860000),
                    Map.entry("owner", List.of(Map.of("name", "张三", "id", "user-001", "type", 3, "excelType", ""))),
                    Map.entry("customer", Map.of("cust_fullname", "北京菲斯曼供热技术有限公司", "schemaCode", "crm_customer", "id", "cus-001", "propertyType", "relevance_form")),
                    Map.entry("clues_id", Map.of("sequenceNo", "QL20250324009026", "schemaCode", "sql_line", "id", "clue-001")),
                    Map.entry("sales_org_id", Map.of("org_name", "华北销售组织", "schemaCode", "sales_org", "id", "org-001")),
                    Map.entry("ownerDeptId", List.of(Map.of("name", "销售一部", "id", "dept-001", "unitType", 1))),
                    Map.entry("createdAt", "2024-01-12")
                )),
                Map.of("id", "opp-002", "data", Map.of("instanceName", "云南交投经营管理系统升级", "stage", "需求沟通", "amount", 420000, "owner", "销售二部", "createdAt", "2024-03-08")),
                Map.of("id", "opp-003", "data", Map.of("instanceName", "国能--低代码平台项目", "stage", "合同审批", "amount", 260000, "owner", "客户成功部", "createdAt", "2023-11-20"))
            );
            return new CloudPivotRuntimeQueryResult(schemaCode, 237, limit(records, pageSize), "/api/runtime/query/listSkipQueryListV2");
        }
        if ("crm_customer".equals(schemaCode)) {
            List<Map<String, Object>> records = List.of(
                Map.of("id", "cus-001", "data", Map.of("name", "华东制造集团", "industry", "制造业", "province", "上海市", "customerType", "老客户", "owner", "销售一部", "createdAt", "2022-03-18")),
                Map.of("id", "cus-002", "data", Map.of("name", "西南零售连锁", "industry", "零售", "province", "广东省", "customerType", "新客户", "owner", "销售二部", "createdAt", "2023-07-09")),
                Map.of("id", "cus-003", "data", Map.of("name", "总部存量客户", "industry", "集团总部", "province", "北京市", "customerType", "老客户", "owner", "客户成功部", "createdAt", "2023-11-26")),
                Map.of("id", "cus-004", "data", Map.of("name", "华南新能源科技", "industry", "新能源", "province", "广东省", "customerType", "新客户", "owner", "销售三部", "createdAt", "2024-05-14"))
            );
            return new CloudPivotRuntimeQueryResult(schemaCode, 58, limit(records, pageSize), "/api/runtime/query/listSkipQueryListV2");
        }
        if ("metadata_object".equals(schemaCode)) {
            List<Map<String, Object>> records = List.of(Map.of("id", "meta-001", "data", Map.of("name", "业务模型", "type", "元数据")));
            return new CloudPivotRuntimeQueryResult(schemaCode, 1, limit(records, pageSize), "/api/runtime/query/listSkipQueryListV2");
        }
        if ("business_opportunity".equals(schemaCode)) {
            List<Map<String, Object>> records = List.of(
                Map.of("id", "biz-opp-001", "data", Map.of("instanceName", "项目基础数据商机 A", "stage", "项目立项", "amount", 120000, "createdAt", "2024-04-18")),
                Map.of("id", "biz-opp-002", "data", Map.of("instanceName", "项目基础数据商机 B", "stage", "资源确认", "amount", 90000, "createdAt", "2024-05-03"))
            );
            return new CloudPivotRuntimeQueryResult(schemaCode, 12, limit(records, pageSize), "/api/runtime/query/listSkipQueryListV2");
        }
        if ("pm_project".equals(schemaCode)) {
            List<Map<String, Object>> records = List.of(
                Map.of("id", "project-001", "data", Map.of("instanceName", "在建项目 A", "projectStatus", "进行中", "projectAmount", 1200000, "createdAt", "2024-04-18")),
                Map.of("id", "project-002", "data", Map.of("instanceName", "在建项目 B", "projectStatus", "进行中", "projectAmount", 900000, "createdAt", "2024-05-03")),
                Map.of("id", "project-003", "data", Map.of("instanceName", "已完成项目 C", "projectStatus", "已完成", "projectAmount", 500000, "createdAt", "2023-12-01"))
            );
            return new CloudPivotRuntimeQueryResult(schemaCode, 7057, limit(records, pageSize), "/api/runtime/query/listSkipQueryListV2");
        }
        if ("Test009".equals(schemaCode)) {
            throw new AssertionError("Generic business questions should prefer CRM core schema, not secondary schema " + schemaCode);
        }
        return new CloudPivotRuntimeQueryResult(schemaCode, 0, List.of(), "/api/runtime/query/listSkipQueryListV2");
    }

    private CloudPivotRuntimeQueryResult runtimeResult(String schemaCode, int pageSize, List<RuntimeQueryFilter> filters) {
        CloudPivotRuntimeQueryResult result = runtimeResult(schemaCode, pageSize);
        if (filters == null || filters.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> matched = result.records().stream()
            .filter(record -> filters.stream().allMatch(filter -> testFilterMatches(record, filter)))
            .toList();
        return new CloudPivotRuntimeQueryResult(schemaCode, matched.size(), matched, result.sourceEndpoint());
    }

    private boolean testFilterMatches(Map<String, Object> record, RuntimeQueryFilter filter) {
        if (filter == null || !filter.valid()) {
            return true;
        }
        Optional<String> value = Optional.ofNullable(record.get("data"))
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(data -> data.get(filter.fieldCode()))
            .flatMap(this::readableTestValue);
        return value
            .map(actual -> normalizeTestValue(actual).contains(normalizeTestValue(filter.value())) || normalizeTestValue(filter.value()).contains(normalizeTestValue(actual)))
            .orElse(false);
    }

    private Optional<String> readableTestValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::readableTestValue).filter(Optional::isPresent).map(Optional::get).findFirst();
        }
        if (value instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null) {
                return Optional.of(String.valueOf(name));
            }
        }
        return Optional.of(String.valueOf(value));
    }

    private String normalizeTestValue(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private List<Map<String, Object>> limit(List<Map<String, Object>> records, int pageSize) {
        return records.stream().limit(Math.max(1, pageSize)).toList();
    }

    @TestConfiguration
    static class VectorSearchTestConfig {
        @Bean
        @Primary
        FakeMetadataVectorSearch fakeMetadataVectorSearch() {
            return new FakeMetadataVectorSearch();
        }
    }

    static class FakeMetadataVectorSearch implements MetadataVectorSearch {
        private boolean enabled;
        private String query = "";
        private String code = "";
        private List<MetadataSearchDocument> documents = List.of();

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void indexDocuments(List<MetadataSearchDocument> documents) {
            this.documents = documents == null ? List.of() : documents;
        }

        @Override
        public List<VectorSearchCandidate> search(String query) {
            if (!enabled || query == null || !query.contains(this.query) || code.isBlank()) {
                return List.of();
            }
            return documents.stream()
                .filter(document -> code.equals(document.getCode()))
                .findFirst()
                .map(document -> List.of(new VectorSearchCandidate(document.getId(), 0.93, "fake-test-embedding", "向量语义召回 similarity=0.9300")))
                .orElse(List.of());
        }

        void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        void setQuery(String query) {
            this.query = query;
        }

        void setCode(String code) {
            this.code = code;
        }

        void reset() {
            this.enabled = false;
            this.query = "";
            this.code = "";
        }
    }
}
