package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.settings.repository.SystemSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-settings;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class CpClawSettingsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsRepository settingsRepository;

    @Test
    void readingSettingsDoesNotCreateDefaultRow() throws Exception {
        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMetadata.searchEngineType").value("mysql"));

        assertFalse(settingsRepository.existsById("default"));

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
            .andExpect(jsonPath("$.data.userCloudPivot.hasPassword").value(true));

        assertTrue(settingsRepository.existsById("default"));
    }
}
