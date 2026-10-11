package com.cpclaw.skill.yunshu.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultYunshuConnectionResolverTests {
    @Test
    void businessWriteUsesCurrentUserCloudPivotCredentialNotAdminMetadataCredential() {
        SystemSettings settings = new SystemSettings();
        settings.setAdminCloudPivotBaseUrl("https://admin-yunshu"); settings.setCloudPivotUsername("user-a");
        SystemSettingsRepository repository = org.mockito.Mockito.mock(SystemSettingsRepository.class);
        CredentialService credentials = org.mockito.Mockito.mock(CredentialService.class);
        when(repository.findById("default")).thenReturn(Optional.of(settings));
        when(credentials.revealCredential("system", "default", "user_cloudpivot_password")).thenReturn(Optional.of("secret"));

        var resolved = new DefaultYunshuConnectionResolver(repository, credentials)
                .resolve(new YunshuHostContext("tenant-a", "user-a", "cpclaw-web", java.util.Map.of()));

        assertTrue(resolved.isPresent());
        BoundCloudPivotConnection connection = resolved.get();
        assertEquals("https://admin-yunshu", connection.baseUrl());
        assertEquals("user-a", connection.username());
    }

    @Test
    void externalHostCannotReuseCpclawWebResolver() {
        SystemSettingsRepository repository = org.mockito.Mockito.mock(SystemSettingsRepository.class);
        CredentialService credentials = org.mockito.Mockito.mock(CredentialService.class);
        var resolved = new DefaultYunshuConnectionResolver(repository, credentials)
                .resolve(new YunshuHostContext("tenant-a", "user-a", "workbuddy", java.util.Map.of()));
        assertTrue(resolved.isEmpty());
    }
}
