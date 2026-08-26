package com.cpclaw.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-conversation-lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.persistence.runtime-guard-enabled=false"
})
@AutoConfigureMockMvc
class ConversationLifecycleApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationLifecycleService lifecycleService;

    @MockBean
    private CloudPivotConnector cloudPivotConnector;

    @Test
    void draftIsHiddenUntilOutputStartsAndReadEndpointIsIdempotent() throws Exception {
        String response = mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"生命周期 API 测试\",\"thinkingEnabled\":false}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String conversationId = objectMapper.readTree(response).path("data").path("id").asText();

        String draftList = mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertFalse(draftList.contains(conversationId));

        lifecycleService.markOutputStarted(conversationId, "生命周期 API 测试");
        String runningList = mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertTrue(runningList.contains(conversationId));
        assertTrue(runningList.contains("\"lifecycleStatus\":\"RUNNING\""));

        lifecycleService.markCompleted(conversationId);
        String unreadList = mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertTrue(unreadList.contains("\"unread\":true"));

        mockMvc.perform(put("/api/conversations/" + conversationId + "/read"))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/conversations/" + conversationId + "/read"))
            .andExpect(status().isOk());
        String readList = mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode conversations = objectMapper.readTree(readList).path("data");
        JsonNode summary = null;
        for (JsonNode candidate : conversations) {
            if (conversationId.equals(candidate.path("id").asText())) {
                summary = candidate;
                break;
            }
        }
        assertTrue(summary != null && !summary.path("unread").asBoolean());
    }
}
