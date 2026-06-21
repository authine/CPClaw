package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.cloudpivot.allow-metadata-fallback=true"
})
@AutoConfigureMockMvc
class CpClawApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mvpApiFlowWorks() throws Exception {
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
            .andExpect(jsonPath("$.data.entityCount").value(7));

        mockMvc.perform(get("/api/metadata/apps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/metadata/search").param("query", "元数据对象"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].name").value("元数据对象"));

        MvcResult conversation = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"MVP 测试会话\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn();

        String body = conversation.getResponse().getContentAsString();
        String conversationId = body.replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

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
            .andExpect(jsonPath("$.data.candidates[0].name").value("系统商机"))
            .andExpect(jsonPath("$.data.steps[0].title").value("Observe 观察上下文"))
            .andExpect(jsonPath("$.data.steps[1].title").value("Think 理解意图"))
            .andExpect(jsonPath("$.data.steps[2].title").value("Act 执行动作"))
            .andExpect(jsonPath("$.data.steps[3].title").value("Reflect 反思检查"))
            .andReturn();
        String countOpportunityBody = countOpportunityResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(countOpportunityBody.contains("总计 **3** 条"));
        assertTrue(countOpportunityBody.contains("### 执行过程"));
        assertTrue(countOpportunityBody.contains("schemaCode=`system_opportunity`"));
        assertTrue(countOpportunityBody.contains("华东制造业数字化项目"));
        assertFalse(countOpportunityBody.contains("总计 **4** 条"));

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
            .andExpect(jsonPath("$.data.candidates[0].name").value("系统客户"))
            .andExpect(jsonPath("$.data.steps[3].title").value("Reflect 反思检查"))
            .andReturn();
        String countCustomerBody = countCustomerResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(countCustomerBody.contains("总计 **4** 条"));
        assertTrue(countCustomerBody.contains("### 执行过程"));
        assertTrue(countCustomerBody.contains("schemaCode=`system_customer`"));
        assertTrue(countCustomerBody.contains("华东制造集团"));
        assertFalse(countCustomerBody.contains("总计 **3** 条"));

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
            .andExpect(jsonPath("$.data.candidates[0].name").value("系统客户"))
            .andExpect(jsonPath("$.data.steps[0].title").value("Observe 观察上下文"))
            .andExpect(jsonPath("$.data.steps[3].title").value("Reflect 反思检查"))
            .andReturn();
        String yearlyCustomerBody = yearlyCustomerResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(yearlyCustomerBody.contains("按年客户量分析"));
        assertTrue(yearlyCustomerBody.contains("2023 年：2 个客户"));
        assertTrue(yearlyCustomerBody.contains("趋势判断"));
        assertTrue(yearlyCustomerBody.contains("schemaCode=`system_customer`"));
        assertTrue(yearlyCustomerBody.contains("原始数据摘要"));

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
            .andExpect(jsonPath("$.data.candidates[0].name").value("系统商机"))
            .andReturn();
        String analysisBody = analysisResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(analysisBody.contains("结论摘要"));
        assertTrue(analysisBody.contains("系统商机"));

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
        assertTrue(agentBody.contains("总计"));
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
            .andExpect(jsonPath("$.data.candidates[0].name").value("系统商机"))
            .andExpect(jsonPath("$.data.steps[3].title").value("Reflect 反思检查"))
            .andReturn();
        String followUpBody = followUpResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(followUpBody.contains("跟进内容"));
        assertTrue(followUpBody.contains("Observe 观察上下文"));
    }
}
