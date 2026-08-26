package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotQueryAnswer;
import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy;
import com.cpclaw.cloudpivot.CloudPivotRuntimeProperties;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.credential.CredentialUnavailableException;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.skill.SkillQuestionSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Provider runtime containing only reusable execution primitives. It does not
 * interpret domain vocabulary, infer business fields, or select a scenario
 * report. Those concerns belong to an installed template plugin.
 */
@Service
public class YunshuRuntimeService {
    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String USER_CLOUDPIVOT_PASSWORD = "user_cloudpivot_password";

    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector connector;
    private final ModelGateway modelGateway;
    private final CloudPivotRecordDisplayPolicy displayPolicy;
    private final CloudPivotRuntimeProperties properties;
    private final SkillQuestionSemantics semantics;

    public YunshuRuntimeService(SystemSettingsRepository settingsRepository,
        CredentialService credentialService, CloudPivotConnector connector,
        ModelGateway modelGateway, CloudPivotRecordDisplayPolicy displayPolicy,
        CloudPivotRuntimeProperties properties, SkillQuestionSemantics semantics) {
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.connector = connector;
        this.modelGateway = modelGateway;
        this.displayPolicy = displayPolicy;
        this.properties = properties;
        this.semantics = semantics;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String question,
        String modelConfigId, boolean thinkingEnabled) {
        return query(match, question, modelConfigId, thinkingEnabled, List.of(), List.of(), Map.of(), AgentProgressListener.NOOP);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String question,
        String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters) {
        return query(match, question, modelConfigId, thinkingEnabled, filters, List.of(), Map.of(), AgentProgressListener.NOOP);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String question,
        String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters,
        List<String> metricFieldCodes) {
        return query(match, question, modelConfigId, thinkingEnabled, filters, metricFieldCodes, Map.of(), AgentProgressListener.NOOP);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String question,
        String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters,
        List<String> metricFieldCodes, Map<String, Object> reasoningContext,
        AgentProgressListener progressListener) {
        if (match == null || !"entity".equals(match.objectType()) || !hasText(match.code())) {
            throw new IllegalArgumentException("没有匹配到可查询的云枢数据对象");
        }
        AgentProgressListener progress = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码"));
        String password = password();
        CloudPivotRuntimeProperties.Query queryConfig = properties.getQuery();
        boolean count = semantics.isCountQuestion(question);
        boolean detail = semantics.isSingleRecordDetailQuestion(question) || semantics.isDetailCollectionQuestion(question);
        int pageSize = detail ? queryConfig.getListPageSize() : count ? queryConfig.getCountPageSize() : queryConfig.getAnalysisPageSize();
        int limit = detail ? queryConfig.getListRecordLimit() : count ? queryConfig.getCountRecordLimit() : queryConfig.getAnalysisRecordLimit();
        List<RuntimeQueryFilter> safeFilters = filters == null ? List.of() : filters.stream().filter(RuntimeQueryFilter::valid).toList();
        progress.onExecution("读取数据", "正在调用云枢数据接口", Map.of("entityName", match.name()), "running");
        CloudPivotRuntimeQueryResult result = connector.queryRecords(settings.getAdminCloudPivotBaseUrl(), settings.getCloudPivotUsername(), password,
            match.code(), pageSize, detail, limit, safeFilters);
        if (result == null) {
            throw new IllegalStateException("云枢接口未返回结果");
        }
        if ("local-fallback".equals(result.sourceEndpoint())) {
            throw new IllegalStateException("当前连接返回本地演示数据，不能回答真实云枢数据");
        }
        result = applyFilters(result, safeFilters);
        progress.onExecution("读取数据", "云枢数据读取完成", Map.of("total", result.total(), "returnedRecords", result.records().size()), "completed");

        String answer;
        String mode;
        if (count) {
            answer = "已查询“" + safeName(match) + "”，共 **" + result.total() + "** 条记录。";
            mode = "count";
        } else if (detail) {
            answer = renderRecords(match, result);
            mode = "record";
        } else {
            mode = "model";
            progress.onAnswerStart(mode);
            StringBuilder streamedAnswer = new StringBuilder();
            Optional<String> generated = modelGateway.analyzeRecordsStream(modelConfigId, question, safeName(match), result.total(), result.records(), thinkingEnabled, reasoningContext,
                streamedAnswer::append);
            CloudPivotRuntimeQueryResult completedResult = result;
            answer = generated.filter(this::hasText).orElseGet(() -> renderRecords(match, completedResult));
            if (generated.isPresent() && streamedAnswer.length() > 0) {
                answer = streamedAnswer.toString();
            }
        }
        if (!"model".equals(mode)) {
            progress.onAnswerStart(mode);
            AnswerStreamSupport.emitReadableChunks(answer, progress::onAnswerChunk);
            progress.onAnswerComplete(mode);
        }
        answer = sanitizeAnswer(answer, match.code());
        if ("model".equals(mode)) {
            AnswerStreamSupport.emitReadableChunks(answer, progress::onAnswerChunk);
            progress.onAnswerComplete(mode);
        }
        String conclusion = "已基于云枢对象“" + safeName(match) + "”的已核验返回数据生成结果";
        return new CloudPivotQueryAnswer(match.name(), match.code(), result.total(), result.records().size(), answer,
            result.sourceEndpoint(), "通用数据读取与结果呈现", rawSummary(result), conclusion,
            detail ? List.copyOf(result.records()) : List.of());
    }

    public RuntimeRecordTarget resolveRecordTarget(MetadataSearchResult match, String question) {
        if (match == null || !hasText(match.code())) throw new IllegalArgumentException("没有匹配到可操作的数据对象");
        String explicit = explicitId(question);
        if (hasText(explicit)) return new RuntimeRecordTarget(match.code(), explicit);
        int ordinal = semantics.requestedRecordOrdinal(question);
        if (ordinal < 1) throw new IllegalArgumentException("请指定记录ID或记录序号");
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID).orElseThrow(() -> new IllegalArgumentException("请先配置云枢连接"));
        CloudPivotRuntimeQueryResult result = connector.queryRecords(settings.getAdminCloudPivotBaseUrl(), settings.getCloudPivotUsername(), password(), match.code(), ordinal, false, ordinal);
        if (result.records().size() < ordinal) throw new IllegalArgumentException("未查询到指定记录");
        Object id = recordData(result.records().get(ordinal - 1)).get("id");
        if (id == null) id = result.records().get(ordinal - 1).get("id");
        if (id == null) throw new IllegalArgumentException("返回记录缺少可操作ID");
        return new RuntimeRecordTarget(match.code(), String.valueOf(id));
    }

    private String password() {
        try { return credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, USER_CLOUDPIVOT_PASSWORD).orElseThrow(() -> new IllegalArgumentException("请先填写云枢登录密码")); }
        catch (CredentialUnavailableException e) { throw new IllegalArgumentException(e.getMessage(), e); }
    }

    private CloudPivotRuntimeQueryResult applyFilters(CloudPivotRuntimeQueryResult result, List<RuntimeQueryFilter> filters) {
        if (filters.isEmpty()) return result;
        List<Map<String, Object>> records = result.records().stream().filter(record -> filters.stream().allMatch(filter -> matches(record, filter))).toList();
        return new CloudPivotRuntimeQueryResult(result.schemaCode(), records.size(), records, result.sourceEndpoint());
    }

    private boolean matches(Map<String, Object> record, RuntimeQueryFilter filter) {
        Object raw = recordData(record).get(filter.fieldCode());
        if (raw == null) raw = record.get(filter.fieldCode());
        String actual = raw == null ? "" : String.valueOf(raw);
        String expected = filter.value();
        return "=".equals(filter.operator()) ? actual.equalsIgnoreCase(expected) : actual.toLowerCase().contains(expected.toLowerCase());
    }

    private String renderRecords(MetadataSearchResult match, CloudPivotRuntimeQueryResult result) {
        if (result.records().isEmpty()) return "未查询到“" + safeName(match) + "”的记录。";
        var context = displayPolicy.context(result.schemaCode());
        StringBuilder out = new StringBuilder("“").append(safeName(match)).append("”共返回 ").append(result.records().size()).append(" 条记录：");
        int index = 1;
        for (Map<String, Object> record : result.records()) {
            out.append("\n").append(index++).append(". ").append(displayPolicy.summarize(context, recordData(record)));
            if (index > 11) break;
        }
        return out.toString();
    }

    private String rawSummary(CloudPivotRuntimeQueryResult result) {
        if (result.records().isEmpty()) return "未返回记录明细";
        var context = displayPolicy.context(result.schemaCode());
        return result.records().stream().limit(3).map(r -> displayPolicy.summarize(context, recordData(r))).reduce((a, b) -> a + "；" + b).orElse("未返回记录明细");
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> recordData(Map<String, Object> record) {
        Object data = record == null ? null : record.get("data");
        return data instanceof Map<?, ?> map ? map : (record == null ? Map.of() : record);
    }

    private String explicitId(String question) {
        if (question == null) return "";
        var matcher = java.util.regex.Pattern.compile("(?:记录ID|业务对象ID|bizObjectId|id)\\s*(?:为|是|=|：|:)\\s*([A-Za-z0-9_-]+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(question);
        return matcher.find() ? matcher.group(1) : "";
    }
    private String safeName(MetadataSearchResult match) { return hasText(match.name()) ? match.name() : "数据对象"; }
    private String sanitizeAnswer(String answer, String schemaCode) {
        if (!hasText(answer) || !hasText(schemaCode)) return answer;
        return answer.replace(schemaCode, "").replace("schemaCode=", "");
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    public record RuntimeRecordTarget(String schemaCode, String bizObjectId) { }
}
