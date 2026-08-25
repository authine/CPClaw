package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.credential.repository.EncryptedCredentialRepository;
import com.cpclaw.memory.MemoryService;
import com.cpclaw.memory.entity.AgentMemory;
import com.cpclaw.memory.repository.AgentMemoryRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.nio.charset.StandardCharsets;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cpclaw-settings;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "cpclaw.persistence.runtime-guard-enabled=false"
})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CpClawSettingsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemSettingsRepository settingsRepository;

    @Autowired
    private ModelConfigRepository modelConfigRepository;

    @Autowired
    private EncryptedCredentialRepository credentialRepository;

    @Autowired
    private AgentMemoryRepository agentMemoryRepository;

    @Autowired
    private MemoryService memoryService;

    @Test
    @Order(1)
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
        assertTrue(modelConfigRepository.count() == 1);
        assertTrue(credentialRepository.count() == 2);

        String encryptedUserPassword = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(
                "system", "default", "user_cloudpivot_password")
            .orElseThrow()
            .getEncryptedValue();

        // The UI sends an empty value when the existing password mask is left unchanged.
        // That must preserve the existing encrypted credential rather than deleting or replacing it.
        mockMvc.perform(post("/api/settings/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cloudPivotBaseUrl":"https://cloudpivot.example.local",
                      "cloudPivotUsername":"demo-user",
                      "cloudPivotPassword":"",
                      "modelName":"",
                      "modelApiBaseUrl":"",
                      "modelApiKey":"",
                      "modelDisplayName":"",
                      "supportsThinking":false,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userCloudPivot.hasPassword").value(true));

        String preservedEncryptedUserPassword = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(
                "system", "default", "user_cloudpivot_password")
            .orElseThrow()
            .getEncryptedValue();
        assertEquals(encryptedUserPassword, preservedEncryptedUserPassword);

        mockMvc.perform(post("/api/settings/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetBaseUrl":"https://cloudpivot-admin.example.local",
                      "username":"admin-user",
                      "password":"admin-secret",
                      "searchEngineType":"mysql",
                      "searchEndpoint":""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMetadata.hasPassword").value(true));
        String encryptedAdminPassword = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(
                "system", "default", "admin_cloudpivot_password")
            .orElseThrow()
            .getEncryptedValue();

        mockMvc.perform(post("/api/settings/admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetBaseUrl":"https://cloudpivot-admin.example.local",
                      "username":"admin-user",
                      "password":"",
                      "searchEngineType":"mysql",
                      "searchEndpoint":""
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.adminMetadata.hasPassword").value(true));
        String preservedEncryptedAdminPassword = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType(
                "system", "default", "admin_cloudpivot_password")
            .orElseThrow()
            .getEncryptedValue();
        assertEquals(encryptedAdminPassword, preservedEncryptedAdminPassword);

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userCloudPivot.environmentConfigured").value(true))
            .andExpect(jsonPath("$.data.userCloudPivot.username").value("demo-user"))
            .andExpect(jsonPath("$.data.userCloudPivot.hasPassword").value(true))
            .andExpect(jsonPath("$.data.models[0].hasApiKey").value(true));
    }

    @Test
    @Order(2)
    void cloudPivotConnectionTestValidatesCurrentInputAndDoesNotPersistIt() throws Exception {
        mockMvc.perform(post("/api/settings/cloudpivot/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value("请先由管理员配置云枢环境，然后填写个人云枢账号"));

        mockMvc.perform(post("/api/settings/cloudpivot/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cloudPivotBaseUrl":"https://current-input.example.local",
                      "cloudPivotUsername":"current-user",
                      "cloudPivotPassword":"current-password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value("个人云枢账号验证失败，请检查当前输入。"));

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userCloudPivot.environmentConfigured").value(true))
            .andExpect(jsonPath("$.data.userCloudPivot.username").value("demo-user"));
    }

    @Test
    @Order(3)
    void testingUnsavedModelDoesNotPersistModelOrCredential() throws Exception {
        long modelCountBefore = modelConfigRepository.count();
        long credentialCountBefore = credentialRepository.count();

        mockMvc.perform(post("/api/settings/models/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelName":"pre-save-test-model",
                      "modelApiBaseUrl":"http://127.0.0.1:11434/v1",
                      "modelApiKey":"transient-test-key",
                      "modelDisplayName":"预检模型",
                      "supportsThinking":false,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value("本地模拟地址不执行真实模型验证，请配置可访问的模型服务地址"));

        assertEquals(modelCountBefore, modelConfigRepository.count());
        assertEquals(credentialCountBefore, credentialRepository.count());
    }

    @Test
    @Order(4)
    void unreadablePersistedCredentialsReturnRecoveryGuidanceAndCanBeReplaced() throws Exception {
        mockMvc.perform(post("/api/settings/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelName":"credential-recovery-model",
                      "modelApiBaseUrl":"https://model.example.local",
                      "modelApiKey":"old-test-value",
                      "modelDisplayName":"凭据恢复测试模型",
                      "supportsThinking":false,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk());

        String modelId = modelConfigRepository.findAll().stream()
            .filter(model -> "credential-recovery-model".equals(model.getModelName()))
            .findFirst()
            .orElseThrow()
            .getId();
        var credential = credentialRepository
            .findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType("model_config", modelId, "model_api_key")
            .orElseThrow();
        credential.setAuthTag("AAAAAAAAAAAAAAAAAAAAAA==");
        credentialRepository.save(credential);

        mockMvc.perform(get("/api/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.models[?(@.id == '" + modelId + "')].credentialStatus").value("unreadable"));

        mockMvc.perform(post("/api/settings/models/" + modelId + "/test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value("已保存的模型 API Key 无法使用。请在系统设置中重新录入并验证后保存。"));

        mockMvc.perform(put("/api/settings/models/" + modelId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelName":"credential-recovery-model",
                      "modelApiBaseUrl":"https://model.example.local",
                      "modelApiKey":"replacement-test-value",
                      "modelDisplayName":"凭据恢复测试模型",
                      "supportsThinking":false,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.credentialStatus").value("available"));
    }

    @Test
    @Order(5)
    void deletingModelRemovesItsCredentialButKeepsHistoricalReferences() throws Exception {
        mockMvc.perform(post("/api/settings/models")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "modelName":"delete-model-test",
                      "modelApiBaseUrl":"https://model.example.local",
                      "modelApiKey":"delete-test-key",
                      "modelDisplayName":"待删除模型",
                      "supportsThinking":false,
                      "defaultThinkingEnabled":false
                    }
                    """))
            .andExpect(status().isOk());
        String modelId = modelConfigRepository.findAll().stream()
            .filter(model -> "delete-model-test".equals(model.getModelName()))
            .findFirst()
            .orElseThrow()
            .getId();
        assertTrue(credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType("model_config", modelId, "model_api_key").isPresent());

        mockMvc.perform(delete("/api/settings/models/" + modelId))
            .andExpect(status().isOk());

        assertFalse(modelConfigRepository.existsById(modelId));
        assertFalse(credentialRepository.findFirstByCredentialOwnerTypeAndCredentialOwnerIdAndCredentialType("model_config", modelId, "model_api_key").isPresent());
        mockMvc.perform(get("/api/settings/models"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.id == '" + modelId + "')]").isEmpty());
    }

    @Test
    @Order(6)
    void sessionMemoryStaysRuntimeOnlyAndIsNotReturnedBySettings() throws Exception {
        Instant now = Instant.now();
        AgentMemory session = new AgentMemory();
        session.setId("session-memory-settings-test");
        session.setConversationId("settings-session-conversation");
        session.setMemoryScope("SESSION");
        session.setOwnerPrincipal("huangj");
        session.setTenantId("default");
        session.setPriority(0);
        session.setMemoryType("runtime_mapping");
        session.setContentJson("{\"text\":\"内部会话记忆，不应出现在设置页\"}");
        session.setConfidence(1.0D);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plusSeconds(3600));
        agentMemoryRepository.save(session);

        MvcResult settingsResult = mockMvc.perform(get("/api/settings/memory"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.personal").isArray())
            .andExpect(jsonPath("$.data.global").isArray())
            .andExpect(jsonPath("$.data.personal[?(@.scope == 'SESSION')]").isEmpty())
            .andExpect(jsonPath("$.data.global[?(@.scope == 'SESSION')]").isEmpty())
            .andReturn();
        assertFalse(settingsResult.getResponse().getContentAsString(StandardCharsets.UTF_8).contains("内部会话记忆"));

        assertTrue(memoryService.recall("settings-session-conversation").stream()
            .anyMatch(item -> "runtime_mapping".equals(item.get("memoryType"))));
    }
}
