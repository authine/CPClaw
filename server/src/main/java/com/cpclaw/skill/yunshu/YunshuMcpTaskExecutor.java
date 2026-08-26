package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.MetadataExecutionPlanner;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.WorkflowReadResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.mcp.McpTaskExperienceRenderer;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.insight.InsightDataAccess;
import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.skill.yunshu.YunshuInsightReportService;
import com.cpclaw.skill.yunshu.runtime.YunshuProvider;
import com.cpclaw.skill.yunshu.runtime.YunshuSkillRuntime;
import com.cpclaw.skill.yunshu.runtime.YunshuIntentPlanner;
import com.cpclaw.skill.yunshu.runtime.YunshuMetadataDiscovery;
import com.cpclaw.skill.yunshu.runtime.YunshuDiscovery;
import com.cpclaw.skill.yunshu.runtime.YunshuExecutionScope;
import com.cpclaw.skill.yunshu.runtime.YunshuPlanValidator;
import com.cpclaw.skill.yunshu.runtime.YunshuPlanValidation;
import com.cpclaw.skill.yunshu.runtime.YunshuEvidenceComposer;
import com.cpclaw.skill.yunshu.runtime.YunshuCompletionValidator;
import com.cpclaw.skill.yunshu.runtime.YunshuRuntimePhase;
import com.cpclaw.skill.yunshu.runtime.YunshuResultComposer;
import com.cpclaw.model.IntentPlanningResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.model.ConversationRouteResult;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.skill.SkillCatalog;
import com.cpclaw.skill.SkillExecutionContext;
import com.cpclaw.skill.SkillExecutor;
import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.task.dto.TaskSpec;
import com.cpclaw.workflow.WorkflowCenterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Service;

/** Natural-language MCP entry point. It exposes safe task facts, never raw model reasoning. */
@Service
public class YunshuMcpTaskExecutor implements SkillExecutor, YunshuSkillRuntime {
    private final YunshuMetadataDiscovery metadataDiscovery;
    private final YunshuProvider yunshuProvider;
    private final YunshuIntentPlanner intentPlanner;
    private final YunshuPlanValidator planValidator;
    private final YunshuEvidenceComposer evidenceComposer;
    private final YunshuCompletionValidator completionValidator;
    private final YunshuResultComposer resultComposer;
    private final WorkflowCenterService workflowCenterService;
    private final CloudPivotRecordDisplayPolicy recordDisplayPolicy;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final YunshuInsightReportService insightReportService;
    private final McpTaskExperienceRenderer experienceRenderer;

    public YunshuMcpTaskExecutor(
        YunshuMetadataDiscovery metadataDiscovery,
        YunshuProvider yunshuProvider,
        YunshuIntentPlanner intentPlanner,
        YunshuPlanValidator planValidator,
        YunshuEvidenceComposer evidenceComposer,
        YunshuCompletionValidator completionValidator,
        YunshuResultComposer resultComposer,
        WorkflowCenterService workflowCenterService,
        CloudPivotRecordDisplayPolicy recordDisplayPolicy,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        YunshuInsightReportService insightReportService,
        McpTaskExperienceRenderer experienceRenderer
    ) {
        this.metadataDiscovery = metadataDiscovery;
        this.yunshuProvider = yunshuProvider;
        this.intentPlanner = intentPlanner;
        this.planValidator = planValidator;
        this.evidenceComposer = evidenceComposer;
        this.completionValidator = completionValidator;
        this.resultComposer = resultComposer;
        this.workflowCenterService = workflowCenterService;
        this.recordDisplayPolicy = recordDisplayPolicy;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.insightReportService = insightReportService;
        this.experienceRenderer = experienceRenderer;
    }

    @Override
    public String skillId() {
        return SkillCatalog.YUNSHU_BUSINESS_SYSTEM;
    }

    @Override
    public Map<String, Object> execute(SemanticTaskRequest request, SkillExecutionContext context, String taskId, Consumer<TaskProgressEvent> progress) {
        // A conversation can complete safely without a CloudPivot connection.
        // Keep that decision inside the shared Runtime rather than having the
        // Web adapter retain a separate pre-execution path.
        BoundCloudPivotConnection connection = context.find("cloudPivotConnection", BoundCloudPivotConnection.class)
            .orElse(new BoundCloudPivotConnection("", "", "", ""));
        return handleInternal(taskId, connection, request.userGoal(), request.context(), request.taskSpec(), progress);
    }

    /** Shared-runtime entry point used by non-MCP adapters. */
    @Override
    public Map<String, Object> execute(SemanticTaskRequest request, YunshuExecutionScope scope,
            String taskId, Consumer<TaskProgressEvent> progress) {
        return execute(request, new SkillExecutionContext(Map.of("cloudPivotConnection", scope.connection())), taskId, progress);
    }

    private Map<String, Object> handleInternal(
        String taskId,
        BoundCloudPivotConnection connection,
        String request,
        List<String> context,
        TaskSpec taskSpec,
        Consumer<TaskProgressEvent> progressConsumer
    ) {
        List<Map<String, Object>> trace = new ArrayList<>();
        Consumer<TaskProgressEvent> progress = progressConsumer == null ? ignored -> { } : progressConsumer;
        emit(progress, trace, taskId, YunshuRuntimePhase.UNDERSTAND, 5, "理解业务目标", "正在结合请求、上下文和已同步元数据判断处理方式。", "running");
        String query = normalize(taskSpec.goal().isBlank() ? request : taskSpec.goal());
        if (!taskSpec.constraints().isEmpty()) query = query + " " + taskSpec.constraints().entrySet().stream().limit(8).map(entry -> entry.getKey() + ":" + entry.getValue()).reduce("", (left, right) -> left + " " + right);
        if (query.isBlank()) {
            return finish(taskId, "clarification_required", "请描述希望查询、分析或处理的业务目标。", Map.of(), trace, progress, 100, "请求内容为空，无法开始业务对象定位。");
        }
        if (isGenericConversation(query)) {
            return finishGenericConversation(taskId, query, context, trace, progress);
        }
        Optional<ConversationRouteResult> routed = routeConversation(query, context);
        if (routed.isPresent() && routed.get().isConversation()) {
            String answer = routed.get().answer();
            emit(progress, trace, taskId, YunshuRuntimePhase.COMPLETE, 80, "完成对话回应", "当前内容不需要访问云枢业务 Skill。", "completed");
            return finish(taskId, "completed", answer, Map.of("answer", answer, "mode", "conversation", "intent", "conversation"), trace, progress, 100, "已直接完成通用对话回应。");
        }
        String intent = intentPlanner.classify(query);
        YunshuDiscovery discovery = discoverWithContext(query, context);
        MetadataSearchResult match = discovery.match();
        MetadataExecutionPlanner.MetadataExecutionPlan plan = discovery.plan();
        OptionalModelPlan modelPlan = modelPlan(query, context, match, plan);
        if (modelPlan.usable() && intentPlanner.isRead(intent)) {
            String plannedIntent = intentPlanner.normalizeModelIntent(modelPlan.value().intent(), intent);
            // A model may omit the analytical aspect of a short follow-up
            // such as “都处于什么阶段？”. Preserve the deterministic
            // operation-shape signal when it is more specific.
            if (!("analyze_data".equals(intent) && "query_data".equals(plannedIntent))) {
                intent = plannedIntent;
            }
        }
        emit(progress, trace, taskId, YunshuRuntimePhase.DISCOVER, 25, "匹配业务元数据", discovery.summary(), "completed");
        Map<String, Object> matched = metadata(match);

        // Workflow read contracts are resolved by the workflow capability
        // registry rather than an arbitrary data-object match. They still pass
        // through the same Runtime lifecycle and provider boundary, but do not
        // require a business entity candidate before a verified read contract
        // can be queried.
        if (intentPlanner.isWorkflowAction(intent)) {
            return workflowActionUnavailable(taskId, trace, progress);
        }
        if (intentPlanner.isWorkflow(intent)) {
            return queryWorkflow(taskId, connection, query, matched, trace, progress);
        }

        if ("clarify_intent".equals(intent) || !isUsableMatch(match)) {
            String message = !isUsableMatch(match)
                ? "暂未从已同步的真实云枢元数据中唯一定位业务对象，不会用本地演示数据返回业务结果；请补充应用名、业务对象名或更具体的查询目标。"
                : "当前请求需要补充业务对象或处理范围后才能安全执行。";
            return finish(taskId, "clarification_required", message, Map.of("question", message, "intent", intent), trace, progress, 100, "未执行云枢调用。");
        }

        YunshuPlanValidation validation = planValidator.validate(
            plan, intent, YunshuExecutionScope.readOnly(connection, "mcp", "")
        );
        if ("blocked".equals(validation.state())) {
            return finish(taskId, "blocked", "当前请求未通过云枢执行计划校验。", Map.of("validationReasons", validation.reasons(), "matchedMetadata", matched, "intent", intent), trace, progress, 100, "执行计划校验未通过，未调用云枢写接口。");
        }
        if (validation.requiresConfirmation()) {
            return finishWithConfirmation(taskId, intent, matched, trace, progress, query);
        }

        if (intentPlanner.isWrite(intent)) {
            return finishWithConfirmation(taskId, intent, matched, trace, progress, query);
        }
        if (!intentPlanner.isRead(intent)) {
            return finish(taskId, "clarification_required", "请说明要查询、分析还是执行业务操作。", Map.of("question", "请补充希望完成的动作。", "intent", intent), trace, progress, 100, "动作类型不明确，未执行云枢调用。");
        }
        if (intentPlanner.isAnalysis(intent) && insightReportService.supports(query, intent)) {
            return executeInsight(taskId, connection, query, match, taskSpec, trace, progress);
        }
        emit(progress, trace, taskId, YunshuRuntimePhase.PLAN, 45, "确定读取范围", "已根据元数据确定业务对象和可用读取能力。", "completed");
        try {
            int pageSize = intentPlanner.isAnalysis(intent) ? 50 : 20;
            CloudPivotRuntimeQueryResult result = yunshuProvider.query(connection, match.code(), pageSize, true, pageSize);
            if (result == null) {
                result = new CloudPivotRuntimeQueryResult(match.code(), 0, List.of(), "");
            }
            emit(progress, trace, taskId, YunshuRuntimePhase.EXECUTE, 75, "读取云枢数据", "已从绑定账号可访问的云枢业务对象读取数据。", "completed");
            boolean analysisMode = intentPlanner.isAnalysis(intent);
            Map<String, Object> data = resultComposer.composeQuery(match, result, recordDisplayPolicy, analysisMode);
            int returned = result.records().size();
            String answer = analysisMode
                ? "已根据“" + match.name() + "”的真实数据生成分析基础结果，共 " + result.total() + " 条记录。"
                : resultComposer.answer(match, result, returned);
            return finish(taskId, "completed", answer, Map.of(
                "answer", answer,
                "result", data,
                "matchedMetadata", matched,
                "intent", analysisMode ? "analyze_data" : "query_data"
            ), trace, progress, 100, "结果已核验，未返回原始敏感字段。");
        } catch (RuntimeException exception) {
            return finish(taskId, "failed", safeMessage(exception), Map.of("error", safeMessage(exception)), trace, progress, 100, "云枢读取失败，已停止后续处理。");
        }
    }

    private Map<String, Object> executeInsight(
        String taskId,
        BoundCloudPivotConnection connection,
        String query,
        MetadataSearchResult match,
        TaskSpec taskSpec,
        List<Map<String, Object>> trace,
        Consumer<TaskProgressEvent> progress
    ) {
        emit(progress, trace, taskId, YunshuRuntimePhase.ANALYZE, 40, "规划数据洞察", "已验证主业务对象，正在按已同步字段、关联关系和数据口径生成分析方案。", "running");
        try {
            AgentProgressListener insightProgress = new AgentProgressListener() {
                @Override
                public void onProgress(String title, String status) {
                    emit(progress, trace, taskId, 60, title, status, "running");
                }

                @Override
                public void onThought(String phase, String title, String status, String state) {
                    emit(progress, trace, taskId, 55, title, status, state);
                }

                @Override
                public void onExecution(String title, String status, Map<String, Object> ignored, String state) {
                    emit(progress, trace, taskId, 70, title, status, state);
                }
            };
            InsightExecutionResult execution = insightReportService.execute(
                match,
                query,
                null,
                true,
                insightProgress,
                new InsightDataAccess(connection.baseUrl(), connection.username(), connection.password())
            );
            McpTaskExperienceRenderer.RenderedInsight rendered = experienceRenderer.renderInsight(execution);
            emit(progress, trace, taskId, YunshuRuntimePhase.ANALYZE, 92, "生成业务分析结果", "已生成指标、图表数据、结论和数据口径提示。", "completed");
            return finish(
                taskId,
                "completed",
                rendered.markdown(),
                Map.of(
                    "answer", rendered.markdown(),
                    "intent", "analyze_data",
                    "matchedMetadata", metadata(match),
                    "artifact", rendered.artifact(),
                    "dataRange", Map.of("scope", execution.report().scopeLabel(), "period", execution.report().periodLabel()),
                    "followUps", execution.report().relatedQuestions()
                    , "evidence", evidenceComposer.compose(execution.evidence(), execution.report().scopeLabel(), "yunshu-skill-runtime")
                    , "completion", completionValidator.validate(taskSpec,
                        evidenceComposer.compose(execution.evidence(), execution.report().scopeLabel(), "yunshu-skill-runtime"), "completed")
                ),
                trace,
                progress,
                100,
                "数据洞察任务已根据已验证数据完成。"
            );
        } catch (RuntimeException exception) {
            return finish(taskId, "failed", safeMessage(exception), Map.of("error", safeMessage(exception)), trace, progress, 100, "数据洞察执行失败，未输出未经核验的结论。");
        }
    }

    private Optional<ConversationRouteResult> routeConversation(String query, List<String> context) {
        try {
            return modelGateway.routeConversation(null, query, context == null ? List.of() : context, true);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves follow-up references using the existing conversation context.
     * It only reuses synchronised metadata matches; it does not infer object
     * identifiers or field aliases from arbitrary text.
     */
    private YunshuDiscovery discoverWithContext(String query, List<String> context) {
        YunshuDiscovery current = metadataDiscovery.discover(query);
        if (current.executable() || context == null || context.isEmpty()) return current;
        for (int index = context.size() - 1; index >= 0; index--) {
            String item = context.get(index);
            if (item == null || item.isBlank()) continue;
            YunshuDiscovery prior = metadataDiscovery.discover(item);
            if (prior.executable()) return prior;
        }
        return current;
    }

    /** Generic host-level conversation guard; it contains no domain vocabulary. */
    private boolean isGenericConversation(String query) {
        String value = normalize(query);
        return value.matches("(?s).*(^|\\b)(hi|hello|hey)(\\b|$).*")
            || startsWithAny(value, "你好", "您好", "嗨", "在吗", "早上好", "下午好", "晚上好")
            || value.contains("天气")
            || value.contains("怎么称呼")
            || value.contains("你是谁")
            || value.contains("谢谢")
            || value.contains("设计")
            || value.contains("架构方案")
            || value.contains("说明文档")
            || value.contains("大纲");
    }

    private Map<String, Object> finishGenericConversation(
        String taskId,
        String query,
        List<String> context,
        List<Map<String, Object>> trace,
        Consumer<TaskProgressEvent> progress
    ) {
        String answer = modelGateway.answerGeneralConversation(null, query, context == null ? List.of() : context, true)
            .orElseGet(() -> genericConversationFallback(query));
        emit(progress, trace, taskId, YunshuRuntimePhase.COMPLETE, 80, "完成对话回应", "当前内容不需要访问云枢业务 Skill。", "completed");
        return finish(taskId, "completed", answer,
            Map.of("answer", answer, "mode", "conversation", "intent", "conversation"),
            trace, progress, 100, "已按通用对话模式直接回答，未调用云枢业务能力。");
    }

    private String genericConversationFallback(String query) {
        String value = normalize(query);
        if (value.contains("天气")) return "我暂时无法获取实时天气，但可以继续帮你处理已接入系统中的数据任务。";
        if (value.contains("谢谢")) return "不客气，有需要继续告诉我即可。";
        if (value.contains("你是谁") || value.contains("怎么称呼")) return "我是 CPClaw，可以通过已接入的 Skill 协助你完成系统查询、分析和受控操作。";
        return "这是一个通用问题，我会直接回答，不调用云枢业务系统。请继续告诉我你想了解的内容。";
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) if (value.startsWith(prefix)) return true;
        return false;
    }

    private Map<String, Object> queryWorkflow(String taskId, BoundCloudPivotConnection connection, String query, Map<String, Object> matched, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress) {
        String apiCode = intentPlanner.workflowApiCode(query);
        emit(progress, trace, taskId, YunshuRuntimePhase.EXECUTE, 55, "查询流程中心", "正在读取当前账号可见的流程数据。", "running");
        try {
            WorkflowReadResult result = workflowCenterService.queryForBinding(apiCode, 20, connection.baseUrl(), connection.username(), connection.password());
            Map<String, Object> data = Map.of("cardType", "workflow-list", "total", result.total(), "items", result.items());
            emit(progress, trace, taskId, YunshuRuntimePhase.VALIDATE, 85, "核验流程结果", "流程数据读取完成，已按只读契约核验返回范围。", "completed");
            String answer = "已查询流程中心，共 **" + result.total() + "** 条记录。";
            return finish(taskId, "completed", answer, Map.of("answer", answer, "result", data, "matchedMetadata", matched, "intent", "query_workflow"), trace, progress, 100, "流程只读结果已核验。");
        } catch (RuntimeException exception) {
            return finish(taskId, "failed", safeMessage(exception), Map.of("error", safeMessage(exception)), trace, progress, 100, "流程只读契约不可用或本次读取失败。");
        }
    }

    private Map<String, Object> workflowActionUnavailable(String taskId, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress) {
        String message = "当前流程处理能力尚未通过真实云枢写入契约验证，本轮不会执行任何流程写操作。";
        emit(progress, trace, taskId, YunshuRuntimePhase.VALIDATE, 80, "核对流程处理能力", message, "completed_with_gaps");
        return finish(taskId, "completed_with_gaps", message,
            Map.of("answer", message, "intent", "workflow_action", "workflowActionAvailable", false),
            trace, progress, 100, "流程处理保持禁用，未调用云枢写接口。");
    }

    private Map<String, Object> finishWithConfirmation(String taskId, String intent, Map<String, Object> matched, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress, String query) {
        String message = "识别到需要写入或处理业务数据的请求。请回到 CPClaw 确认影响范围后执行；当前 MCP 不会自行确认写操作。";
        emit(progress, trace, taskId, YunshuRuntimePhase.VALIDATE, 80, "生成确认计划", message, "needs_input");
        return finish(taskId, "confirmation_required", message, Map.of("intent", intent, "confirmationRequired", true, "requestSummary", limit(query, 240), "matchedMetadata", matched), trace, progress, 100, "写操作已被确认门禁拦截。");
    }

    private Map<String, Object> finish(String taskId, String status, String answer, Map<String, Object> payload, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress, int percent, String finalMessage) {
        emit(progress, trace, taskId, YunshuRuntimePhase.COMPLETE, percent, "完成任务核验", finalMessage, "completed".equals(status) ? "completed" : status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", status);
        result.put("understandingSummary", answer);
        result.put("executionTrace", List.copyOf(trace));
        result.putAll(payload);
        return result;
    }

    private void emit(Consumer<TaskProgressEvent> consumer, List<Map<String, Object>> trace, String taskId, int percent, String title, String message, String state) {
        emit(consumer, trace, taskId, YunshuRuntimePhase.EXECUTE, percent, title, message, state);
    }

    private void emit(Consumer<TaskProgressEvent> consumer, List<Map<String, Object>> trace, String taskId, YunshuRuntimePhase phase, int percent, String title, String message, String state) {
        TaskProgressEvent event = new TaskProgressEvent(percent, "execution", title, message, state);
        trace.add(Map.of("phase", "execution", "runtimePhase", phase.name(), "percent", percent, "title", title, "message", message, "state", state));
        consumer.accept(event);
    }

    private Map<String, Object> metadata(MetadataSearchResult match) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objectType", match == null ? "" : safe(match.objectType()));
        result.put("objectName", match == null ? "" : safe(match.name()));
        result.put("matchStatus", isUsableMatch(match) ? "matched" : "needs_clarification");
        return result;
    }

    private OptionalModelPlan modelPlan(String query, List<String> context, MetadataSearchResult match, MetadataExecutionPlanner.MetadataExecutionPlan plan) {
        try {
            Map<String, Object> planningContext = new LinkedHashMap<>();
            planningContext.put("userGoal", query);
            planningContext.put("context", context == null ? List.of() : context.stream().limit(6).map(value -> limit(value, 400)).toList());
            planningContext.put("metadata", metadata(match));
            return new OptionalModelPlan(modelGateway.planIntent(null, planningContext, true).orElse(IntentPlanningResult.empty()));
        } catch (RuntimeException ignored) {
            return OptionalModelPlan.empty();
        }
    }

    private boolean isUsableMatch(MetadataSearchResult match) { return match != null && "entity".equals(match.objectType()) && !safe(match.code()).isBlank(); }
    private String safeMessage(RuntimeException exception) { return limit(exception == null ? "云枢任务执行失败" : (exception.getMessage() == null ? "云枢任务执行失败" : exception.getMessage()), 300); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private String safe(String value) { return value == null ? "" : value; }
    private String limit(String value, int max) { String safeValue = value == null ? "" : value; return safeValue.length() <= max ? safeValue : safeValue.substring(0, max) + "..."; }

    private String scopedRequestId(String conversationId, String clientRequestId) {
        String requestId = clientRequestId == null ? "" : clientRequestId.trim();
        if (requestId.isBlank()) return "";
        String conversation = conversationId == null ? "" : conversationId.trim();
        return conversation.isBlank() ? requestId : limit(conversation, 80) + ":" + limit(requestId, 47);
    }

    private String scopedTurnId(String conversationId, String turnId) {
        String value = turnId == null ? "" : turnId.trim();
        if (value.isBlank()) return "";
        String conversation = conversationId == null ? "" : conversationId.trim();
        return conversation.isBlank() ? value : limit(conversation, 80) + ":" + limit(value, 47);
    }

    private String principalFingerprint(BoundCloudPivotConnection connection) {
        String material = connection.baseUrl() + "\n" + connection.username() + "\n" + connection.password();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("mcp-");
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法建立 MCP 调用主体隔离", exception);
        }
    }

    private record OptionalModelPlan(IntentPlanningResult value) { boolean usable() { return value != null && value.confidence() >= 0.65 && !safeStatic(value.intent()).isBlank(); } static OptionalModelPlan empty() { return new OptionalModelPlan(IntentPlanningResult.empty()); } private static String safeStatic(String value) { return value == null ? "" : value; } }
}
