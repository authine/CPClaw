package com.cpclaw;

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
    "spring.jpa.hibernate.ddl-auto=create-drop"
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
            .andExpect(jsonPath("$.data.entityCount").value(5));

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

        MvcResult agentResult = mockMvc.perform(post("/api/conversations/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "conversationId":"%s",
                      "content":"查询元数据对象",
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

        mockMvc.perform(get("/api/audit/agent-runs/" + agentRunId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("completed"))
            .andExpect(jsonPath("$.data.tools.length()").value(1));
    }
}
