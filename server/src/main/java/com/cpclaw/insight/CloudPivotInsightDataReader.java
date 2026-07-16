package com.cpclaw.insight;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CloudPivotInsightDataReader {

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";
    private static final int PAGE_SIZE = 200;
    private static final int MAX_RECORDS = 20_000;

    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector connector;

    public CloudPivotInsightDataReader(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector connector
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.connector = connector;
    }

    public CloudPivotRuntimeQueryResult query(CloudPivotEntity entity) {
        return query(entity, false);
    }

    public CloudPivotRuntimeQueryResult query(CloudPivotEntity entity, boolean enrichDetails) {
        return query(entity, enrichDetails, enrichDetails ? 500 : MAX_RECORDS, List.of());
    }

    public CloudPivotRuntimeQueryResult query(
        CloudPivotEntity entity,
        boolean enrichDetails,
        int maxRecords,
        List<RuntimeQueryFilter> filters
    ) {
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先配置云枢普通用户账号"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先配置云枢普通用户密码"));
        int recordLimit = Math.max(1, Math.min(MAX_RECORDS, maxRecords));
        CloudPivotRuntimeQueryResult result = connector.queryRecords(
            settings.getCloudPivotBaseUrl(),
            settings.getCloudPivotUsername(),
            password,
            entity.getEntityCode(),
            PAGE_SIZE,
            enrichDetails,
            recordLimit,
            filters == null ? List.of() : filters
        );
        if ("local-fallback".equals(result.sourceEndpoint())) {
            throw new IllegalStateException("智能问数报告不能使用本地演示数据源");
        }
        return result;
    }

    public String configuredUsername() {
        return settingsRepository.findById(SETTINGS_ID)
            .map(SystemSettings::getCloudPivotUsername)
            .filter(value -> value != null && !value.isBlank())
            .orElse("");
    }
}
