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
            queryPageSize(userQuestion)
        );
        RuntimeAnswerDetail detail = summarize(match, result, userQuestion, modelConfigId, thinkingEnabled);
        return new CloudPivotQueryAnswer(
            match.name(),
            match.code(),
            result.total(),
            result.records().size(),
            detail.answer(),
            result.sourceEndpoint(),
            detail.actionSummary(),
            rawDataSummary(result),
            detail.conclusionSummary()
        );
    }

    private RuntimeAnswerDetail summarize(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
        if (isYearlyDistributionQuestion(userQuestion)) {
            return yearlyDistributionAnalysis(match, result);
        }
        if (isCountQuestion(userQuestion)) {
            String conclusion = "统计“" + match.name() + "”总数为 " + result.total() + " 条";
            String answer = withTrace(match, result, "计数查询：查询运行态总数", conclusion, "已在云枢中查询到“" + match.name() + "”对应数据，总计 **" + result.total() + "** 条。");
            return new RuntimeAnswerDetail(answer, "计数查询：查询运行态总数", conclusion);
        }
        if (isAnalysisQuestion(userQuestion)) {
            String conclusion = "基于“" + match.name() + "”返回数据生成业务分析";
            String answer = modelGateway.analyzeRecords(modelConfigId, userQuestion, match.name(), result.total(), result.records(), thinkingEnabled)
                .map(modelAnswer -> "已查询“" + match.name() + "”数据，并基于返回结果完成分析：\n\n" + modelAnswer)
                .orElseGet(() -> fallbackAnalysis(match, result));
            return new RuntimeAnswerDetail(withTrace(match, result, "分析查询：查询运行态数据并生成分析", conclusion, answer), "分析查询：查询运行态数据并生成分析", conclusion);
        }
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (!result.records().isEmpty()) {
            answer.append("\n\n前 ").append(result.records().size()).append(" 条记录摘要：");
            for (int i = 0; i < result.records().size(); i++) {
                answer.append("\n").append(i + 1).append(". ").append(recordSummary(result.records().get(i)));
            }
        }
        String conclusion = "查询“" + match.name() + "”并返回前 " + result.records().size() + " 条摘要";
        return new RuntimeAnswerDetail(withTrace(match, result, "列表查询：查询运行态记录摘要", conclusion, answer.toString()), "列表查询：查询运行态记录摘要", conclusion);
    }

    private RuntimeAnswerDetail yearlyDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        Map<String, Long> yearlyCounts = result.records().stream()
            .map(this::recordData)
            .map(this::recordYear)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.TreeMap::new, java.util.stream.Collectors.counting()));

        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        String objectLabel = objectLabel(match);
        String metricLabel = objectLabel + "量";
        String unitLabel = unitLabel(objectLabel);
        answer.append("### 按年").append(metricLabel).append("分析\n");
        if (yearlyCounts.isEmpty()) {
            answer.append("- 当前返回记录中没有可识别的年份字段，建议确认").append(objectLabel).append("表是否存在创建时间、登记时间或年份字段。\n");
            if (!result.records().isEmpty()) {
                answer.append("- 返回样本：").append(recordSummary(result.records().getFirst())).append("。\n");
            }
            String conclusion = "未能从返回记录识别年份字段，不能生成年度分布";
            return new RuntimeAnswerDetail(withTrace(match, result, "按年分析：查询运行态数据并按年份聚合", conclusion, answer.toString()), "按年分析：查询运行态数据并按年份聚合", conclusion);
        }
        yearlyCounts.forEach((year, count) -> answer.append("- ").append(year).append(" 年：").append(count).append(" ").append(unitLabel).append("\n"));
        answer.append("\n### 判断\n");
        answer.append("- 年份覆盖：").append(String.join("、", yearlyCounts.keySet())).append("。\n");
        answer.append("- 峰值年份：").append(peakYear(yearlyCounts, unitLabel)).append("。\n");
        trendSummary(yearlyCounts, metricLabel).ifPresent(summary -> answer.append("- 趋势判断：").append(summary).append("。\n"));
        if (result.records().size() < result.total()) {
            answer.append("- 当前运行态接口本次返回 ").append(result.records().size()).append(" 条样本，少于总数 ").append(result.total()).append(" 条；生产环境需要分页拉取全量后再做最终年度分布。\n");
        }
        answer.append("\n### 下一步建议\n");
        answer.append("- 可以继续按负责人、行业、等级或来源渠道下钻，判断").append(metricLabel).append("变化来自哪个团队或客群。\n");
        String conclusion = "按年份聚合“" + match.name() + "”，年度分布为 " + yearlyDistributionSummary(yearlyCounts, unitLabel);
        return new RuntimeAnswerDetail(withTrace(match, result, "按年分析：查询运行态数据并按年份聚合", conclusion, answer.toString()), "按年分析：查询运行态数据并按年份聚合", conclusion);
    }

    private String withTrace(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String actionSummary, String conclusionSummary, String answer) {
        return "### 执行过程\n"
            + "- 意图理解：识别为数据读取任务，动作是“" + actionSummary + "”。\n"
            + "- 对象匹配：命中" + schemaSourceLabel(result) + "“" + match.name() + "”，" + schemaCodeLabel(result) + "=`" + match.code() + "`。\n"
            + "- 数据动作：调用云枢运行态查询，来源=`" + result.sourceEndpoint() + "`，总数=" + result.total() + "，本次返回=" + result.records().size() + "。\n"
            + fallbackNotice(result)
            + "- 原始数据摘要：" + rawDataSummary(result) + "。\n"
            + "- 结论生成：" + conclusionSummary + "。\n\n"
            + answer;
    }

    private String schemaSourceLabel(CloudPivotRuntimeQueryResult result) {
        return isFallbackResult(result) ? "本地演示对象" : "云枢对象";
    }

    private String schemaCodeLabel(CloudPivotRuntimeQueryResult result) {
        return isFallbackResult(result) ? "演示编码" : "schemaCode";
    }

    private String fallbackNotice(CloudPivotRuntimeQueryResult result) {
        if (!isFallbackResult(result)) {
            return "";
        }
        return "- 编码说明：当前来源为 `local-fallback`，上面的编码仅用于本地演示，不是真实云枢 schemaCode；连接真实云枢并同步元数据后才会显示真实对象编码。\n";
    }

    private boolean isFallbackResult(CloudPivotRuntimeQueryResult result) {
        return result != null && "local-fallback".equals(result.sourceEndpoint());
    }

    private String rawDataSummary(CloudPivotRuntimeQueryResult result) {
        if (result.records().isEmpty()) {
            return "未返回记录明细";
        }
        return result.records().stream()
            .limit(3)
            .map(this::recordSummary)
            .reduce((left, right) -> left + "；" + right)
            .orElse("未返回记录明细");
    }

    private String yearlyDistributionSummary(Map<String, Long> yearlyCounts, String unitLabel) {
        return yearlyCounts.entrySet().stream()
            .map(entry -> entry.getKey() + " 年 " + entry.getValue() + " " + unitLabel)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无年度分布");
    }

    private String objectLabel(MetadataSearchResult match) {
        String value = match == null || match.name() == null ? "记录" : match.name().trim();
        if (value.startsWith("系统") && value.length() > 2) {
            value = value.substring(2);
        }
        return value.isBlank() ? "记录" : value;
    }

    private String unitLabel(String objectLabel) {
        if (objectLabel.contains("客户")) {
            return "个客户";
        }
        if (objectLabel.contains("商机")) {
            return "条商机";
        }
        return "条";
    }

    private String peakYear(Map<String, Long> yearlyCounts, String unitLabel) {
        return yearlyCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> entry.getKey() + " 年（" + entry.getValue() + " " + unitLabel + "）")
            .orElse("暂无可判断年份");
    }

    private java.util.Optional<String> trendSummary(Map<String, Long> yearlyCounts, String metricLabel) {
        if (yearlyCounts.size() < 2) {
            return java.util.Optional.empty();
        }
        List<Long> values = yearlyCounts.values().stream().toList();
        long first = values.getFirst();
        long last = values.getLast();
        if (last > first) {
            return java.util.Optional.of("最近年份" + metricLabel + "高于最早年份，整体呈增长信号");
        }
        if (last < first) {
            return java.util.Optional.of("最近年份" + metricLabel + "低于最早年份，需要关注增长放缓风险");
        }
        return java.util.Optional.of("首末年份" + metricLabel + "持平，需要结合中间年份波动继续观察");
    }

    private java.util.Optional<String> recordYear(Map<?, ?> data) {
        for (String key : List.of("createdAt", "createTime", "createdTime", "createdDate", "registerDate", "signupDate", "year")) {
            Object value = data.get(key);
            java.util.Optional<String> year = extractYear(value);
            if (year.isPresent()) {
                return year;
            }
        }
        return data.values().stream()
            .map(this::extractYear)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private java.util.Optional<String> extractYear(Object value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String text = String.valueOf(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2}|19\\d{2})").matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
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
        return value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看") || value.contains("怎么样") || value.contains("按年") || value.contains("每年") || value.contains("年度");
    }

    private boolean isYearlyDistributionQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("每年") || value.contains("按年") || value.contains("年度") || value.contains("年份") || (value.contains("年") && (value.contains("数量") || value.contains("量") || value.contains("情况") || value.contains("趋势")));
    }

    private int queryPageSize(String content) {
        if (isYearlyDistributionQuestion(content)) {
            return 50;
        }
        return isCountQuestion(content) ? 1 : 10;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuntimeAnswerDetail(String answer, String actionSummary, String conclusionSummary) {
    }
}
