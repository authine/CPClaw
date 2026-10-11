package com.cpclaw.skill.yunshu.form;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DefaultYunshuConnectionResolver implements YunshuConnectionResolver {
    private final SystemSettingsRepository settings;
    private final CredentialService credentials;
    public DefaultYunshuConnectionResolver(SystemSettingsRepository settings, CredentialService credentials) { this.settings = settings; this.credentials = credentials; }
    @Override public Optional<BoundCloudPivotConnection> resolve(YunshuHostContext context) {
        if (context == null || !"cpclaw-web".equals(context.hostId())) return Optional.empty();
        SystemSettings value = settings.findById("default").orElse(null);
        if (value == null || value.getAdminCloudPivotBaseUrl() == null || value.getAdminCloudPivotBaseUrl().isBlank() || value.getCloudPivotUsername() == null || value.getCloudPivotUsername().isBlank()) return Optional.empty();
        return credentials.revealCredential("system", "default", "user_cloudpivot_password")
                .filter(password -> !password.isBlank())
                .map(password -> new BoundCloudPivotConnection("cpclaw-web", value.getAdminCloudPivotBaseUrl(), value.getCloudPivotUsername(), password));
    }
}
