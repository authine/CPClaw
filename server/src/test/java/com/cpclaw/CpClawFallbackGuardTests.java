package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    "spring.datasource.url=jdbc:h2:mem:cpclaw-fallback-guard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.persistence.runtime-guard-enabled=false",
    "cpclaw.cloudpivot.allow-metadata-fallback=true"
})
@AutoConfigureMockMvc
class CpClawFallbackGuardTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greetingWithoutModelRouterDoesNotUseAFrameworkWordList() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"HI",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
            .andExpect(jsonPath("$.data.candidates").isEmpty())
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("真实云枢元数据") || body.contains("业务目标"));
        assertFalse(body.contains("总计 **"));
    }

    @Test
    void designDiscussionWithoutModelRouterStaysInGovernedTaskPath() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"请给出合同管理系统详细设计大纲，列出业务对象、流程和关键控制点。",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("yunshu_task"))
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(body.contains("案件管理"));
    }

    @Test
    void greetingRouterFailureUsesNormalSkillResolution() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"hello",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("真实云枢元数据") || body.contains("业务目标"));
    }

    @Test
    void nonBusinessQuestionDoesNotUseFrameworkConversationKeywords() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"",
                      "content":"今天天气怎么样",
                      "thinkingEnabled":false,
                      "attachmentIds":[]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("真实云枢元数据") || body.contains("业务目标"));
    }

    @Test
    void fallbackMetadataMustNotAnswerBusinessQuestionsAsRealCloudPivotData() throws Exception {
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
            .andExpect(status().isOk());

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
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/metadata/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.entityCount").value(9));

        MvcResult result = mockMvc.perform(post("/api/conversations/messages")
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
            .andExpect(jsonPath("$.data.intent").value("clarify_intent"))
            .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("真实云枢元数据"));
        assertTrue(body.contains("不会用本地演示数据返回业务结果") || body.contains("已同步的真实云枢元数据"));
        assertFalse(body.contains("system_opportunity"));
        assertFalse(body.contains("华东制造业数字化项目"));
        assertFalse(body.contains("总计 **3** 条"));
    }
}
