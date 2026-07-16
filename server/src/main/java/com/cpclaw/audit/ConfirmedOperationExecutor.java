package com.cpclaw.audit;

import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotOperationResult;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ConfirmedOperationExecutor {

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";

    private final CloudPivotConnector cloudPivotConnector;
    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;

    public ConfirmedOperationExecutor(
        CloudPivotConnector cloudPivotConnector,
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        ObjectMapper objectMapper
    ) {
        this.cloudPivotConnector = cloudPivotConnector;
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
    }

    public ConfirmedOperationResult execute(Confirmation confirmation) {
        Map<String, Object> plan = readPlan(confirmation.getChangesJsonMasked());
        String operation = stringValue(plan.get("operation"));
        if (!"delete_data".equals(operation) && !"delete".equals(operation)) {
            return new ConfirmedOperationResult(false, "unsupported", "当前确认单没有可自动执行的云枢写操作", Map.of("operation", operation));
        }
        String appCode = stringValue(plan.get("appCode"));
        String schemaCode = stringValue(plan.get("schemaCode"));
        String bizObjectId = stringValue(plan.get("bizObjectId"));
        if (appCode.isBlank() || schemaCode.isBlank() || bizObjectId.isBlank()) {
            throw new IllegalArgumentException("确认单缺少删除目标，无法执行。需要 appCode、schemaCode 和 bizObjectId。");
        }

        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中填写云枢登录密码"));
        CloudPivotOperationResult result = cloudPivotConnector.deleteRecord(
            settings.getCloudPivotBaseUrl(),
            settings.getCloudPivotUsername(),
            password,
            appCode,
            schemaCode,
            bizObjectId
        );
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", result.operation());
        output.put("appCode", result.appCode());
        output.put("schemaCode", result.schemaCode());
        output.put("bizObjectId", result.bizObjectId());
        output.put("endpoint", result.endpoint());
        output.put("message", result.message());
        output.put("response", result.responseSummary());
        return new ConfirmedOperationResult(result.success(), result.operation(), result.message(), output);
    }

    private Map<String, Object> readPlan(String changesJson) {
        if (changesJson == null || changesJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(changesJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("确认单执行计划无法解析", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ConfirmedOperationResult(boolean executed, String operation, String message, Map<String, Object> output) {
    }
}
