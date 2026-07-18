package com.cpclaw.cloudpivot;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy.DisplayContext;
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
    private static final List<String> CHINA_PROVINCE_NAMES = List.of(
        "北京市", "天津市", "上海市", "重庆市", "河北省", "山西省", "辽宁省", "吉林省", "黑龙江省", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省", "湖北省", "湖南省", "广东省", "海南省", "四川省", "贵州省", "云南省", "陕西省", "甘肃省", "青海省", "台湾省", "内蒙古自治区", "广西壮族自治区", "西藏自治区", "宁夏回族自治区", "新疆维吾尔自治区", "香港特别行政区", "澳门特别行政区"
    );

    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector cloudPivotConnector;
    private final ModelGateway modelGateway;
    private final CloudPivotRuntimeProperties runtimeProperties;
    private final CloudPivotRecordDisplayPolicy recordDisplayPolicy;

    public CloudPivotRuntimeService(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector,
        ModelGateway modelGateway,
        CloudPivotRuntimeProperties runtimeProperties,
        CloudPivotRecordDisplayPolicy recordDisplayPolicy
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
        this.modelGateway = modelGateway;
        this.runtimeProperties = runtimeProperties;
        this.recordDisplayPolicy = recordDisplayPolicy;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
        if (match == null || !"entity".equals(match.objectType()) || !hasText(match.code())) {
            throw new IllegalArgumentException("没有从本地元数据中匹配到可查询的云枢模型，请补充应用或表单名称后重试");
        }

        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中填写云枢登录密码"));

        int pageSize = queryPageSize(userQuestion);
        boolean fullDimensionDetails = requiresFullDimensionDetails(userQuestion);
        int recordLimit = queryRecordLimit(userQuestion);
        CloudPivotRuntimeQueryResult result = cloudPivotConnector.queryRecords(
            settings.getCloudPivotBaseUrl(),
            settings.getCloudPivotUsername(),
            password,
            match.code(),
            pageSize,
            fullDimensionDetails,
            recordLimit
        );
        if (isStageDistributionQuestion(userQuestion) && !hasRecognizableStage(result.records())) {
            try {
                CloudPivotRuntimeQueryResult stageSample = cloudPivotConnector.queryRecords(
                    settings.getCloudPivotBaseUrl(),
                    settings.getCloudPivotUsername(),
                    password,
                    match.code(),
                    2,
                    true,
                    2
                );
                if (hasRecognizableStage(stageSample.records())) {
                    result = stageSample;
                }
            } catch (RuntimeException ignored) {
                // Keep the fast list result if optional detail sampling fails.
            }
        }
        if (isNewOldCustomerQuestion(userQuestion)
            && result.records().size() < result.total()
            && hasRecognizableCustomerLifecycle(result.records())) {
            try {
                result = cloudPivotConnector.queryRecords(
                    settings.getCloudPivotBaseUrl(),
                    settings.getCloudPivotUsername(),
                    password,
                    match.code(),
                    200,
                    false,
                    20_000
                );
            } catch (RuntimeException ignored) {
                // Keep the fast sample result if the optional full aggregation request fails.
            }
        }
        if (isFallbackResult(result)) {
            throw new IllegalStateException("当前连接返回的是本地演示数据源 local-fallback，不能用于回答真实云枢业务数据。请配置真实云枢地址、账号并重新同步元数据。");
        }
        DisplayContext displayContext = recordDisplayPolicy.context(match.code());
        RuntimeAnswerDetail detail = summarize(match, result, userQuestion, modelConfigId, thinkingEnabled, displayContext);
        return new CloudPivotQueryAnswer(
            match.name(),
            match.code(),
            result.total(),
            result.records().size(),
            detail.answer(),
            result.sourceEndpoint(),
            detail.actionSummary(),
            rawDataSummary(result, displayContext),
            detail.conclusionSummary()
        );
    }

    private RuntimeAnswerDetail summarize(
        MetadataSearchResult match,
        CloudPivotRuntimeQueryResult result,
        String userQuestion,
        String modelConfigId,
        boolean thinkingEnabled,
        DisplayContext displayContext
    ) {
        if (isStageDistributionQuestion(userQuestion)) {
            return stageDistributionAnalysis(match, result, displayContext);
        }
        if (isYearlyDistributionQuestion(userQuestion)) {
            return yearlyDistributionAnalysis(match, result, displayContext);
        }
        if (isProvinceDistributionQuestion(userQuestion)) {
            return provinceDistributionAnalysis(match, result, displayContext);
        }
        if (isNewOldCustomerQuestion(userQuestion)) {
            return newOldCustomerAnalysis(match, result, displayContext);
        }
        if (isCountQuestion(userQuestion)) {
            String conclusion = "统计“" + match.name() + "”总数为 " + result.total() + " 条";
            String answer = "已在云枢中查询到“" + match.name() + "”对应数据，总计 **" + result.total() + "** 条。";
            return new RuntimeAnswerDetail(answer, "计数查询：查询运行态总数", conclusion);
        }
        if (isAnalysisQuestion(userQuestion)) {
            String conclusion = "基于“" + match.name() + "”返回数据生成业务分析";
            String answer = modelGateway.analyzeRecords(modelConfigId, userQuestion, match.name(), result.total(), result.records(), thinkingEnabled)
                .map(modelAnswer -> "已查询“" + match.name() + "”数据，并基于返回结果完成分析：\n\n" + modelAnswer)
                .orElseGet(() -> fallbackAnalysis(match, result, displayContext));
            return new RuntimeAnswerDetail(answer, "分析查询：查询运行态数据并生成分析", conclusion);
        }
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (!result.records().isEmpty()) {
            answer.append("\n\n前 ").append(result.records().size()).append(" 条记录摘要：");
            for (int i = 0; i < result.records().size(); i++) {
                answer.append("\n").append(i + 1).append(". ").append(recordSummary(displayContext, result.records().get(i)));
            }
        }
        String conclusion = "查询“" + match.name() + "”并返回前 " + result.records().size() + " 条摘要";
        return new RuntimeAnswerDetail(answer.toString(), "列表查询：查询运行态记录摘要", conclusion);
    }

    private RuntimeAnswerDetail yearlyDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
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
                answer.append("- 返回样本：").append(recordSummary(displayContext, result.records().getFirst())).append("。\n");
            }
            String conclusion = "未能从返回记录识别年份字段，不能生成年度分布";
            return new RuntimeAnswerDetail(answer.toString(), "按年分析：查询运行态数据并按年份聚合", conclusion);
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
        return new RuntimeAnswerDetail(answer.toString(), "按年分析：查询运行态数据并按年份聚合", conclusion);
    }

    private RuntimeAnswerDetail stageDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
        Map<String, Long> stageCounts = result.records().stream()
            .map(this::recordData)
            .map(this::recordStage)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));

        String objectLabel = objectLabel(match);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        answer.append("### 按阶段分布\n");
        if (stageCounts.isEmpty()) {
            answer.append("- 当前返回记录中没有可识别的阶段或状态字段，建议确认").append(objectLabel).append("表是否存在阶段、状态、stage、status 等字段。\n");
            if (!result.records().isEmpty()) {
                answer.append("- 返回样本：").append(recordSummary(displayContext, result.records().getFirst())).append("。\n");
            }
            String conclusion = "未能从返回记录识别阶段或状态字段，不能生成阶段分布";
            return new RuntimeAnswerDetail(answer.toString(), "阶段分布分析：查询运行态数据并按阶段聚合", conclusion);
        }
        stageCounts.forEach((stage, count) -> answer.append("- ").append(stage).append("：").append(count).append(unitLabel(objectLabel)).append("\n"));
        long recognizedRecords = stageCounts.values().stream().mapToLong(Long::longValue).sum();
        if (recognizedRecords < result.records().size()) {
            answer.append("\n当前返回记录中有 ").append(result.records().size() - recognizedRecords).append(" 条未识别到阶段/状态字段，以上分布基于已识别字段的 ").append(recognizedRecords).append(" 条记录。\n");
        }
        if (result.records().size() < result.total()) {
            answer.append("\n当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；阶段分布会以本次已返回数据为准。\n");
        }
        String conclusion = "按阶段聚合“" + match.name() + "”，阶段分布为 " + stageDistributionSummary(stageCounts, unitLabel(objectLabel));
        return new RuntimeAnswerDetail(answer.toString(), "阶段分布分析：查询运行态数据并按阶段聚合", conclusion);
    }

    private RuntimeAnswerDetail provinceDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
        Map<String, Long> provinceCounts = result.records().stream()
            .map(this::recordData)
            .map(this::recordProvince)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((left, right) -> {
                int countCompare = Long.compare(right.getValue(), left.getValue());
                return countCompare != 0 ? countCompare : left.getKey().compareTo(right.getKey());
            })
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                java.util.LinkedHashMap::new
            ));

        String objectLabel = objectLabel(match);
        String unitLabel = unitLabel(objectLabel);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        answer.append("### 按省份分布\n");
        if (provinceCounts.isEmpty()) {
            answer.append("- 当前返回记录中没有可识别的省份、地区、区域、城市或地址字段，建议确认").append(objectLabel).append("表是否存在 province、省份、所属省份、地区、区域、城市或地址字段。\n");
            if (!result.records().isEmpty()) {
                answer.append("- 返回样本：").append(recordSummary(displayContext, result.records().getFirst())).append("。\n");
            }
            String conclusion = "未能从返回记录识别省份或区域字段，不能生成省份分布";
            return new RuntimeAnswerDetail(answer.toString(), "省份分布分析：查询运行态数据并按省份/区域聚合", conclusion);
        }
        provinceCounts.forEach((province, count) -> answer.append("- ").append(province).append("：").append(count).append(" ").append(unitLabel).append("\n"));
        long recognizedRecords = provinceCounts.values().stream().mapToLong(Long::longValue).sum();
        if (recognizedRecords < result.records().size()) {
            answer.append("\n当前返回记录中有 ").append(result.records().size() - recognizedRecords).append(" 条未识别到省份/区域字段，以上分布基于已识别字段的 ").append(recognizedRecords).append(" 条记录。\n");
        }
        if (result.records().size() < result.total()) {
            answer.append("\n当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；省份分布会以本次已返回数据为准。\n");
        }
        String conclusion = "按省份/区域聚合“" + match.name() + "”，分布为 " + provinceDistributionSummary(provinceCounts, unitLabel);
        return new RuntimeAnswerDetail(answer.toString(), "省份分布分析：查询运行态数据并按省份/区域聚合", conclusion);
    }

    private RuntimeAnswerDetail newOldCustomerAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
        Map<String, Long> lifecycleCounts = result.records().stream()
            .map(this::recordData)
            .map(this::recordCustomerLifecycleType)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        String objectLabel = objectLabel(match);
        String unitLabel = unitLabel(objectLabel);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        answer.append("### 新老客户对比\n");
        if (lifecycleCounts.isEmpty()) {
            answer.append("- 当前返回记录中没有可识别的新老客户或客户类型字段，不能可靠判断新客户、老客户哪个更多。\n");
            answer.append("- 建议确认").append(objectLabel).append("表是否存在 customerType、customer_type、客户类型、新老客户、是否新客户、客户属性、客户性质、存量/新增 等字段。\n");
            if (!result.records().isEmpty()) {
                answer.append("- 返回样本：").append(recordSummary(displayContext, result.records().getFirst())).append("。\n");
            }
            if (result.records().size() < result.total()) {
                answer.append("- 为避免卡顿，本次只做了前 ").append(result.records().size()).append(" 条轻量样本识别；样本未发现可用字段，因此没有继续全量扫描。\n");
            }
            String conclusion = "未能从返回记录识别新老客户或客户类型字段，不能生成新老客户对比";
            return new RuntimeAnswerDetail(answer.toString(), "新老客户对比：识别客户类型字段并聚合", conclusion);
        }

        long newCustomers = lifecycleCounts.getOrDefault("新客户", 0L);
        long oldCustomers = lifecycleCounts.getOrDefault("老客户", 0L);
        answer.append("- 新客户：").append(newCustomers).append(" ").append(unitLabel).append("\n");
        answer.append("- 老客户：").append(oldCustomers).append(" ").append(unitLabel).append("\n");
        lifecycleCounts.entrySet().stream()
            .filter(entry -> !"新客户".equals(entry.getKey()) && !"老客户".equals(entry.getKey()))
            .forEach(entry -> answer.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append(" ").append(unitLabel).append("\n"));

        long recognizedRecords = lifecycleCounts.values().stream().mapToLong(Long::longValue).sum();
        answer.append("\n### 判断\n");
        if (newCustomers > oldCustomers) {
            answer.append("- 新客户更多，比老客户多 ").append(newCustomers - oldCustomers).append(" ").append(unitLabel).append("。\n");
        } else if (oldCustomers > newCustomers) {
            answer.append("- 老客户更多，比新客户多 ").append(oldCustomers - newCustomers).append(" ").append(unitLabel).append("。\n");
        } else {
            answer.append("- 新客户和老客户数量持平。\n");
        }
        if (recognizedRecords < result.records().size()) {
            answer.append("- 当前返回记录中有 ").append(result.records().size() - recognizedRecords).append(" 条未识别到新老客户字段，以上对比基于已识别的 ").append(recognizedRecords).append(" 条记录。\n");
        }
        if (result.records().size() < result.total()) {
            answer.append("- 当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；新老客户对比会以本次已返回数据为准。\n");
        }
        String conclusion = "按新老客户聚合“" + match.name() + "”，分布为 " + customerLifecycleSummary(lifecycleCounts, unitLabel);
        return new RuntimeAnswerDetail(answer.toString(), "新老客户对比：查询运行态数据并按客户类型聚合", conclusion);
    }
    private boolean isFallbackResult(CloudPivotRuntimeQueryResult result) {
        return result != null && "local-fallback".equals(result.sourceEndpoint());
    }

    private String rawDataSummary(CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
        if (result.records().isEmpty()) {
            return "未返回记录明细";
        }
        return result.records().stream()
            .limit(3)
            .map(record -> recordSummary(displayContext, record))
            .reduce((left, right) -> left + "；" + right)
            .orElse("未返回记录明细");
    }

    private String yearlyDistributionSummary(Map<String, Long> yearlyCounts, String unitLabel) {
        return yearlyCounts.entrySet().stream()
            .map(entry -> entry.getKey() + " 年 " + entry.getValue() + " " + unitLabel)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无年度分布");
    }

    private String stageDistributionSummary(Map<String, Long> stageCounts, String unitLabel) {
        return stageCounts.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + unitLabel)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无阶段分布");
    }

    private String provinceDistributionSummary(Map<String, Long> provinceCounts, String unitLabel) {
        return provinceCounts.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + unitLabel)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无省份分布");
    }

    private String customerLifecycleSummary(Map<String, Long> lifecycleCounts, String unitLabel) {
        return lifecycleCounts.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + " " + unitLabel)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无新老客户分布");
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

    private java.util.Optional<String> recordStage(Map<?, ?> data) {
        for (String key : List.of("stage", "stageName", "stage_name", "opportunityStage", "opportunity_stage", "status", "statusName", "status_name", "state", "阶段", "阶段名称", "状态", "状态名称")) {
            Object value = data.get(key);
            java.util.Optional<String> stage = readableDimensionValue(value);
            if (stage.isPresent()) {
                return stage;
            }
        }
        return data.entrySet().stream()
            .filter(entry -> {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                return key.contains("stage") || key.contains("status") || key.contains("state") || key.contains("阶段") || key.contains("状态");
            })
            .map(Map.Entry::getValue)
            .map(this::readableDimensionValue)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private boolean hasRecognizableStage(List<Map<String, Object>> records) {
        return records.stream()
            .map(this::recordData)
            .map(this::recordStage)
            .anyMatch(java.util.Optional::isPresent);
    }
    private boolean hasRecognizableCustomerLifecycle(List<Map<String, Object>> records) {
        return records.stream()
            .map(this::recordData)
            .map(this::recordCustomerLifecycleType)
            .anyMatch(java.util.Optional::isPresent);
    }

    private java.util.Optional<String> recordCustomerLifecycleType(Map<?, ?> data) {
        for (String key : List.of("customerType", "customer_type", "customerTypeName", "customerCategory", "customer_category", "customerLevelType", "type", "typeName", "category", "categoryName", "isNewCustomer", "newCustomer", "is_new_customer", "客户类型", "客户类别", "新老客户", "客户属性", "客户性质", "是否新客户", "新老类型", "存量类型", "新增存量")) {
            java.util.Optional<String> lifecycle = customerLifecycleValue(data.get(key), key);
            if (lifecycle.isPresent()) {
                return lifecycle;
            }
        }
        return data.entrySet().stream()
            .filter(entry -> {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                return key.contains("customer") || key.contains("type") || key.contains("category") || key.contains("new") || key.contains("old") || key.contains("客户") || key.contains("类型") || key.contains("类别") || key.contains("属性") || key.contains("性质") || key.contains("新老") || key.contains("新增") || key.contains("存量");
            })
            .map(entry -> customerLifecycleValue(entry.getValue(), String.valueOf(entry.getKey())))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private java.util.Optional<String> customerLifecycleValue(Object value, String key) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            for (String nestedKey : List.of("name", "label", "displayName", "displayCode", "text", "value")) {
                java.util.Optional<String> nested = customerLifecycleValue(map.get(nestedKey), nestedKey);
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalized = text.toLowerCase(java.util.Locale.ROOT);
        String normalizedKey = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        if (value instanceof Boolean booleanValue && (normalizedKey.contains("new") || normalizedKey.contains("新"))) {
            return java.util.Optional.of(booleanValue ? "新客户" : "老客户");
        }
        if (("true".equals(normalized) || "是".equals(text)) && (normalizedKey.contains("new") || normalizedKey.contains("新"))) {
            return java.util.Optional.of("新客户");
        }
        if (("false".equals(normalized) || "否".equals(text)) && (normalizedKey.contains("new") || normalizedKey.contains("新"))) {
            return java.util.Optional.of("老客户");
        }
        if (text.contains("新客户") || text.contains("新增") || text.contains("新客") || normalized.contains("new")) {
            return java.util.Optional.of("新客户");
        }
        if (text.contains("老客户") || text.contains("存量") || text.contains("老客") || normalized.contains("existing") || normalized.contains("old")) {
            return java.util.Optional.of("老客户");
        }
        return java.util.Optional.empty();
    }
    private java.util.Optional<String> recordProvince(Map<?, ?> data) {
        for (String key : List.of("province", "provinceName", "province_name", "provinceCode", "region", "regionName", "region_name", "area", "areaName", "city", "cityName", "location", "address", "省份", "所属省份", "省", "省份名称", "地区", "区域", "所属区域", "归属地", "所在地", "城市", "省市", "地址", "联系地址", "注册地址", "办公地址")) {
            Object value = data.get(key);
            java.util.Optional<String> province = readableDimensionValue(value).flatMap(this::normalizeProvinceValue);
            if (province.isPresent()) {
                return province;
            }
        }
        return data.entrySet().stream()
            .filter(entry -> {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                return key.contains("province") || key.contains("region") || key.contains("area") || key.contains("city") || key.contains("location") || key.contains("address") || key.contains("省") || key.contains("地区") || key.contains("区域") || key.contains("城市") || key.contains("地址");
            })
            .map(Map.Entry::getValue)
            .map(this::readableDimensionValue)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .map(this::normalizeProvinceValue)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private java.util.Optional<String> normalizeProvinceValue(String value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String text = value.trim();
        if (text.isBlank()) {
            return java.util.Optional.empty();
        }
        for (String provinceName : CHINA_PROVINCE_NAMES) {
            if (text.contains(provinceName)) {
                return java.util.Optional.of(provinceName);
            }
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([^\\s,，;；]{2,12}(省|市|自治区|特别行政区))").matcher(text);
        if (matcher.find()) {
            return java.util.Optional.of(matcher.group(1));
        }
        return java.util.Optional.of(text.length() > 40 ? text.substring(0, 40) + "..." : text);
    }

    private java.util.Optional<String> readableDimensionValue(Object value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("name", "label", "displayName", "displayCode", "text", "value")) {
                Object nested = map.get(key);
                if (nested != null && !String.valueOf(nested).isBlank()) {
                    return java.util.Optional.of(String.valueOf(nested));
                }
            }
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(text.length() > 40 ? text.substring(0, 40) + "..." : text);
    }

    private java.util.Optional<String> extractYear(Object value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String text = String.valueOf(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2}|19\\d{2})").matcher(text);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private String fallbackAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, DisplayContext displayContext) {
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。当前模型未配置或暂不可用，先基于查询结果给出规则分析：");
        if (result.records().isEmpty()) {
            answer.append("\n\n- 暂未返回可分析记录，建议确认普通用户账号权限、筛选条件和云枢数据是否存在。");
            return answer.toString();
        }
        answer.append("\n\n- 返回样本数：").append(result.records().size()).append(" 条。");
        answer.append("\n- 代表性记录：").append(recordSummary(displayContext, result.records().getFirst())).append("。");
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

    private String recordSummary(DisplayContext displayContext, Map<String, Object> record) {
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return recordDisplayPolicy.summarize(displayContext, dataMap);
        }
        return recordDisplayPolicy.summarize(displayContext, record);
    }

    private boolean isCountQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("几条") || value.contains("几个") || value.contains("几项") || value.contains("几笔") || value.contains("几份") || value.contains("几单") || value.contains("一共") || value.contains("总共") || value.contains("共有");
    }

    private boolean isAnalysisQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看") || value.contains("怎么样") || value.contains("按年") || value.contains("每年") || value.contains("年度") || isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content);
    }

    private boolean isNewOldCustomerQuestion(String content) {
        String value = content == null ? "" : content;
        boolean mentionsNewOldCustomer = value.contains("新客户")
            || value.contains("老客户")
            || value.contains("新老客户")
            || value.contains("新老")
            || value.contains("新增客户")
            || value.contains("存量客户")
            || (value.contains("客户") && value.contains("新") && value.contains("老"));
        boolean asksComparison = value.contains("多")
            || value.contains("哪个")
            || value.contains("哪类")
            || value.contains("谁")
            || value.contains("更多")
            || value.contains("占比")
            || value.contains("比例")
            || value.contains("对比")
            || value.contains("比较")
            || value.contains("分布")
            || value.contains("统计")
            || value.contains("数量")
            || value.contains("情况")
            || value.contains("还是");
        return mentionsNewOldCustomer && asksComparison;
    }
    private boolean isStageDistributionQuestion(String content) {
        String value = content == null ? "" : content;
        return (value.contains("阶段") || value.contains("状态"))
            && (value.contains("分别")
                || value.contains("分布")
                || value.contains("各")
                || value.contains("哪些")
                || value.contains("多少")
                || value.contains("数量")
                || value.contains("处于")
                || value.contains("什么阶段")
                || value.contains("什么状态"));
    }

    private boolean isYearlyDistributionQuestion(String content) {
        String value = content == null ? "" : content;
        return value.contains("每年") || value.contains("按年") || value.contains("年度") || value.contains("年份") || (value.contains("年") && (value.contains("数量") || value.contains("量") || value.contains("情况") || value.contains("趋势")));
    }

    private boolean isProvinceDistributionQuestion(String content) {
        String value = content == null ? "" : content;
        boolean hasProvinceDimension = value.contains("省份")
            || value.contains("所属省")
            || value.contains("哪些省")
            || value.contains("哪个省")
            || value.contains("省市")
            || value.contains("地区")
            || value.contains("区域")
            || value.contains("城市")
            || value.contains("地域")
            || value.contains("归属地")
            || value.contains("所在地");
        return hasProvinceDimension
            && (value.contains("分别")
                || value.contains("分布")
                || value.contains("各")
                || value.contains("哪些")
                || value.contains("多少")
                || value.contains("数量")
                || value.contains("属于")
                || value.contains("都")
                || value.contains("按")
                || value.contains("情况")
                || value.contains("有"));
    }

    private int queryPageSize(String content) {
        CloudPivotRuntimeProperties.Query query = runtimeProperties.getQuery();
        if (isYearlyDistributionQuestion(content)) {
            return query.getYearlyPageSize();
        }
        if (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content) || isAnalysisQuestion(content)) {
            return query.getAnalysisPageSize();
        }
        return isCountQuestion(content) ? query.getCountPageSize() : query.getListPageSize();
    }

    private int queryRecordLimit(String content) {
        CloudPivotRuntimeProperties.Query query = runtimeProperties.getQuery();
        if (isCountQuestion(content)) {
            return query.getCountRecordLimit();
        }
        if (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content)) {
            return query.getDimensionRecordLimit();
        }
        if (isYearlyDistributionQuestion(content)) {
            return query.getYearlyRecordLimit();
        }
        if (isAnalysisQuestion(content)) {
            return query.getAnalysisRecordLimit();
        }
        return query.getListRecordLimit();
    }

    private boolean requiresFullDimensionDetails(String content) {
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuntimeAnswerDetail(String answer, String actionSummary, String conclusionSummary) {
    }
}
