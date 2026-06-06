package com.cpclaw.settings;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.settings.dto.AdminMetadataSettings;
import com.cpclaw.settings.dto.ConnectionTestResponse;
import com.cpclaw.settings.dto.ModelConfigResponse;
import com.cpclaw.settings.dto.SaveAdminSettingsRequest;
import com.cpclaw.settings.dto.SaveUserSettingsRequest;
import com.cpclaw.settings.dto.SettingsResponse;
import com.cpclaw.settings.dto.UserCloudPivotSettings;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.time.Instant;
import java.util.List;
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

    public SettingsService(
        SystemSettingsRepository settingsRepository,
        ModelConfigRepository modelConfigRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector
    ) {
        this.settingsRepository = settingsRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
    }

    public SettingsResponse getSettings() {
        SystemSettings settings = getOrCreateSettings();
        return new SettingsResponse(
            new UserCloudPivotSettings(
                settings.getCloudPivotBaseUrl(),
                settings.getCloudPivotUsername(),
                credentialService.hasCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            ),
            new AdminMetadataSettings(
                settings.getAdminCloudPivotBaseUrl(),
                settings.getAdminCloudPivotUsername(),
                settings.getSearchEngineType(),
                settings.getSearchEngineUrl(),
                credentialService.hasCredential(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD)
            ),
            listModelSummaries()
        );
    }

    public List<ModelConfigResponse> listModelSummaries() {
        return modelConfigRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream()
            .map(this::toModelResponse)
            .toList();
    }

    @Transactional
    public SettingsResponse saveUserSettings(SaveUserSettingsRequest request) {
        Instant now = Instant.now();
        SystemSettings settings = getOrCreateSettings();
        settings.setCloudPivotBaseUrl(request.cloudPivotBaseUrl());
        settings.setCloudPivotUsername(request.cloudPivotUsername());
        settings.setUpdatedAt(now);
        settingsRepository.save(settings);
        credentialService.saveCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD, request.cloudPivotPassword());

        if (hasText(request.modelName()) && hasText(request.modelApiBaseUrl())) {
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

    public ConnectionTestResponse testUserCloudPivotConnection() {
        SystemSettings settings = getOrCreateSettings();
        if (!hasText(settings.getCloudPivotBaseUrl()) || !hasText(settings.getCloudPivotUsername())) {
            return new ConnectionTestResponse(false, "请先填写普通用户云枢访问地址和账号");
        }
        return credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .filter(password -> cloudPivotConnector.testConnection(settings.getCloudPivotBaseUrl(), settings.getCloudPivotUsername(), password))
            .map(password -> new ConnectionTestResponse(true, "普通用户云枢账号验证通过"))
            .orElseGet(() -> new ConnectionTestResponse(false, "普通用户云枢账号验证失败"));
    }

    public ConnectionTestResponse testAdminCloudPivotConnection() {
        SystemSettings settings = getOrCreateSettings();
        if (!hasText(settings.getAdminCloudPivotBaseUrl()) || !hasText(settings.getAdminCloudPivotUsername())) {
            return new ConnectionTestResponse(false, "请先填写管理员云枢环境和账号");
        }
        return credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD)
            .filter(password -> cloudPivotConnector.testConnection(settings.getAdminCloudPivotBaseUrl(), settings.getAdminCloudPivotUsername(), password))
            .map(password -> new ConnectionTestResponse(true, "管理员云枢账号验证通过，可执行云枢元数据初始化"))
            .orElseGet(() -> new ConnectionTestResponse(false, "管理员云枢账号验证失败"));
    }

    private SystemSettings getOrCreateSettings() {
        return settingsRepository.findById(SETTINGS_ID).orElseGet(() -> {
            Instant now = Instant.now();
            SystemSettings settings = new SystemSettings();
            settings.setId(SETTINGS_ID);
            settings.setSearchEngineType("mysql");
            settings.setCreatedAt(now);
            settings.setUpdatedAt(now);
            return settingsRepository.save(settings);
        });
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
            modelConfig.getApiKeyCredentialId() != null
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
