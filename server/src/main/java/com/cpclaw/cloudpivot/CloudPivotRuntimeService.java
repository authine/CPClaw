package com.cpclaw.cloudpivot;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.util.List;
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
    private final ModelGateway modelGateway;

    public CloudPivotRuntimeService(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector,
        ModelGateway modelGateway
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
        this.modelGateway = modelGateway;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
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
        return new CloudPivotQueryAnswer(match.name(), match.code(), result.total(), result.records().size(), summarize(match, result, userQuestion, modelConfigId, thinkingEnabled));
    }

    private String summarize(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
        if (isCountQuestion(userQuestion)) {
            return "已在云枢中查询到“" + match.name() + "”对应数据，总计 **" + result.total() + "** 条。";
        }
        if (isAnalysisQuestion(userQuestion)) {
            return modelGateway.analyzeRecords(modelConfigId, userQuestion, match.name(), result.total(), result.records(), thinkingEnabled)
                .map(answer -> "已查询“" + match.name() + "”数据，并基于返回结果完成分析：\n\n" + answer)
                .orElseGet(() -> fallbackAnalysis(match, result));
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

    private String fallbackAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。当前模型未配置或暂不可用，先基于查询结果给出规则分析：");
        if (result.records().isEmpty()) {
            answer.append("\n\n- 暂未返回可分析记录，建议确认普通用户账号权限、筛选条件和云枢数据是否存在。");
            return answer.toString();
        }
        answer.append("\n\n- 返回样本数：").append(result.records().size()).append(" 条。");
        answer.append("\n- 代表性记录：").append(recordSummary(result.records().getFirst())).append("。");
        opportunityAmountSummary(result.records()).ifPresent(summary -> answer.append("\n- 金额概览：").append(summary).append("。"));
        stageSummary(result.records()).ifPresent(summary -> answer.append("\n- 阶段分布：").append(summary).append("。"));
        answer.append("\n- 建议下一步：配置可用大模型后重新分析，系统会基于完整返回样本生成更深入的结论、风险和行动建议。");
        return answer.toString();
    }

    private java.util.Optional<String> opportunityAmountSummary(List<Map<String, Object>> records) {
        List<Double> amounts = records.stream()
            .map(this::recordData)
            .map(data -> data.get("amount"))
            .filter(value -> value instanceof Number)
            .map(value -> ((Number) value).doubleValue())
            .toList();
        if (amounts.isEmpty()) {
            return java.util.Optional.empty();
        }
        double total = amounts.stream().mapToDouble(Double::doubleValue).sum();
        double average = total / amounts.size();
        return java.util.Optional.of("样本金额合计 " + Math.round(total) + "，平均 " + Math.round(average));
    }

    private java.util.Optional<String> stageSummary(List<Map<String, Object>> records) {
        Map<String, Long> counts = records.stream()
            .map(this::recordData)
            .map(data -> data.get("stage"))
            .filter(value -> value != null && !String.valueOf(value).isBlank())
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        if (counts.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(counts.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + " 条")
            .reduce((left, right) -> left + "，" + right)
            .orElse(""));
    }

    private Map<?, ?> recordData(Map<String, Object> record) {
        Object data = record.get("data");
        return data instanceof Map<?, ?> dataMap ? dataMap : record;
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
            .sorted((left, right) -> Integer.compare(displayPriority(left.getKey()), displayPriority(right.getKey())))
            .limit(5)
            .map(entry -> String.valueOf(entry.getKey()) + "=" + valueText(entry.getValue()))
            .reduce((left, right) -> left + "，" + right)
            .orElse("无可展示字段");
    }

    private int displayPriority(Object key) {
        String value = String.valueOf(key);
        if ("instanceName".equalsIgnoreCase(value) || "name".equalsIgnoreCase(value) || "title".equalsIgnoreCase(value)) {
            return 0;
        }
        if ("id".equalsIgnoreCase(value) || "objectId".equalsIgnoreCase(value) || "dataId".equalsIgnoreCase(value)) {
            return 2;
        }
        return 1;
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
        return value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("几条") || value.contains("几个") || value.contains("几项") || value.contains("几笔") || value.contains("几份") || value.contains("几单") || value.contains("一共") || value.contains("总共") || value.contains("共有");
    }

    private boolean isAnalysisQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
