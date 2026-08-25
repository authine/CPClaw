package com.cpclaw.settings;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.credential.CredentialStatus;
import com.cpclaw.credential.CredentialUnavailableException;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.settings.dto.AdminMetadataSettings;
import com.cpclaw.settings.dto.ConnectionTestResponse;
import com.cpclaw.settings.dto.ModelConfigResponse;
import com.cpclaw.settings.dto.ModelConnectionTestResponse;
import com.cpclaw.settings.dto.SaveAdminSettingsRequest;
import com.cpclaw.settings.dto.SaveModelConfigRequest;
import com.cpclaw.settings.dto.SaveUserSettingsRequest;
import com.cpclaw.settings.dto.SettingsResponse;
import com.cpclaw.settings.dto.UserCloudPivotSettings;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String OWNER_MODEL = "model_config";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";
    private static final String ADMIN_CLOUDPIVOT_PASSWORD = "admin_cloudpivot_password";
    private static final String MODEL_API_KEY = "model_api_key";

    private final SystemSettingsRepository settingsRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector cloudPivotConnector;
    private final ModelGateway modelGateway;

    public SettingsService(
        SystemSettingsRepository settingsRepository,
        ModelConfigRepository modelConfigRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector,
        ModelGateway modelGateway
    ) {
        this.settingsRepository = settingsRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
        this.modelGateway = modelGateway;
    }

    public SettingsResponse getSettings() {
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID).orElseGet(this::newDefaultSettings);
        return new SettingsResponse(
            new UserCloudPivotSettings(
                hasText(settings.getAdminCloudPivotBaseUrl()),
                settings.getCloudPivotUsername(),
                credentialService.hasCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD),
                credentialStatus(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            ),
            new AdminMetadataSettings(
                settings.getAdminCloudPivotBaseUrl(),
                settings.getAdminCloudPivotUsername(),
                settings.getSearchEngineType(),
                settings.getSearchEngineUrl(),
                credentialService.hasCredential(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD),
                credentialStatus(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD)
            ),
            listModelSummaries()
        );
    }

    public List<ModelConfigResponse> listModelSummaries() {
        return modelConfigRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
            .map(this::toModelResponse)
            .toList();
    }

    public ModelConnectionTestResponse testModel(String modelConfigId) {
        if (!hasText(modelConfigId)) {
            return new ModelConnectionTestResponse(false, "未指定模型配置", 0);
        }
        Map<String, Object> result = modelGateway.testModel(modelConfigId);
        return new ModelConnectionTestResponse(
            Boolean.TRUE.equals(result.get("success")),
            String.valueOf(result.getOrDefault("message", "模型连接测试失败")),
            result.get("latencyMs") instanceof Number number ? number.longValue() : 0
        );
    }

    /**
     * Tests the configuration currently entered in the browser without creating a model
     * record or storing its API key. The request value is used only for this outbound call.
     */
    public ModelConnectionTestResponse testUnsavedModel(SaveModelConfigRequest request) {
        if (request == null || !hasText(request.modelName()) || !hasText(request.modelApiBaseUrl()) || !hasText(request.modelApiKey())) {
            return new ModelConnectionTestResponse(false, "请填写模型名称、API 地址和 API Key", 0);
        }
        Map<String, Object> result = modelGateway.testUnsavedModel(
            request.modelName(),
            request.modelApiBaseUrl(),
            request.modelApiKey()
        );
        return new ModelConnectionTestResponse(
            Boolean.TRUE.equals(result.get("success")),
            String.valueOf(result.getOrDefault("message", "模型连接测试失败")),
            result.get("latencyMs") instanceof Number number ? number.longValue() : 0
        );
    }

    @Transactional
    public ModelConfigResponse saveModel(SaveModelConfigRequest request) {
        if (!hasText(request.modelName()) || !hasText(request.modelApiBaseUrl()) || !hasText(request.modelApiKey())) {
            throw new IllegalArgumentException("请填写模型名称、API 地址和 API Key");
        }
        Instant now = Instant.now();
        ModelConfig modelConfig = new ModelConfig();
        modelConfig.setId(UUID.randomUUID().toString());
        modelConfig.setName(hasText(request.modelDisplayName()) ? request.modelDisplayName() : request.modelName());
        modelConfig.setApiBaseUrl(request.modelApiBaseUrl());
        modelConfig.setModelName(request.modelName());
        modelConfig.setSupportsThinking(request.supportsThinking());
        modelConfig.setDefaultThinkingEnabled(request.supportsThinking() && request.defaultThinkingEnabled());
        modelConfig.setEnabled(true);
        modelConfig.setCreatedAt(now);
        modelConfig.setUpdatedAt(now);
        credentialService.saveCredential(OWNER_MODEL, modelConfig.getId(), MODEL_API_KEY, request.modelApiKey())
            .ifPresent(modelConfig::setApiKeyCredentialId);
        modelConfigRepository.save(modelConfig);
        return toModelResponse(modelConfig);
    }

    @Transactional
    public ModelConfigResponse updateModel(String modelConfigId, SaveModelConfigRequest request) {
        if (!hasText(request.modelName()) || !hasText(request.modelApiBaseUrl()) || !hasText(request.modelApiKey())) {
            throw new IllegalArgumentException("请填写模型名称、API 地址和 API Key");
        }
        ModelConfig modelConfig = modelConfigRepository.findById(modelConfigId)
            .orElseThrow(() -> new IllegalArgumentException("模型配置不存在或已被删除"));
        modelConfig.setName(hasText(request.modelDisplayName()) ? request.modelDisplayName() : request.modelName());
        modelConfig.setApiBaseUrl(request.modelApiBaseUrl());
        modelConfig.setModelName(request.modelName());
        modelConfig.setSupportsThinking(request.supportsThinking());
        modelConfig.setDefaultThinkingEnabled(request.supportsThinking() && request.defaultThinkingEnabled());
        modelConfig.setUpdatedAt(Instant.now());
        credentialService.saveCredential(OWNER_MODEL, modelConfig.getId(), MODEL_API_KEY, request.modelApiKey())
            .ifPresent(modelConfig::setApiKeyCredentialId);
        return toModelResponse(modelConfigRepository.save(modelConfig));
    }

    @Transactional
    public void deleteModel(String modelConfigId) {
        if (!hasText(modelConfigId)) {
            throw new IllegalArgumentException("未指定要删除的模型配置");
        }
        ModelConfig modelConfig = modelConfigRepository.findById(modelConfigId)
            .orElseThrow(() -> new IllegalArgumentException("模型配置不存在或已被删除"));
        // Conversations and audit records remain historical references.
        credentialService.deleteCredential(OWNER_MODEL, modelConfig.getId(), MODEL_API_KEY);
        modelConfigRepository.delete(modelConfig);
    }

    @Transactional
    public SettingsResponse saveUserSettings(SaveUserSettingsRequest request) {
        Instant now = Instant.now();
        SystemSettings settings = getOrCreateSettings();
        settings.setCloudPivotUsername(request.cloudPivotUsername());
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
        credentialService.saveCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD, request.cloudPivotPassword());

        boolean hasModelName = hasText(request.modelName());
        boolean hasModelApiBaseUrl = hasText(request.modelApiBaseUrl());
        boolean hasModelApiKey = hasText(request.modelApiKey());
        if (hasModelName || hasModelApiBaseUrl || hasModelApiKey) {
            if (!hasModelName || !hasModelApiBaseUrl || !hasModelApiKey) {
                throw new IllegalArgumentException("模型配置必须同时填写模型名称、API 地址和 API Key");
            }
            ModelConfig modelConfig = new ModelConfig();
            modelConfig.setId(UUID.randomUUID().toString());
            modelConfig.setName(hasText(request.modelDisplayName()) ? request.modelDisplayName() : request.modelName());
            modelConfig.setApiBaseUrl(request.modelApiBaseUrl());
            modelConfig.setModelName(request.modelName());
            modelConfig.setSupportsThinking(request.supportsThinking());
            modelConfig.setDefaultThinkingEnabled(request.defaultThinkingEnabled());
            modelConfig.setEnabled(true);
            modelConfig.setCreatedAt(now);
            modelConfig.setUpdatedAt(now);
            modelConfigRepository.save(modelConfig);
            credentialService.saveCredential(OWNER_MODEL, modelConfig.getId(), MODEL_API_KEY, request.modelApiKey())
                .ifPresent(modelConfig::setApiKeyCredentialId);
            modelConfigRepository.save(modelConfig);
        }

        return getSettings();
    }

    @Transactional
    public SettingsResponse saveAdminSettings(SaveAdminSettingsRequest request) {
        Instant now = Instant.now();
        SystemSettings settings = getOrCreateSettings();
        settings.setAdminCloudPivotBaseUrl(request.targetBaseUrl());
        settings.setAdminCloudPivotUsername(request.username());
        settings.setSearchEngineType(hasText(request.searchEngineType()) ? request.searchEngineType() : "mysql");
        settings.setSearchEngineUrl(request.searchEndpoint());
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
        credentialService.saveCredential(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD, request.password());
        return getSettings();
    }

    public ConnectionTestResponse testUserCloudPivotConnection(SaveUserSettingsRequest request) {
        if (request == null || !hasText(request.cloudPivotUsername())) {
            return new ConnectionTestResponse(false, "请先由管理员配置云枢环境，然后填写个人云枢账号");
        }
        String environmentBaseUrl;
        try {
            environmentBaseUrl = configuredCloudPivotEnvironment();
        } catch (IllegalArgumentException exception) {
            return new ConnectionTestResponse(false, exception.getMessage());
        }
        return testCloudPivotConnection(
            environmentBaseUrl, request.cloudPivotUsername(), request.cloudPivotPassword(),
            USER_CLOUDPIVOT_PASSWORD, "个人云枢账号"
        );
    }

    public ConnectionTestResponse testAdminCloudPivotConnection(SaveAdminSettingsRequest request) {
        if (request == null || !hasText(request.targetBaseUrl()) || !hasText(request.username())) {
            return new ConnectionTestResponse(false, "请填写当前管理员云枢访问地址和账号");
        }
        return testCloudPivotConnection(
            request.targetBaseUrl(), request.username(), request.password(),
            ADMIN_CLOUDPIVOT_PASSWORD, "管理员云枢环境"
        );
    }

    private ConnectionTestResponse testCloudPivotConnection(
        String baseUrl, String username, String submittedPassword, String credentialType, String label
    ) {
        try {
            java.util.Optional<String> password = hasText(submittedPassword)
                ? java.util.Optional.of(submittedPassword)
                : credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, credentialType);
            return password
                .filter(value -> cloudPivotConnector.testConnection(baseUrl, username, value))
                .map(value -> new ConnectionTestResponse(true, label + "验证通过；可保存当前配置。"))
                .orElseGet(() -> new ConnectionTestResponse(false, label + "验证失败，请检查当前输入。"));
        } catch (CredentialUnavailableException exception) {
            return new ConnectionTestResponse(false, exception.getMessage());
        }
    }

    private String configuredCloudPivotEnvironment() {
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID).orElse(null);
        if (settings == null || !hasText(settings.getAdminCloudPivotBaseUrl())) {
            throw new IllegalArgumentException("管理员尚未配置云枢环境地址，请先在“管理员云枢环境”中配置并验证。");
        }
        return settings.getAdminCloudPivotBaseUrl().trim();
    }

    private SystemSettings getOrCreateSettings() {
        return settingsRepository.findById(SETTINGS_ID).orElseGet(() -> settingsRepository.save(newDefaultSettings()));
    }

    private SystemSettings newDefaultSettings() {
        Instant now = Instant.now();
        SystemSettings settings = new SystemSettings();
        settings.setId(SETTINGS_ID);
        settings.setSearchEngineType("mysql");
        settings.setCloudPivotUsername("huangj");
        settings.setCreatedAt(now);
        settings.setUpdatedAt(now);
        return settings;
    }

    private ModelConfigResponse toModelResponse(ModelConfig modelConfig) {
        return new ModelConfigResponse(
            modelConfig.getId(),
            modelConfig.getName(),
            modelConfig.getApiBaseUrl(),
            modelConfig.getModelName(),
            modelConfig.isSupportsThinking(),
            modelConfig.isDefaultThinkingEnabled(),
            modelConfig.isEnabled(),
            modelConfig.getApiKeyCredentialId() != null,
            credentialStatus(OWNER_MODEL, modelConfig.getId(), MODEL_API_KEY)
        );
    }

    private String credentialStatus(String ownerType, String ownerId, String credentialType) {
        return credentialService.credentialStatus(ownerType, ownerId, credentialType).name().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
