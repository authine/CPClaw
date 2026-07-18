package com.cpclaw.cloudpivot;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final CloudPivotRecordDisplayPolicy recordDisplayPolicy;
    private final CloudPivotRuntimeProperties runtimeProperties;

    public CloudPivotRuntimeService(
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector,
        ModelGateway modelGateway,
        CloudPivotRecordDisplayPolicy recordDisplayPolicy,
        CloudPivotRuntimeProperties runtimeProperties
    ) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
        this.modelGateway = modelGateway;
        this.recordDisplayPolicy = recordDisplayPolicy;
        this.runtimeProperties = runtimeProperties;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
        return query(match, userQuestion, modelConfigId, thinkingEnabled, List.of());
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters) {
        return query(match, userQuestion, modelConfigId, thinkingEnabled, filters, List.of());
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes) {
        return query(match, userQuestion, modelConfigId, thinkingEnabled, filters, metricFieldCodes, Map.of());
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes, Map<String, Object> reasoningContext) {
        return query(match, userQuestion, modelConfigId, thinkingEnabled, filters, metricFieldCodes, reasoningContext, AgentProgressListener.NOOP);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes, Map<String, Object> reasoningContext, AgentProgressListener progressListener) {
        AgentProgressListener progress = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        if (match == null || !"entity".equals(match.objectType()) || !hasText(match.code())) {
            throw new IllegalArgumentException("没有从本地元数据中匹配到可查询的云枢模型，请补充应用或表单名称后重试");
        }

        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中填写云枢登录密码"));

        int pageSize = queryPageSize(userQuestion);
        boolean fullDimensionDetails = requiresFullDimensionDetails(userQuestion);
        List<RuntimeQueryFilter> runtimeFilters = safeFilters(filters);
        if (runtimeFilters.isEmpty() && requestedOwnerFilter(userQuestion).present()) {
            fullDimensionDetails = true;
        }
        int recordLimit = queryRecordLimit(userQuestion);
        progress.onExecution(
            "调用云枢运行态接口",
            "正在查询“" + match.name() + "”数据，schemaCode=" + match.code(),
            Map.of(
                "entityName", match.name(),
                "schemaCode", match.code(),
                "pageSize", pageSize,
                "recordLimit", recordLimit,
                "filters", runtimeFilters.stream().map(RuntimeQueryFilter::summary).toList()
            ),
            "running"
        );
        CloudPivotRuntimeQueryResult result;
        try {
            result = cloudPivotConnector.queryRecords(
                settings.getCloudPivotBaseUrl(),
                settings.getCloudPivotUsername(),
                password,
                match.code(),
                pageSize,
                fullDimensionDetails,
                recordLimit,
                runtimeFilters
            );
        } catch (RuntimeException filteredQueryFailure) {
            progress.checkCancelled();
            if (runtimeFilters.isEmpty()) {
                throw filteredQueryFailure;
            }
            result = cloudPivotConnector.queryRecords(
                settings.getCloudPivotBaseUrl(),
                settings.getCloudPivotUsername(),
                password,
                match.code(),
                Math.max(pageSize, runtimeProperties.getQuery().getFilterFallbackPageSize()),
                true,
                Math.max(recordLimit, runtimeProperties.getQuery().getFilterFallbackRecordLimit())
            );
        }
        if (isStageDistributionQuestion(userQuestion) && !hasRecognizableStage(result.records())) {
            try {
                CloudPivotRuntimeProperties.Query query = runtimeProperties.getQuery();
                int stageSampleLimit = Math.max(
                    query.getDimensionProbeMinRecords(),
                    Math.min(recordLimit, query.getDimensionProbeMaxRecords())
                );
                CloudPivotRuntimeQueryResult stageSample = cloudPivotConnector.queryRecords(
                    settings.getCloudPivotBaseUrl(),
                    settings.getCloudPivotUsername(),
                    password,
                    match.code(),
                    Math.min(stageSampleLimit, query.getDimensionProbePageSize()),
                    true,
                    stageSampleLimit
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
        if ((isAmountAggregationQuestion(userQuestion) || isAmountRankingQuestion(userQuestion)) && !hasRecognizableAmount(result.records())) {
            try {
                CloudPivotRuntimeQueryResult amountDetailSample = cloudPivotConnector.queryRecords(
                    settings.getCloudPivotBaseUrl(),
                    settings.getCloudPivotUsername(),
                    password,
                    match.code(),
                    200,
                    true,
                    20_000,
                    runtimeFilters
                );
                if (hasRecognizableAmount(amountDetailSample.records())) {
                    result = amountDetailSample;
                }
            } catch (RuntimeException ignored) {
                // Keep the fast list result if optional detail sampling fails.
            }
        }
        if (isFallbackResult(result)) {
            throw new IllegalStateException("当前连接返回的是本地演示数据源 local-fallback，不能用于回答真实云枢业务数据。请配置真实云枢地址、账号并重新同步元数据。");
        }
        result = filterResultByRuntimeFilters(result, runtimeFilters);
        progress.onExecution(
            "调用云枢运行态接口",
            "云枢接口调用完成",
            Map.of("sourceEndpoint", result.sourceEndpoint(), "schemaCode", match.code()),
            "completed"
        );
        progress.onExecution(
            "云枢数据返回",
            "已取得真实运行态数据，总数=" + result.total() + "，本次返回=" + result.records().size(),
            Map.of("total", result.total(), "returnedRecords", result.records().size(), "sourceEndpoint", result.sourceEndpoint()),
            "completed"
        );
        AnswerStreamState streamState = new AnswerStreamState();
        RuntimeAnswerDetail detail = summarize(match, result, userQuestion, modelConfigId, thinkingEnabled, runtimeFilters, metricFieldCodes, reasoningContext, progress, streamState);
        if (!streamState.started) {
            streamAnswer(progress, detail.answer(), "rule");
        }
        long displayTotal = detail.displayTotal() >= 0 ? detail.displayTotal() : result.total();
        int displayReturnedRecords = detail.displayReturnedRecords() >= 0 ? detail.displayReturnedRecords() : result.records().size();
        return new CloudPivotQueryAnswer(
            match.name(),
            match.code(),
            displayTotal,
            displayReturnedRecords,
            detail.answer(),
            result.sourceEndpoint(),
            detail.actionSummary(),
            rawDataSummary(result),
            detail.conclusionSummary()
        );
    }

    public RuntimeRecordTarget resolveRecordTarget(MetadataSearchResult match, String userQuestion) {
        if (match == null || !"entity".equals(match.objectType()) || !hasText(match.code())) {
            throw new IllegalArgumentException("没有匹配到可执行删除的云枢业务对象");
        }
        java.util.Optional<String> explicitId = explicitBizObjectId(userQuestion);
        if (explicitId.isPresent()) {
            return new RuntimeRecordTarget(match.code(), explicitId.get());
        }
        if (!hasRecordOrdinalReference(userQuestion)) {
            throw new IllegalArgumentException("删除操作缺少明确记录。请指定记录ID，或说明删除第几条记录。");
        }

        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中填写云枢登录密码"));
        OptionalInt parsedOrdinal = parseRequestedRecordOrdinal(userQuestion);
        if (parsedOrdinal.isEmpty()) {
            throw new IllegalArgumentException("删除操作缺少可解析的记录序号。请明确说明删除第几条记录，或提供记录ID。");
        }
        int ordinal = parsedOrdinal.getAsInt();
        CloudPivotRuntimeQueryResult result = cloudPivotConnector.queryRecords(
            settings.getCloudPivotBaseUrl(),
            settings.getCloudPivotUsername(),
            password,
            match.code(),
            ordinal,
            false,
            ordinal
        );
        if (result.records().isEmpty() || result.records().size() < ordinal) {
            throw new IllegalArgumentException("未查询到可删除的第 " + ordinal + " 条“" + match.name() + "”记录");
        }
        Map<String, Object> record = result.records().get(ordinal - 1);
        String bizObjectId = recordId(record).orElseThrow(() -> new IllegalArgumentException("已查询到记录，但未返回可用于删除的记录ID"));
        return new RuntimeRecordTarget(match.code(), bizObjectId);
    }

    private RuntimeAnswerDetail summarize(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes, Map<String, Object> reasoningContext, AgentProgressListener progress, AnswerStreamState streamState) {
        RuntimeQuestionPlan answerPlan = planRuntimeQuestion(userQuestion);
        OwnerFilter ownerFilter = requestedOwnerFilter(userQuestion);
        if (ownerFilter.present() && safeFilters(filters).isEmpty()) {
            result = filterResultByOwner(result, ownerFilter.name());
        }
        if (isSingleRecordDetailQuestion(userQuestion)) {
            return singleRecordDetail(match, result, userQuestion);
        }
        if (isDetailCollectionQuestion(userQuestion)) {
            return recordDetailList(match, result);
        }
        if (isStatusAmountAggregationQuestion(userQuestion)) {
            return statusAmountAggregationAnalysis(match, result, userQuestion);
        }
        if (answerPlan.operation() == RuntimeOperation.RANKING) {
            return rankingAnalysis(match, result, answerPlan, userQuestion, modelConfigId, thinkingEnabled, reasoningContext);
        }
        if (isStageDistributionQuestion(userQuestion)) {
            return stageDistributionAnalysis(match, result);
        }
        if (isYearlyDistributionQuestion(userQuestion)) {
            return yearlyDistributionAnalysis(match, result);
        }
        if (isProvinceDistributionQuestion(userQuestion)) {
            return provinceDistributionAnalysis(match, result);
        }
        if (isNewOldCustomerQuestion(userQuestion)) {
            return newOldCustomerAnalysis(match, result);
        }
        if (isOwnerOpportunityRankingQuestion(userQuestion)) {
            return ownerOpportunityRankingAnalysis(match, result);
        }
        if (isAmountAggregationQuestion(userQuestion)) {
            return amountAggregationAnalysis(match, result, metricFieldCodes, filters);
        }
        if (isCountQuestion(userQuestion)) {
            if (ownerFilter.present()) {
                String objectLabel = objectLabel(match);
                String unitLabel = unitLabel(objectLabel);
                String answer = "已在云枢中按负责人/销售筛选“" + ownerFilter.name() + "”名下的“" + match.name() + "”，共 **" + result.total() + "** " + unitLabel + "。";
                answer += "\n\n处理口径：已基于“" + match.name() + "”实体的数据项识别负责人/销售字段，并在本次返回且已补齐详情的记录中进行筛选统计。";
                if (result.records().isEmpty()) {
                    answer += "\n\n当前返回记录中没有匹配到负责人/销售为“" + ownerFilter.name() + "”的" + objectLabel + "。";
                }
                String conclusion = "按负责人/销售筛选“" + ownerFilter.name() + "”统计“" + match.name() + "”，数量=" + result.total();
                return new RuntimeAnswerDetail(answer, "筛选计数：按负责人/销售过滤运行态数据后统计数量", conclusion, result.total(), result.records().size());
            }
            String conclusion = "统计“" + match.name() + "”总数为 " + result.total() + " 条";
            String answer = "已在云枢中查询到“" + match.name() + "”对应数据，总计 **" + result.total() + "** 条。";
            return new RuntimeAnswerDetail(answer, "计数查询：查询运行态总数", conclusion);
        }
        if (isAnalysisQuestion(userQuestion)) {
            String conclusion = "基于“" + match.name() + "”返回数据生成业务分析";
            final CloudPivotRuntimeQueryResult summaryResult = result;
            String answer = streamModelAnswer(match, summaryResult, userQuestion, modelConfigId, thinkingEnabled, reasoningContext, progress, streamState);
            return new RuntimeAnswerDetail(answer, "分析查询：查询运行态数据并生成分析", conclusion);
        }
        String conclusion = "查询“" + match.name() + "”并结合返回数据生成业务回答";
        final CloudPivotRuntimeQueryResult summaryResult = result;
        String answer = streamModelAnswer(match, summaryResult, userQuestion, modelConfigId, thinkingEnabled, reasoningContext, progress, streamState);
        return new RuntimeAnswerDetail(answer, "列表查询：查询运行态记录并生成业务回答", conclusion);
    }

    private String streamModelAnswer(
        MetadataSearchResult match,
        CloudPivotRuntimeQueryResult result,
        String userQuestion,
        String modelConfigId,
        boolean thinkingEnabled,
        Map<String, Object> reasoningContext,
        AgentProgressListener progress,
        AnswerStreamState streamState
    ) {
        streamState.started = true;
        progress.onExecution("大模型总结", "正在结合用户问题、云枢元数据和真实查询结果生成回答", Map.of("mode", "model"), "running");
        progress.onAnswerStart("model");
        java.util.Optional<String> modelAnswer = modelGateway.analyzeRecordsStream(
            modelConfigId,
            userQuestion,
            match.name(),
            result.total(),
            result.records(),
            thinkingEnabled,
            reasoningContext,
            progress::onAnswerChunk
        );
        if (modelAnswer.isPresent() && !modelAnswer.get().isBlank()) {
            progress.onAnswerComplete("model");
            progress.onExecution("大模型总结", "回答生成完成", Map.of("mode", "model"), "completed");
            return modelAnswer.get();
        }
        progress.onAnswerReset("模型未在时限内返回完整内容，已切换为规则总结");
        progress.onExecution("大模型总结", "模型不可用或超时，正在生成可解释的规则总结", Map.of("mode", "fallback"), "fallback");
        String fallback = fallbackAnalysis(match, result);
        progress.onAnswerStart("fallback");
        emitAnswerChunks(progress, fallback);
        progress.onAnswerComplete("fallback");
        return fallback;
    }

    private void streamAnswer(AgentProgressListener progress, String answer, String mode) {
        progress.onAnswerStart(mode);
        emitAnswerChunks(progress, answer);
        progress.onAnswerComplete(mode);
    }

    private void emitAnswerChunks(AgentProgressListener progress, String content) {
        AnswerStreamSupport.emitReadableChunks(content, progress::onAnswerChunk);
    }

    private static final class AnswerStreamState {
        private boolean started;
    }

    private RuntimeAnswerDetail recordDetailList(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (result.records().isEmpty()) {
            String conclusion = "未查询到“" + match.name() + "”可展示的明细记录";
            answer.append("\n\n当前没有可展示的记录详情。");
            return new RuntimeAnswerDetail(answer.toString(), "集合详情查询：查询运行态数据集合并展示明细", conclusion);
        }
        answer.append("\n\n以下展示前 ").append(result.records().size()).append(" 条").append(match.name()).append("详情：");
        CloudPivotRecordDisplayPolicy.DisplayContext displayContext = recordDisplayPolicy.context(match.code());
        for (int i = 0; i < result.records().size(); i++) {
            Map<String, Object> record = result.records().get(i);
            Map<?, ?> data = recordData(record);
            answer.append("\n\n").append(i + 1).append(". ")
                .append(recordDisplayPolicy.summarize(displayContext, data));
        }
        String conclusion = "查询“" + match.name() + "”数据集合并展示前 " + result.records().size() + " 条详情";
        return new RuntimeAnswerDetail(answer.toString(), "集合详情查询：查询运行态数据集合并展示明细", conclusion);
    }

    private RuntimeAnswerDetail singleRecordDetail(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion) {
        int ordinal = requestedRecordOrdinal(userQuestion);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (result.records().isEmpty()) {
            String conclusion = "未查询到“" + match.name() + "”可返回的第 " + ordinal + " 条记录";
            answer.append("\n\n当前没有可返回的记录明细。");
            return new RuntimeAnswerDetail(answer.toString(), "单条明细查询：查询运行态第 " + ordinal + " 条记录", conclusion);
        }
        Map<String, Object> record = result.records().get(Math.min(ordinal - 1, result.records().size() - 1));
        Map<?, ?> data = recordData(record);
        CloudPivotRecordDisplayPolicy.DisplayContext displayContext = recordDisplayPolicy.context(match.code());
        answer.append("\n\n第 ").append(ordinal).append(" 条").append(match.name()).append("明细：\n- ")
            .append(recordDisplayPolicy.summarize(displayContext, data));
        String conclusion = "查询“" + match.name() + "”第 " + ordinal + " 条记录明细";
        return new RuntimeAnswerDetail(answer.toString(), "单条明细查询：查询运行态第 " + ordinal + " 条记录并补齐详情", conclusion);
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
                answer.append("- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
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

    private RuntimeAnswerDetail stageDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
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
                answer.append("- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
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

    private RuntimeAnswerDetail amountAggregationAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, List<String> metricFieldCodes, List<RuntimeQueryFilter> filters) {
        List<Double> amounts = result.records().stream()
            .map(this::recordData)
            .map(data -> recordAmount(data, metricFieldCodes))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();
        int missingAmount = result.records().size() - amounts.size();
        String objectLabel = objectLabel(match);
        String unitLabel = unitLabel(objectLabel);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        if (!safeFilters(filters).isEmpty()) {
            answer.append("本次已按条件筛选：").append(runtimeFilterSummary(filters)).append("。\n\n");
        }
        answer.append("### 金额汇总\n");
        if (amounts.isEmpty()) {
            answer.append("- 当前返回记录中没有识别到金额字段，无法计算金额合计。\n");
            if (!result.records().isEmpty()) {
                answer.append("- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
            }
            answer.append("\n### 处理口径\n");
            answer.append("- 已继承或匹配云枢元数据对象“").append(match.name()).append("”，schemaCode=`").append(match.code()).append("`。\n");
            answer.append("- 用户询问金额，应查询业务对象数据集合并识别金额类数据项，而不是只返回对象数量。\n");
            answer.append("- 当前返回数据未包含可识别的金额字段，建议确认云枢模型是否存在 amount、金额、商机金额、预计金额、合同额等数据项。\n");
            String conclusion = "未能从返回记录识别金额字段，不能生成金额汇总";
            return new RuntimeAnswerDetail(answer.toString(), "金额汇总：查询运行态数据集合并按金额字段聚合", conclusion);
        }
        double totalAmount = amounts.stream().mapToDouble(Double::doubleValue).sum();
        double average = totalAmount / amounts.size();
        double max = amounts.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        answer.append("- 可识别金额记录：**").append(amounts.size()).append("** ").append(unitLabel).append("。\n");
        answer.append("- ").append(objectLabel).append("金额合计：**").append(formatAmount(totalAmount)).append("**。\n");
        answer.append("- 平均金额：**").append(formatAmount(average)).append("**。\n");
        answer.append("- 最大单笔金额：**").append(formatAmount(max)).append("**。\n");
        if (missingAmount > 0) {
            answer.append("- 有 ").append(missingAmount).append(" 条返回记录未识别到金额字段，未纳入金额计算。\n");
        }
        if (result.records().size() < result.total()) {
            answer.append("\n当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；以上金额汇总以本次已返回数据为准，生产口径需要分页拉取全量后再计算。\n");
        }
        answer.append("\n### 处理口径\n");
        answer.append("- 识别业务对象：").append(match.name()).append("，schemaCode=`").append(match.code()).append("`。\n");
        answer.append("- 识别用户问题中的指标字段：金额。\n");
        answer.append("- 处理方式：读取该业务对象的数据集合，并对 amount/金额/商机金额/预计金额/合同额等金额字段做合计、平均值和最大值统计。\n");
        String conclusion = "按金额字段聚合“" + match.name() + "”，金额合计=" + formatAmount(totalAmount) + "，可识别金额记录=" + amounts.size();
        return new RuntimeAnswerDetail(answer.toString(), "金额汇总：查询运行态数据集合并按金额字段聚合", conclusion);
    }

    private String runtimeFilterSummary(List<RuntimeQueryFilter> filters) {
        return safeFilters(filters).stream()
            .map(filter -> filter.fieldName() + "(" + filter.fieldCode() + ") " + filter.operator() + " " + filter.value())
            .reduce((left, right) -> left + "；" + right)
            .orElse("无");
    }

    private RuntimeAnswerDetail rankingAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, RuntimeQuestionPlan answerPlan, String userQuestion, String modelConfigId, boolean thinkingEnabled, Map<String, Object> reasoningContext) {
        List<RankedRecord> rankedRecords = result.records().stream()
            .map(record -> new RankedRecord(record, recordData(record), metricValue(recordData(record), answerPlan.metric()).orElse(null)))
            .filter(item -> item.amount() != null)
            .sorted((left, right) -> Double.compare(right.amount(), left.amount()))
            .toList();
        int missingAmount = result.records().size() - rankedRecords.size();
        int topLimit = Math.min(answerPlan.limit(), rankedRecords.size());
        String objectLabel = objectLabel(match);
        String unitLabel = unitLabel(objectLabel);
        String metricLabel = metricLabel(answerPlan.metric());
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。\n\n");
        answer.append("### 结论\n");
        if (rankedRecords.isEmpty()) {
            answer.append("当前返回记录中没有识别到").append(metricLabel).append("字段，无法判断哪些").append(objectLabel).append(metricLabel).append("比较高。\n\n");
            answer.append("### 建议\n");
            answer.append("请确认该业务对象是否存在与“").append(metricLabel).append("”对应的数据项，并确保云枢列表或详情接口能返回该字段。\n");
            if (!result.records().isEmpty()) {
                answer.append("\n返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
            }
            String conclusion = "未能从返回记录识别" + metricLabel + "字段，不能生成排行";
            return new RuntimeAnswerDetail(answer.toString(), "指标排行：按回答计划识别指标字段并降序返回重点记录", conclusion);
        }
        RankedRecord top = rankedRecords.getFirst();
        answer.append(metricLabel).append("最高的").append(objectLabel).append("是 **").append(recordBusinessName(top.data())).append("**，").append(metricLabel).append(" **").append(formatAmount(top.amount())).append("**。\n");
        if (rankedRecords.size() > 1) {
            answer.append("以下是按").append(metricLabel).append("降序排列的前 ").append(topLimit).append(" ").append(unitLabel).append("：\n");
        }
        answer.append("\n### ").append(metricLabel).append("较高的").append(objectLabel).append("\n");
        for (int i = 0; i < topLimit; i++) {
            RankedRecord item = rankedRecords.get(i);
            answer.append(i + 1)
                .append(". **").append(recordBusinessName(item.data())).append("**")
                .append("：").append(metricLabel).append(" **").append(formatAmount(item.amount())).append("**")
                .append(rankingDetail(item.data()))
                .append("\n");
        }
        if (missingAmount > 0) {
            answer.append("\n有 ").append(missingAmount).append(" 条返回记录未识别到").append(metricLabel).append("字段，未纳入排行。\n");
        }
        if (result.records().size() < result.total()) {
            answer.append("\n当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；以上排行以本次已返回数据为准，需要最终口径时应分页拉取全量数据。\n");
        }
        String ruleConclusion = "按" + metricLabel + "降序分析“" + match.name() + "”，最高记录=" + recordBusinessName(top.data()) + "，" + metricLabel + "=" + formatAmount(top.amount());
        String modelSummary = modelGateway.analyzeRecords(modelConfigId, userQuestion, match.name(), result.total(), rankedRecords.stream().limit(topLimit).map(RankedRecord::record).toList(), thinkingEnabled, reasoningContext)
            .map(value -> "\n### AI 总结\n" + value.trim() + "\n")
            .orElse("");
        return new RuntimeAnswerDetail(answer.append(modelSummary).toString(), "指标排行：按回答计划识别指标字段并降序返回重点记录", ruleConclusion);
    }

    private RuntimeAnswerDetail provinceDistributionAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
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
                answer.append("- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
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

    private RuntimeAnswerDetail newOldCustomerAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
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
                answer.append("- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。\n");
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

    private RuntimeAnswerDetail ownerOpportunityRankingAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        Map<String, OwnerOpportunityStats> stats = new java.util.LinkedHashMap<>();
        long missingOwner = 0;
        long missingAmount = 0;
        for (Map<String, Object> record : result.records()) {
            Map<?, ?> data = recordData(record);
            java.util.Optional<String> owner = recordOwner(data);
            if (owner.isEmpty()) {
                missingOwner++;
                continue;
            }
            java.util.Optional<Double> amount = recordAmount(data);
            if (amount.isEmpty()) {
                missingAmount++;
            }
            stats.computeIfAbsent(owner.get(), ignored -> new OwnerOpportunityStats()).add(amount.orElse(0D));
        }

        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        answer.append("\n\n### 按销售/负责人汇总");
        if (stats.isEmpty()) {
            answer.append("\n- 当前返回记录中没有可识别的销售或负责人字段，不能可靠判断谁的商机最多。");
            answer.append("\n- 建议确认商机表是否存在 owner、ownerName、负责人、销售、销售人员等数据项，并确保运行态列表或详情接口返回这些字段。");
            if (!result.records().isEmpty()) {
                answer.append("\n- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。");
            }
            String conclusion = "未能从商机记录识别销售/负责人字段，不能生成负责人维度汇总";
            return new RuntimeAnswerDetail(answer.toString(), "负责人商机汇总：识别负责人和金额字段并聚合", conclusion);
        }

        List<Map.Entry<String, OwnerOpportunityStats>> byAmount = stats.entrySet().stream()
            .sorted((left, right) -> Double.compare(right.getValue().amount(), left.getValue().amount()))
            .toList();
        List<Map.Entry<String, OwnerOpportunityStats>> byCount = stats.entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue().count(), left.getValue().count()))
            .toList();
        Map.Entry<String, OwnerOpportunityStats> topAmount = byAmount.getFirst();
        Map.Entry<String, OwnerOpportunityStats> topCount = byCount.getFirst();

        answer.append("\n- 按金额最高：").append(topAmount.getKey())
            .append("，商机金额合计 **").append(formatAmount(topAmount.getValue().amount())).append("**，商机数 ")
            .append(topAmount.getValue().count()).append(" 条。");
        answer.append("\n- 按数量最多：").append(topCount.getKey())
            .append("，商机数 **").append(topCount.getValue().count()).append("** 条，金额合计 ")
            .append(formatAmount(topCount.getValue().amount())).append("。");
        answer.append("\n\n### 排名前 5");
        byAmount.stream().limit(5).forEach(entry -> answer.append("\n- ")
            .append(entry.getKey()).append("：")
            .append(entry.getValue().count()).append(" 条，金额合计 ")
            .append(formatAmount(entry.getValue().amount())));
        if (missingOwner > 0 || missingAmount > 0) {
            answer.append("\n\n### 数据说明");
            if (missingOwner > 0) {
                answer.append("\n- 有 ").append(missingOwner).append(" 条记录未识别到销售/负责人字段，未纳入负责人排行。");
            }
            if (missingAmount > 0) {
                answer.append("\n- 有 ").append(missingAmount).append(" 条记录未识别到金额字段，金额按 0 计入，数量仍计入。");
            }
        }
        if (result.records().size() < result.total()) {
            answer.append("\n- 当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；以上汇总以本次已返回数据为准。");
        }
        String conclusion = "按销售/负责人聚合“" + match.name() + "”，金额最高=" + topAmount.getKey() + "，数量最多=" + topCount.getKey();
        return new RuntimeAnswerDetail(answer.toString(), "负责人商机汇总：查询运行态数据并按负责人聚合数量和金额", conclusion);
    }

    private RuntimeAnswerDetail statusAmountAggregationAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, String userQuestion) {
        List<String> targetStatuses = requestedStatusFilters(userQuestion);
        Map<String, StatusAmountStats> statusStats = new java.util.LinkedHashMap<>();
        long missingStatus = 0;
        long missingAmount = 0;
        for (Map<String, Object> record : result.records()) {
            Map<?, ?> data = recordData(record);
            java.util.Optional<String> status = recordStage(data);
            if (status.isEmpty()) {
                missingStatus++;
                continue;
            }
            java.util.Optional<Double> amount = recordAmount(data);
            if (amount.isEmpty()) {
                missingAmount++;
            }
            statusStats.computeIfAbsent(status.get(), ignored -> new StatusAmountStats()).add(amount.orElse(0D));
        }

        String objectLabel = objectLabel(match);
        String unitLabel = unitLabel(objectLabel);
        StringBuilder answer = new StringBuilder();
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        answer.append("\n\n### 状态筛选与金额汇总");
        if (statusStats.isEmpty()) {
            answer.append("\n- 当前返回记录中没有可识别的状态/阶段字段，不能可靠统计目标状态的").append(objectLabel).append("数量和金额。");
            answer.append("\n- 建议确认").append(objectLabel).append("表是否存在 status、projectStatus、stage、状态、项目状态等数据项，并确保运行态接口返回这些字段。");
            if (!result.records().isEmpty()) {
                answer.append("\n- 返回样本：").append(recordSummary(match.code(), result.records().getFirst())).append("。");
            }
            String conclusion = "未能从“" + match.name() + "”记录识别状态字段，不能生成状态筛选金额汇总";
            return new RuntimeAnswerDetail(answer.toString(), "状态金额汇总：识别状态字段和金额字段并聚合", conclusion);
        }

        List<Map.Entry<String, StatusAmountStats>> matchedStats = statusStats.entrySet().stream()
            .filter(entry -> statusMatches(entry.getKey(), targetStatuses))
            .toList();
        long matchedCount = matchedStats.stream().mapToLong(entry -> entry.getValue().count()).sum();
        double matchedAmount = matchedStats.stream().mapToDouble(entry -> entry.getValue().amount()).sum();
        String targetLabel = targetStatuses.isEmpty() ? "目标状态" : String.join("/", targetStatuses);

        answer.append("\n- 目标状态：").append(targetLabel).append("。");
        answer.append("\n- ").append(targetLabel).append(objectLabel).append("数量：**").append(matchedCount).append("** ").append(unitLabel).append("。");
        answer.append("\n- ").append(targetLabel).append(objectLabel).append("金额合计：**").append(formatAmount(matchedAmount)).append("**。");
        answer.append("\n\n### 状态明细");
        if (matchedStats.isEmpty()) {
            answer.append("\n- 当前返回数据中没有命中“").append(targetLabel).append("”的记录。");
        } else {
            matchedStats.forEach(entry -> answer.append("\n- ")
                .append(entry.getKey()).append("：")
                .append(entry.getValue().count()).append(" ").append(unitLabel)
                .append("，金额合计 ").append(formatAmount(entry.getValue().amount())));
        }
        answer.append("\n\n### 处理口径");
        answer.append("\n- 继承或匹配云枢元数据对象“").append(match.name()).append("”，schemaCode=`").append(match.code()).append("`。");
        answer.append("\n- 从运行态记录中识别状态字段（status/projectStatus/stage/状态/项目状态等）和金额字段（amount/projectAmount/contractAmount/金额/项目金额等）。");
        answer.append("\n- 按目标状态“").append(targetLabel).append("”筛选记录，再分别聚合数量和金额。");
        if (missingStatus > 0 || missingAmount > 0) {
            answer.append("\n\n### 数据说明");
            if (missingStatus > 0) {
                answer.append("\n- 有 ").append(missingStatus).append(" 条记录未识别到状态/阶段字段，未纳入目标状态统计。");
            }
            if (missingAmount > 0) {
                answer.append("\n- 有 ").append(missingAmount).append(" 条记录未识别到金额字段，金额按 0 计入，数量仍计入。");
            }
        }
        if (result.records().size() < result.total()) {
            answer.append("\n- 当前运行态接口本次返回 ").append(result.records().size()).append(" 条记录，少于总数 ").append(result.total()).append(" 条；以上汇总以本次已返回数据为准。");
        }
        String conclusion = "按状态筛选“" + match.name() + "”，" + targetLabel + "数量=" + matchedCount + "，金额合计=" + formatAmount(matchedAmount);
        return new RuntimeAnswerDetail(answer.toString(), "状态金额汇总：查询运行态数据并按状态筛选后聚合数量和金额", conclusion);
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
            .map(record -> recordSummary(result.schemaCode(), record))
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
        if (objectLabel.contains("项目")) {
            return "个项目";
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
        return data.entrySet().stream()
            .filter(entry -> isYearSourceField(entry.getKey()))
            .map(entry -> extractYear(entry.getValue()))
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private boolean isYearSourceField(Object key) {
        String value = String.valueOf(key).toLowerCase(java.util.Locale.ROOT);
        return value.contains("created")
            || value.contains("createtime")
            || value.contains("createdtime")
            || value.contains("createdate")
            || value.contains("register")
            || value.contains("signup")
            || value.contains("date")
            || value.contains("time")
            || value.contains("year")
            || value.contains("创建")
            || value.contains("登记")
            || value.contains("注册")
            || value.contains("时间")
            || value.contains("日期")
            || value.contains("年份");
    }

    private java.util.Optional<String> recordStage(Map<?, ?> data) {
        for (String key : List.of("stage", "stageName", "stage_name", "opportunityStage", "opportunity_stage", "projectStatus", "project_status", "status", "statusName", "status_name", "state", "阶段", "阶段名称", "项目状态", "状态", "状态名称")) {
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

    private boolean hasRecognizableAmount(List<Map<String, Object>> records) {
        return records.stream()
            .map(this::recordData)
            .map(this::recordAmount)
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

    private java.util.Optional<String> recordOwner(Map<?, ?> data) {
        for (String key : List.of("owner", "ownerName", "owner_name", "sales", "salesName", "sales_name", "salesperson", "salesPerson", "salesman", "createdByName", "负责人", "负责人姓名", "销售", "销售人员", "销售负责人", "归属销售", "业务员")) {
            java.util.Optional<String> owner = readableDimensionValue(data.get(key));
            if (owner.isPresent()) {
                return owner;
            }
        }
        return data.entrySet().stream()
            .filter(entry -> {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                return key.contains("owner") || key.contains("sales") || key.contains("seller") || key.contains("负责人") || key.contains("销售") || key.contains("业务员");
            })
            .map(Map.Entry::getValue)
            .map(this::readableDimensionValue)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private CloudPivotRuntimeQueryResult filterResultByOwner(CloudPivotRuntimeQueryResult result, String ownerName) {
        if (result == null || !hasText(ownerName)) {
            return result;
        }
        String expected = normalizeOwnerName(ownerName);
        List<Map<String, Object>> matchedRecords = result.records().stream()
            .filter(record -> recordOwner(recordData(record))
                .map(owner -> normalizeOwnerName(owner).contains(expected) || expected.contains(normalizeOwnerName(owner)))
                .orElse(false))
            .toList();
        return new CloudPivotRuntimeQueryResult(result.schemaCode(), matchedRecords.size(), matchedRecords, result.sourceEndpoint());
    }

    private CloudPivotRuntimeQueryResult filterResultByRuntimeFilters(CloudPivotRuntimeQueryResult result, List<RuntimeQueryFilter> filters) {
        List<RuntimeQueryFilter> safeFilters = safeFilters(filters);
        if (result == null || safeFilters.isEmpty()) {
            return result;
        }
        List<Map<String, Object>> matchedRecords = result.records().stream()
            .filter(record -> safeFilters.stream().allMatch(filter -> runtimeFilterMatches(record, filter)))
            .toList();
        return new CloudPivotRuntimeQueryResult(result.schemaCode(), matchedRecords.size(), matchedRecords, result.sourceEndpoint());
    }

    private boolean runtimeFilterMatches(Map<String, Object> record, RuntimeQueryFilter filter) {
        if (record == null || filter == null || !filter.valid()) {
            return false;
        }
        Map<?, ?> data = recordData(record);
        java.util.Optional<String> actual = readableDimensionValue(data.get(filter.fieldCode()));
        if (actual.isEmpty()) {
            actual = readableDimensionValue(record.get(filter.fieldCode()));
        }
        if (actual.isEmpty() && isOwnerFilter(filter)) {
            actual = recordOwner(data);
        }
        String expected = normalizeOwnerName(filter.value());
        return actual
            .map(value -> normalizeOwnerName(value).contains(expected) || expected.contains(normalizeOwnerName(value)))
            .orElse(false);
    }

    private boolean isOwnerFilter(RuntimeQueryFilter filter) {
        String value = (filter.fieldName() + filter.fieldCode() + filter.source()).toLowerCase(java.util.Locale.ROOT);
        return value.contains("owner") || value.contains("sales") || value.contains("seller") || value.contains("\u8d1f\u8d23\u4eba") || value.contains("\u9500\u552e") || value.contains("\u4e1a\u52a1\u5458");
    }

    private List<RuntimeQueryFilter> safeFilters(List<RuntimeQueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        return filters.stream()
            .filter(filter -> filter != null && filter.valid())
            .limit(8)
            .toList();
    }

    private OwnerFilter requestedOwnerFilter(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        if (value.isBlank()) {
            return OwnerFilter.none();
        }
        for (String suffix : List.of("名下有多少", "名下有几个", "名下有几条", "负责的", "负责了", "负责多少", "销售的")) {
            java.util.Optional<String> name = nameBefore(value, suffix);
            if (name.isPresent()) {
                return new OwnerFilter(name.get());
            }
        }
        java.util.Optional<String> directOwnerCount = directOwnerCountFilter(value);
        if (directOwnerCount.isPresent()) {
            return new OwnerFilter(directOwnerCount.get());
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:负责人|销售|业务员|归属销售|owner)(?:是|为|=|：|:)?([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9·._-]{1,15})").matcher(value);
        if (matcher.find()) {
            return new OwnerFilter(cleanOwnerName(matcher.group(1)));
        }
        return OwnerFilter.none();
    }

    private java.util.Optional<String> directOwnerCountFilter(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^([\\p{IsHan}]{2,5}|[A-Za-z][A-Za-z0-9._-]{1,30})(?:有多少|有几个|有几条)(?:商机|项目|客户|线索|订单).*$").matcher(value);
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(cleanOwnerName(matcher.group(1))).filter(this::hasText);
    }

    private java.util.Optional<String> nameBefore(String value, String suffix) {
        int index = value.indexOf(suffix);
        if (index <= 0) {
            return java.util.Optional.empty();
        }
        String before = value.substring(0, index);
        for (String prefix : List.of("请问", "帮我查", "帮我看看", "查询", "统计", "分析", "系统", "现在", "当前")) {
            if (before.startsWith(prefix) && before.length() > prefix.length()) {
                before = before.substring(prefix.length());
            }
        }
        return java.util.Optional.of(cleanOwnerName(before)).filter(this::hasText);
    }

    private String cleanOwnerName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
            .replaceAll("^(?:(请问|帮我查|帮我看看|查询|统计|分析|系统|现在|当前))+", "")
            .replaceAll("(名下|负责|销售|负责人|业务员|归属销售|有多少|有几个|有几条|多少|几个|几条|商机|项目|客户|数据|信息|情况)+$", "")
            .trim();
        return isLikelyPersonName(cleaned) ? cleaned : "";
    }

    private boolean isLikelyPersonName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String text = value.replaceAll("\\s+", "");
        if (text.contains("的") || text.contains("里") || text.contains("中") || text.contains("下")) {
            return false;
        }
        if (text.contains("应用") || text.contains("系统") || text.contains("项目") || text.contains("客户") || text.contains("商机") || text.contains("数据") || text.contains("基础")
            || text.contains("现在") || text.contains("当前") || text.contains("今天") || text.contains("今年") || text.contains("本月") || text.contains("全部") || text.contains("所有")) {
            return false;
        }
        return text.matches("[\\p{IsHan}]{2,5}") || text.matches("[A-Za-z][A-Za-z0-9._-]{1,30}");
    }

    private String normalizeOwnerName(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private java.util.Optional<Double> recordAmount(Map<?, ?> data) {
        for (String key : List.of("amount", "projectAmount", "project_amount", "opportunityAmount", "opportunity_amount", "contractAmount", "contract_amount", "totalAmount", "total_amount", "expectedAmount", "expected_amount", "金额", "项目金额", "商机金额", "预计金额", "合同额", "收入")) {
            java.util.Optional<Double> amount = numericValue(data.get(key));
            if (amount.isPresent()) {
                return amount;
            }
        }
        return data.entrySet().stream()
            .filter(entry -> {
                String key = String.valueOf(entry.getKey()).toLowerCase(java.util.Locale.ROOT);
                return key.contains("amount") || key.contains("money") || key.contains("revenue") || key.contains("金额") || key.contains("合同额") || key.contains("收入");
            })
            .map(Map.Entry::getValue)
            .map(this::numericValue)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .findFirst();
    }

    private java.util.Optional<Double> recordAmount(Map<?, ?> data, List<String> fieldCodes) {
        if (fieldCodes != null) {
            for (String fieldCode : fieldCodes) {
                if (!hasText(fieldCode)) {
                    continue;
                }
                java.util.Optional<Double> direct = numericValue(data.get(fieldCode));
                if (direct.isPresent()) {
                    return direct;
                }
                java.util.Optional<Double> caseInsensitive = data.entrySet().stream()
                    .filter(entry -> String.valueOf(entry.getKey()).equalsIgnoreCase(fieldCode))
                    .map(Map.Entry::getValue)
                    .map(this::numericValue)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .findFirst();
                if (caseInsensitive.isPresent()) {
                    return caseInsensitive;
                }
            }
        }
        return recordAmount(data);
    }

    private java.util.Optional<Double> numericValue(Object value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (value instanceof Number number) {
            return java.util.Optional.of(number.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("amount", "value", "val", "number", "num", "displayValue", "display", "text", "name", "label")) {
                java.util.Optional<Double> nested = numericValue(map.get(key));
                if (nested.isPresent()) {
                    return nested;
                }
            }
        }
        String text = String.valueOf(value).replaceAll("[,，￥¥元万元万]", "").trim();
        if (text.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private java.util.Optional<Double> metricValue(Map<?, ?> data, RuntimeMetric metric) {
        if (metric == RuntimeMetric.AMOUNT) {
            return recordAmount(data);
        }
        return java.util.Optional.empty();
    }

    private String metricLabel(RuntimeMetric metric) {
        return metric == RuntimeMetric.AMOUNT ? "金额" : "指标";
    }

    private String formatAmount(double amount) {
        if (Math.abs(amount - Math.rint(amount)) < 0.000001) {
            return String.valueOf(Math.round(amount));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", amount);
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
        String text = valueText(value).trim();
        return text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(truncateReadable(text, 40));
    }

    private java.util.Optional<String> extractYear(Object value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String text = String.valueOf(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(20\\d{2}|19\\d{2})").matcher(text);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        int maxYear = java.time.Year.now().getValue() + 1;
        return year >= 2000 && year <= maxYear ? java.util.Optional.of(String.valueOf(year)) : java.util.Optional.empty();
    }

    private String fallbackAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        return businessOverviewAnalysis(match, result);
    }

    private String businessOverviewAnalysis(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        StringBuilder answer = new StringBuilder();
        String objectLabel = objectLabel(match);
        answer.append("已在云枢中查询到“").append(match.name()).append("”对应数据，总计 **").append(result.total()).append("** 条。");
        if (result.records().isEmpty()) {
            answer.append("\n\n- 暂未返回可分析记录，建议确认普通用户账号权限、筛选条件和云枢数据是否存在。");
            return answer.toString();
        }
        answer.append("\n\n### 业务概览");
        answer.append("\n- 本次用于分析的返回样本：").append(result.records().size()).append(" 条。");
        if (result.records().size() < result.total()) {
            answer.append("\n- 当前样本少于总数，以下结论以本次返回样本为准；需要最终结论时应分页拉取全量数据。");
        }
        stageSummary(result.records()).ifPresent(summary -> answer.append("\n- 阶段/状态分布：").append(summary).append("。"));
        opportunityAmountSummary(result.records()).ifPresent(summary -> answer.append("\n- 金额概览：").append(summary).append("。"));
        ownerSummary(result.records()).ifPresent(summary -> answer.append("\n- 负责人分布：").append(summary).append("。"));
        yearSummary(result.records()).ifPresent(summary -> answer.append("\n- 时间分布：").append(summary).append("。"));
        answer.append("\n\n### 初步判断");
        answer.append("\n- 如果").append(objectLabel).append("集中在早期或停滞状态，说明推进管道可能存在积压，需要优先关注高金额、高概率或长时间未推进的记录。");
        answer.append("\n- 如果金额集中在少数记录或少数负责人名下，建议进一步核查关键机会、资源投入和跟进压力。");
        answer.append("\n\n### 建议下一步");
        answer.append("\n1. 按阶段/状态继续下钻，先看停滞或推进风险最高的记录。");
        answer.append("\n2. 按负责人汇总数量和金额，识别重点跟进团队或人员。");
        answer.append("\n3. 按金额排序查看前 10 条重点记录，并结合客户/关联对象判断优先级。");
        return answer.toString();
    }

    private java.util.Optional<String> opportunityAmountSummary(List<Map<String, Object>> records) {
        List<Double> amounts = records.stream()
            .map(this::recordData)
            .map(this::recordAmount)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .toList();
        if (amounts.isEmpty()) {
            return java.util.Optional.empty();
        }
        double total = amounts.stream().mapToDouble(Double::doubleValue).sum();
        double average = total / amounts.size();
        double max = amounts.stream().mapToDouble(Double::doubleValue).max().orElse(0D);
        return java.util.Optional.of("样本金额合计 " + formatAmount(total) + "，平均 " + formatAmount(average) + "，最大 " + formatAmount(max));
    }

    private java.util.Optional<String> stageSummary(List<Map<String, Object>> records) {
        Map<String, Long> counts = records.stream()
            .map(this::recordData)
            .map(this::recordStage)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        if (counts.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(counts.entrySet().stream()
            .map(entry -> entry.getKey() + " " + entry.getValue() + " 条")
            .reduce((left, right) -> left + "，" + right)
            .orElse(""));
    }

    private java.util.Optional<String> ownerSummary(List<Map<String, Object>> records) {
        Map<String, Long> counts = records.stream()
            .map(this::recordData)
            .map(this::recordOwner)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()));
        if (counts.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(counts.entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(5)
            .map(entry -> entry.getKey() + " " + entry.getValue() + " 条")
            .reduce((left, right) -> left + "，" + right)
            .orElse(""));
    }

    private java.util.Optional<String> yearSummary(List<Map<String, Object>> records) {
        Map<String, Long> counts = records.stream()
            .map(this::recordData)
            .map(this::recordYear)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .collect(java.util.stream.Collectors.groupingBy(String::valueOf, java.util.TreeMap::new, java.util.stream.Collectors.counting()));
        if (counts.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(yearlyDistributionSummary(counts, "条"));
    }

    private Map<?, ?> recordData(Map<String, Object> record) {
        Object data = record.get("data");
        return data instanceof Map<?, ?> dataMap ? dataMap : record;
    }

    private java.util.Optional<String> recordId(Map<String, Object> record) {
        for (String key : List.of("id", "objectId", "dataId", "bizObjectId")) {
            Object value = record.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return java.util.Optional.of(String.valueOf(value));
            }
        }
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            for (String key : List.of("id", "objectId", "dataId", "bizObjectId")) {
                Object value = dataMap.get(key);
                if (value != null && hasText(String.valueOf(value))) {
                    return java.util.Optional.of(String.valueOf(value));
                }
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> explicitBizObjectId(String content) {
        if (content == null || content.isBlank()) {
            return java.util.Optional.empty();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?i)(?:bizObjectId|objectId|dataId|记录ID|ID)[:：=\s]+([A-Za-z0-9_\\-]{6,})")
            .matcher(content);
        return matcher.find() ? java.util.Optional.of(matcher.group(1)) : java.util.Optional.empty();
    }

    private String recordSummary(String schemaCode, Map<String, Object> record) {
        CloudPivotRecordDisplayPolicy.DisplayContext displayContext = recordDisplayPolicy.context(schemaCode);
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return recordDisplayPolicy.summarize(displayContext, dataMap);
        }
        return recordDisplayPolicy.summarize(displayContext, record);
    }

    private String recordBusinessName(Map<?, ?> data) {
        return readableDimensionValue(firstNonBlank(data, "instanceName", "name", "title", "标题", "商机名称", "项目名称", "客户名称"))
            .orElse("未命名记录");
    }

    private String rankingDetail(Map<?, ?> data) {
        List<String> details = new java.util.ArrayList<>();
        readableDimensionValue(firstNonBlank(data, "stage", "阶段", "status", "状态", "projectStatus", "项目状态"))
            .ifPresent(value -> details.add("阶段/状态：" + value));
        recordOwner(data).ifPresent(value -> details.add("负责人：" + value));
        readableDimensionValue(firstNonBlank(data, "customer", "客户", "customerName", "cust_id", "opportunityCustomer"))
            .ifPresent(value -> details.add("客户：" + value));
        readableDimensionValue(firstNonBlank(data, "createdAt", "createTime", "createdTime", "创建时间", "修改时间"))
            .ifPresent(value -> details.add("时间：" + value));
        return details.isEmpty() ? "。" : "，" + String.join("，", details.stream().distinct().limit(4).toList()) + "。";
    }

    private String pickReadableValue(Map<?, ?> values) {
        return values.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> !String.valueOf(entry.getKey()).toLowerCase().contains("password"))
            .filter(entry -> !isTechnicalField(entry.getKey()))
            .sorted((left, right) -> Integer.compare(displayPriority(left.getKey()), displayPriority(right.getKey())))
            .limit(5)
            .map(entry -> fieldLabel(entry.getKey()) + "：" + valueText(entry.getValue()))
            .reduce((left, right) -> left + "，" + right)
            .orElse("无可展示字段");
    }

    private int displayPriority(Object key) {
        String value = String.valueOf(key);
        if ("instanceName".equalsIgnoreCase(value) || "name".equalsIgnoreCase(value) || "title".equalsIgnoreCase(value)) {
            return 0;
        }
        if ("amount".equalsIgnoreCase(value) || "opportunityAmount".equalsIgnoreCase(value) || "projectAmount".equalsIgnoreCase(value) || String.valueOf(key).contains("金额")) {
            return 1;
        }
        if ("id".equalsIgnoreCase(value) || "objectId".equalsIgnoreCase(value) || "dataId".equalsIgnoreCase(value)) {
            return 3;
        }
        return 2;
    }

    private String valueText(Object value) {
        return valueText(value, 0);
    }

    private String valueText(Object value, int depth) {
        if (value == null || depth > 3) {
            return "";
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return truncateReadable(String.valueOf(value), 80);
        }
        if (value instanceof java.time.temporal.TemporalAccessor) {
            return String.valueOf(value);
        }
        if (value instanceof List<?> list) {
            List<String> readableItems = list.stream()
                .map(item -> valueText(item, depth + 1).trim())
                .filter(this::hasText)
                .filter(item -> !"null".equalsIgnoreCase(item))
                .distinct()
                .limit(4)
                .toList();
            if (readableItems.isEmpty()) {
                return "";
            }
            String text = String.join("、", readableItems.stream().limit(3).toList());
            return list.size() > 3 ? text + "等 " + list.size() + " 项" : text;
        }
        if (value instanceof Map<?, ?> map) {
            return readableMapText(map, depth + 1);
        }
        return truncateReadable(String.valueOf(value), 80);
    }

    private String readableMapText(Map<?, ?> map, int depth) {
        List<String> preferredValues = readablePreferredKeys().stream()
            .map(map::get)
            .map(value -> valueText(value, depth + 1).trim())
            .filter(this::hasText)
            .filter(value -> !"null".equalsIgnoreCase(value))
            .distinct()
            .limit(3)
            .toList();
        if (!preferredValues.isEmpty()) {
            return joinBusinessValues(preferredValues);
        }

        List<String> readableValues = map.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .filter(entry -> !isTechnicalField(entry.getKey()))
            .sorted((left, right) -> Integer.compare(displayPriority(left.getKey()), displayPriority(right.getKey())))
            .map(entry -> valueText(entry.getValue(), depth + 1).trim())
            .filter(this::hasText)
            .filter(value -> !"null".equalsIgnoreCase(value))
            .distinct()
            .limit(3)
            .toList();
        return joinBusinessValues(readableValues);
    }

    private List<String> readablePreferredKeys() {
        return List.of(
            "name", "displayName", "instanceName", "title", "label", "text",
            "cust_fullname", "customerName", "sequenceNo", "sequence_no",
            "org_name", "deptName", "unitName", "username", "userName", "mobile",
            "displayCode", "value"
        );
    }

    private String joinBusinessValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return truncateReadable(values.getFirst(), 80);
        }
        String first = values.getFirst();
        String rest = String.join(" / ", values.subList(1, values.size()));
        return truncateReadable(first + "（" + rest + "）", 80);
    }

    private String truncateReadable(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String value = text.trim();
        return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
    }

    private void appendField(StringBuilder answer, String label, Object value) {
        if (value == null) {
            return;
        }
        String text = valueText(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return;
        }
        answer.append("\n- ").append(label).append("：").append(text);
    }

    private Object firstNonBlank(Map<?, ?> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value != null && !valueText(value).isBlank() && !"null".equalsIgnoreCase(valueText(value))) {
                return value;
            }
        }
        return null;
    }

    private void appendAdditionalFields(StringBuilder answer, Map<?, ?> data) {
        data.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .filter(entry -> !isSensitiveOrInternalField(entry.getKey()))
            .filter(entry -> !isTechnicalField(entry.getKey()))
            .filter(entry -> !isAlreadyDisplayedField(entry.getKey()))
            .sorted((left, right) -> Integer.compare(displayPriority(left.getKey()), displayPriority(right.getKey())))
            .limit(6)
            .forEach(entry -> appendField(answer, fieldLabel(entry.getKey()), entry.getValue()));
    }

    private boolean isSensitiveOrInternalField(Object key) {
        String value = String.valueOf(key).toLowerCase(java.util.Locale.ROOT);
        return value.contains("password") || value.contains("token") || value.contains("secret") || value.contains("credential");
    }

    private boolean isTechnicalField(Object key) {
        String value = String.valueOf(key).toLowerCase(java.util.Locale.ROOT);
        return List.of(
            "schemacode", "propertytype", "exceltype", "unittype", "datatype", "sortkey", "sort", "type",
            "creater", "createdby", "modifier", "modifiedby", "ownerid", "workflowinstanceid"
        ).contains(value);
    }

    private String fieldLabel(Object key) {
        String value = String.valueOf(key);
        return switch (value) {
            case "instanceName", "name", "title" -> "名称";
            case "stage", "status", "projectStatus" -> "阶段/状态";
            case "amount", "opportunityAmount", "projectAmount" -> "金额";
            case "owner", "ownerName", "createdByName" -> "负责人";
            case "customer", "customerName", "cust_id", "opportunityCustomer" -> "客户";
            case "createdAt", "createTime", "createdTime" -> "创建时间";
            case "modifiedTime", "updateTime", "modifiedAt" -> "修改时间";
            case "pre_sign_date" -> "预计签约时间";
            case "sign_probability_new" -> "签约概率";
            case "clues_id" -> "关联线索";
            case "sales_org_id" -> "销售组织";
            case "ownerDeptId" -> "所属部门";
            default -> value;
        };
    }

    private boolean isAlreadyDisplayedField(Object key) {
        String value = String.valueOf(key).toLowerCase(java.util.Locale.ROOT);
        return List.of(
            "id", "instancename", "name", "title", "标题", "商机名称", "客户名称",
            "stage", "阶段", "status", "状态", "amount", "金额", "opportunityamount", "商机金额",
            "owner", "负责人", "ownername", "createdbyname", "customer", "客户", "customername",
            "cust_id", "opportunitycustomer", "createdat", "createtime", "createdtime", "创建时间"
        ).contains(value);
    }

    private boolean isCountQuestion(String content) {
        if (isSingleRecordDetailQuestion(content)) {
            return false;
        }
        String value = content == null ? "" : content;
        return value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("几条") || value.contains("几个") || value.contains("几项") || value.contains("几笔") || value.contains("几份") || value.contains("几单") || value.contains("一共") || value.contains("总共") || value.contains("共有");
    }

    private boolean isAnalysisQuestion(String content) {
        if (isSingleRecordDetailQuestion(content) || isDetailCollectionQuestion(content)) {
            return false;
        }
        String value = content == null ? "" : content;
        return value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看") || value.contains("怎么样") || value.contains("情况") || value.contains("概况") || value.contains("整体") || value.contains("按年") || value.contains("每年") || value.contains("年度") || isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content) || isOwnerOpportunityRankingQuestion(content) || isStatusAmountAggregationQuestion(content) || isAmountRankingQuestion(content) || isAmountAggregationQuestion(content);
    }

    private boolean isDetailCollectionQuestion(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        boolean asksDetail = runtimeProperties.getQuery().getCollectionRequestPhrases().stream()
            .filter(this::hasText)
            .anyMatch(value::contains);
        if (!asksDetail || isSingleRecordDetailQuestion(value)) {
            return false;
        }
        boolean asksAggregation = isStageDistributionQuestion(value)
            || isYearlyDistributionQuestion(value)
            || isProvinceDistributionQuestion(value)
            || isNewOldCustomerQuestion(value)
            || isOwnerOpportunityRankingQuestion(value)
            || isStatusAmountAggregationQuestion(value)
            || isAmountRankingQuestion(value)
            || isAmountAggregationQuestion(value)
            || value.contains("趋势")
            || value.contains("洞察")
            || value.contains("诊断")
            || value.contains("建议")
            || value.contains("占比")
            || value.contains("比例")
            || value.contains("分布")
            || value.contains("排行")
            || value.contains("排名");
        return !asksAggregation;
    }

    private boolean isSingleRecordDetailQuestion(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        boolean asksSingleRecord = value.contains("第一条")
            || value.contains("第1条")
            || value.contains("第一个")
            || value.contains("第1个")
            || value.contains("首条")
            || value.contains("第一笔")
            || value.contains("第1笔")
            || value.contains("第一单")
            || value.contains("第1单");
        boolean asksDetail = value.contains("明细")
            || value.contains("详情")
            || value.contains("详细")
            || value.contains("信息")
            || value.contains("是什么")
            || value.contains("是什么信息")
            || value.contains("内容")
            || value.contains("返回")
            || value.contains("查看")
            || value.contains("看一下");
        return asksSingleRecord && asksDetail;
    }

    private int requestedRecordOrdinal(String content) {
        return parseRequestedRecordOrdinal(content).orElse(1);
    }

    private OptionalInt parseRequestedRecordOrdinal(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        Matcher arabic = Pattern.compile("第([0-9]+)(条|个|笔|单)").matcher(value);
        if (arabic.find()) {
            int parsed = Integer.parseInt(arabic.group(1));
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        }
        Matcher chinese = Pattern.compile("第([一二三四五六七八九十两]+)(条|个|笔|单)").matcher(value);
        if (chinese.find()) {
            int parsed = chineseOrdinal(chinese.group(1));
            return parsed > 0 ? OptionalInt.of(parsed) : OptionalInt.empty();
        }
        if (value.contains("首条") || value.contains("第一条") || value.contains("第一个") || value.contains("第一笔") || value.contains("第一单")) {
            return OptionalInt.of(1);
        }
        return OptionalInt.empty();
    }

    private int chineseOrdinal(String value) {
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> {
                if (value.startsWith("十")) {
                    yield 10 + chineseDigit(value.substring(1));
                }
                int tenIndex = value.indexOf('十');
                if (tenIndex > 0) {
                    int tens = chineseDigit(value.substring(0, tenIndex));
                    int ones = tenIndex + 1 < value.length() ? chineseDigit(value.substring(tenIndex + 1)) : 0;
                    yield tens * 10 + ones;
                }
                yield 0;
            }
        };
    }

    private int chineseDigit(String value) {
        return switch (value) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 0;
        };
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

    private boolean isOwnerOpportunityRankingQuestion(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        boolean mentionsOpportunity = value.contains("商机") || value.contains("机会") || value.contains("销售机会");
        boolean asksOwner = value.contains("谁") || value.contains("谁的") || value.contains("哪个销售") || value.contains("哪位销售") || value.contains("哪个负责人") || value.contains("负责人") || value.contains("销售");
        boolean asksRanking = value.contains("最多") || value.contains("最高") || value.contains("最大") || value.contains("更多") || value.contains("汇总") || value.contains("统计") || value.contains("排行") || value.contains("排名");
        return mentionsOpportunity && asksOwner && asksRanking;
    }

    private boolean isStatusAmountAggregationQuestion(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        boolean hasStatusFilter = !requestedStatusFilters(value).isEmpty();
        boolean asksCountOrAmount = value.contains("多少")
            || value.contains("几个")
            || value.contains("几条")
            || value.contains("数量")
            || value.contains("金额")
            || value.contains("项目金额")
            || value.contains("合同额")
            || value.contains("收入")
            || value.contains("汇总")
            || value.contains("合计")
            || value.contains("总额");
        return hasStatusFilter && asksCountOrAmount;
    }

    private boolean isAmountAggregationQuestion(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        boolean mentionsAmount = value.contains("金额")
            || value.contains("合同额")
            || value.contains("收入")
            || value.contains("总额")
            || value.contains("多少钱")
            || value.contains("多少金额");
        boolean asksAggregation = value.contains("多少")
            || value.contains("多少钱")
            || value.contains("多少金额")
            || value.contains("合计")
            || value.contains("汇总")
            || value.contains("统计")
            || value.contains("一共")
            || value.contains("总共")
            || value.contains("共有")
            || value.contains("总额");
        return mentionsAmount && asksAggregation;
    }

    private RuntimeQuestionPlan planRuntimeQuestion(String content) {
        String value = compactQuestion(content);
        RuntimeMetric metric = detectMetric(value);
        RuntimeOperation operation = detectOperation(value, metric);
        RuntimeSort sort = operation == RuntimeOperation.RANKING ? RuntimeSort.DESC : RuntimeSort.NONE;
        return new RuntimeQuestionPlan(operation, metric, sort, requestedTopLimit(value));
    }

    private RuntimeMetric detectMetric(String value) {
        if (value.contains("金额")
            || value.contains("合同额")
            || value.contains("收入")
            || value.contains("总额")
            || value.contains("预计金额")
            || value.contains("商机金额")
            || value.contains("项目金额")
            || value.contains("多少钱")) {
            return RuntimeMetric.AMOUNT;
        }
        return RuntimeMetric.NONE;
    }

    private RuntimeOperation detectOperation(String value, RuntimeMetric metric) {
        if (metric != RuntimeMetric.NONE && asksRanking(value)) {
            return RuntimeOperation.RANKING;
        }
        if (metric != RuntimeMetric.NONE && asksAggregation(value)) {
            return RuntimeOperation.AGGREGATION;
        }
        return RuntimeOperation.DEFAULT;
    }

    private boolean isAmountRankingQuestion(String content) {
        RuntimeQuestionPlan plan = planRuntimeQuestion(content);
        return plan.operation() == RuntimeOperation.RANKING && plan.metric() == RuntimeMetric.AMOUNT;
    }

    private boolean asksRanking(String value) {
        return value.contains("比较高")
            || value.contains("较高")
            || value.contains("最高")
            || value.contains("最大")
            || value.contains("最多")
            || value.contains("排名")
            || value.contains("排行")
            || value.contains("排序")
            || value.contains("前几")
            || value.contains("前十")
            || value.contains("前10")
            || value.toLowerCase(java.util.Locale.ROOT).contains("top")
            || (value.contains("哪些") && (value.contains("高") || value.contains("大")))
            || (value.contains("哪个") && (value.contains("高") || value.contains("大")));
    }

    private boolean asksAggregation(String value) {
        return value.contains("多少")
            || value.contains("多少钱")
            || value.contains("多少金额")
            || value.contains("合计")
            || value.contains("汇总")
            || value.contains("统计")
            || value.contains("一共")
            || value.contains("总共")
            || value.contains("共有")
            || value.contains("总额");
    }

    private String compactQuestion(String content) {
        return content == null ? "" : content.replaceAll("\\s+", "");
    }

    private int requestedTopLimit(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:前|top|TOP)(\\d{1,2})").matcher(value);
        if (matcher.find()) {
            try {
                return Math.max(1, Math.min(20, Integer.parseInt(matcher.group(1))));
            } catch (NumberFormatException ignored) {
                return 10;
            }
        }
        if (value.contains("前十")) {
            return 10;
        }
        if (value.contains("前五")) {
            return 5;
        }
        if (value.contains("前三")) {
            return 3;
        }
        return 10;
    }

    private List<String> requestedStatusFilters(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        List<String> statuses = new java.util.ArrayList<>();
        boolean asksUnfinished = value.contains("未完成");
        boolean asksCompleted = value.contains("已完成") || value.contains("完成了") || value.contains("完成的") || value.contains("已结项") || value.contains("结项");
        if (value.contains("进行中") || value.contains("在建") || value.contains("执行中") || value.contains("实施中") || asksUnfinished) {
            statuses.add("进行中");
        }
        if (!asksUnfinished && asksCompleted) {
            statuses.add("已完成");
        }
        if (value.contains("暂停") || value.contains("搁置")) {
            statuses.add("暂停");
        }
        if (value.contains("终止") || value.contains("关闭") || value.contains("取消")) {
            statuses.add("终止");
        }
        return statuses.stream().distinct().toList();
    }

    private boolean statusMatches(String actualStatus, List<String> targetStatuses) {
        if (targetStatuses == null || targetStatuses.isEmpty()) {
            return true;
        }
        String actual = actualStatus == null ? "" : actualStatus.replaceAll("\\s+", "");
        for (String target : targetStatuses) {
            if ("进行中".equals(target) && (actual.contains("进行中") || actual.contains("在建") || actual.contains("执行中") || actual.contains("实施中") || actual.contains("未完成"))) {
                return true;
            }
            if ("已完成".equals(target) && !actual.contains("未完成") && (actual.contains("已完成") || actual.contains("完成") || actual.contains("已结项") || actual.contains("结项"))) {
                return true;
            }
            if ("暂停".equals(target) && (actual.contains("暂停") || actual.contains("搁置"))) {
                return true;
            }
            if ("终止".equals(target) && (actual.contains("终止") || actual.contains("关闭") || actual.contains("取消"))) {
                return true;
            }
            if (actual.equals(target) || (!"已完成".equals(target) && actual.contains(target))) {
                return true;
            }
        }
        return false;
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

    private boolean hasRecordOrdinalReference(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        return value.contains("第") && (value.contains("条") || value.contains("个") || value.contains("笔") || value.contains("单"));
    }

    private int queryPageSize(String content) {
        CloudPivotRuntimeProperties.Query query = runtimeProperties.getQuery();
        if (requestedOwnerFilter(content).present()) {
            return query.getOwnerFilterPageSize();
        }
        if (isSingleRecordDetailQuestion(content)) {
            return requestedRecordOrdinal(content);
        }
        if (isDetailCollectionQuestion(content)) {
            return query.getListPageSize();
        }
        if (isYearlyDistributionQuestion(content)) {
            return query.getYearlyPageSize();
        }
        if (isBroadBusinessAnalysisQuestion(content)) {
            return query.getBroadAnalysisPageSize();
        }
        if (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content) || isOwnerOpportunityRankingQuestion(content) || isStatusAmountAggregationQuestion(content) || isAmountRankingQuestion(content) || isAmountAggregationQuestion(content) || isAnalysisQuestion(content)) {
            return query.getAnalysisPageSize();
        }
        return isCountQuestion(content) ? query.getCountPageSize() : query.getListPageSize();
    }

    private int queryRecordLimit(String content) {
        CloudPivotRuntimeProperties.Query query = runtimeProperties.getQuery();
        if (requestedOwnerFilter(content).present()) {
            return requiresCompleteAggregation(content)
                ? query.getCompleteAggregationRecordLimit()
                : query.getOwnerFilterRecordLimit();
        }
        if (isSingleRecordDetailQuestion(content)) {
            return requestedRecordOrdinal(content);
        }
        if (isDetailCollectionQuestion(content)) {
            return query.getListRecordLimit();
        }
        if (isStatusAmountAggregationQuestion(content)) {
            return query.getCompleteAggregationRecordLimit();
        }
        if (isAmountAggregationQuestion(content)) {
            return query.getCompleteAggregationRecordLimit();
        }
        if (isAmountRankingQuestion(content)) {
            return query.getRankingRecordLimit();
        }
        if (isCountQuestion(content)) {
            return query.getCountRecordLimit();
        }
        if (isStageDistributionQuestion(content) || isProvinceDistributionQuestion(content) || isNewOldCustomerQuestion(content)) {
            return query.getDimensionRecordLimit();
        }
        if (isOwnerOpportunityRankingQuestion(content)) {
            return query.getOwnerRankingRecordLimit();
        }
        if (isYearlyDistributionQuestion(content)) {
            return query.getYearlyRecordLimit();
        }
        if (isBroadBusinessAnalysisQuestion(content)) {
            return query.getBroadAnalysisRecordLimit();
        }
        if (isAnalysisQuestion(content)) {
            return query.getAnalysisRecordLimit();
        }
        return query.getListRecordLimit();
    }

    private boolean requiresCompleteAggregation(String content) {
        return isStageDistributionQuestion(content)
            || isProvinceDistributionQuestion(content)
            || isNewOldCustomerQuestion(content)
            || isOwnerOpportunityRankingQuestion(content)
            || isStatusAmountAggregationQuestion(content)
            || isAmountRankingQuestion(content)
            || isAmountAggregationQuestion(content)
            || isYearlyDistributionQuestion(content)
            || isAnalysisQuestion(content);
    }

    private boolean requiresFullDimensionDetails(String content) {
        return isSingleRecordDetailQuestion(content)
            || isDetailCollectionQuestion(content)
            || isOwnerOpportunityRankingQuestion(content)
            || isStatusAmountAggregationQuestion(content)
            || isBroadBusinessAnalysisQuestion(content)
            || isPlainListQuestion(content);
    }

    private boolean isBroadBusinessAnalysisQuestion(String content) {
        return isAnalysisQuestion(content)
            && !isStageDistributionQuestion(content)
            && !isYearlyDistributionQuestion(content)
            && !isProvinceDistributionQuestion(content)
            && !isNewOldCustomerQuestion(content)
            && !isOwnerOpportunityRankingQuestion(content)
            && !isStatusAmountAggregationQuestion(content)
            && !isAmountRankingQuestion(content)
            && !isAmountAggregationQuestion(content);
    }

    private boolean isPlainListQuestion(String content) {
        return !isCountQuestion(content)
            && !isAnalysisQuestion(content)
            && !isSingleRecordDetailQuestion(content)
            && !isDetailCollectionQuestion(content);
    }

    private static final class OwnerOpportunityStats {
        private long count;
        private double amount;

        void add(double value) {
            count++;
            amount += value;
        }

        long count() {
            return count;
        }

        double amount() {
            return amount;
        }
    }

    private enum RuntimeOperation {
        DEFAULT,
        AGGREGATION,
        RANKING
    }

    private enum RuntimeMetric {
        NONE,
        AMOUNT
    }

    private enum RuntimeSort {
        NONE,
        DESC
    }

    private record RuntimeQuestionPlan(RuntimeOperation operation, RuntimeMetric metric, RuntimeSort sort, int limit) {
    }

    private record OwnerFilter(String name) {
        static OwnerFilter none() {
            return new OwnerFilter("");
        }

        boolean present() {
            return name != null && !name.isBlank();
        }
    }

    private record RankedRecord(Map<String, Object> record, Map<?, ?> data, Double amount) {
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuntimeAnswerDetail(String answer, String actionSummary, String conclusionSummary, long displayTotal, int displayReturnedRecords) {
        RuntimeAnswerDetail(String answer, String actionSummary, String conclusionSummary) {
            this(answer, actionSummary, conclusionSummary, -1, -1);
        }
    }

    public record RuntimeRecordTarget(String schemaCode, String bizObjectId) {
    }

    private static final class StatusAmountStats {
        private long count;
        private double amount;

        void add(double value) {
            count++;
            amount += value;
        }

        long count() {
            return count;
        }

        double amount() {
            return amount;
        }
    }
}
