package com.cpclaw.conversation;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.skill.SkillExecutionContext;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Supplies the authenticated Web user's Yunshu connection to the shared Skill runtime. */
@Service
public class WebYunshuExecutionContextFactory {
    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_PASSWORD = "user_cloudpivot_password";
    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;

    public WebYunshuExecutionContextFactory(SystemSettingsRepository settingsRepository, CredentialService credentialService) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
    }

    public SkillExecutionContext create() {
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID).orElse(null);
        if (settings == null || blank(settings.getAdminCloudPivotBaseUrl()) || blank(settings.getCloudPivotUsername())) {
            return SkillExecutionContext.empty();
        }
        return credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_PASSWORD)
            .filter(value -> !value.isBlank())
            .map(password -> new BoundCloudPivotConnection("cpclaw-web", settings.getAdminCloudPivotBaseUrl().trim(), settings.getCloudPivotUsername().trim(), password))
            .map(connection -> new SkillExecutionContext(Map.of("cloudPivotConnection", connection)))
            .orElseGet(SkillExecutionContext::empty);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
