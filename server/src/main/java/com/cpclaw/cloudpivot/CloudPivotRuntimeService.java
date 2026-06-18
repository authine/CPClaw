package com.cpclaw.cloudpivot;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CloudPivotRuntimeService {

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";

    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector cloudPivotConnector;

    public CloudPivotRuntimeService(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion) {
        if (match == null || !"entity".equals(match.objectType()) || !hasText(match.code())) {
            throw new IllegalArgumentException("没有从本地元数据中匹配到可查询的云枢模型，请补充应用或表单名称后重试");
        }

        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中填写云枢登录密码"));

        CloudPivotRuntimeQueryResult result = cloudPivotConnector.queryRecords(
            settings.getCloudPivotBaseUrl(),
            settings.getCloudPivotUsername(),
            password,
            match.code(),
            isCountQuestion(userQuestion) ? 1 : 10
        );
        return new CloudPivotQueryAnswer(match.name(), match.code(), result.total(), result.records().size(), summarize(match, result, userQuestion));
    }

    private String summarize(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion) {
        if (isCountQuestion(userQuestion)) {
            return "已在云枢中查询到“" + match.name() + "”对应数据，总计 **" + result.total() + "** 条。";
        }
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (!result.records().isEmpty()) {
            answer.append("\n\n前 ").append(result.records().size()).append(" 条记录摘要：");
            for (int i = 0; i < result.records().size(); i++) {
                answer.append("\n").append(i + 1).append(". ").append(recordSummary(result.records().get(i)));
            }
        }
        return answer.toString();
    }

    private String recordSummary(Map<String, Object> record) {
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return pickReadableValue(dataMap);
        }
        return pickReadableValue(record);
    }

    private String pickReadableValue(Map<?, ?> values) {
        return values.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> !String.valueOf(entry.getKey()).toLowerCase().contains("password"))
            .limit(5)
            .map(entry -> String.valueOf(entry.getKey()) + "=" + valueText(entry.getValue()))
            .reduce((left, right) -> left + "，" + right)
            .orElse("无可展示字段");
    }

    private String valueText(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("name")) {
            return String.valueOf(map.get("name"));
        }
        String text = String.valueOf(value);
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    private boolean isCountQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("几条");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
