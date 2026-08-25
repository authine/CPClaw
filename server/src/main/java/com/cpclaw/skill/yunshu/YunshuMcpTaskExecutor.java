package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.MetadataExecutionPlanner;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.WorkflowReadResult;
import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.mcp.McpTaskExperienceRenderer;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.insight.InsightDataAccess;
import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.skill.yunshu.YunshuInsightReportService;
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
public class YunshuMcpTaskExecutor implements SkillExecutor {
    private final MetadataSearchService metadataSearchService;
    private final MetadataExecutionPlanner metadataExecutionPlanner;
    private final CloudPivotConnector cloudPivotConnector;
    private final WorkflowCenterService workflowCenterService;
    private final CloudPivotRecordDisplayPolicy recordDisplayPolicy;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final YunshuInsightReportService insightReportService;
    private final McpTaskExperienceRenderer experienceRenderer;

    public YunshuMcpTaskExecutor(
        MetadataSearchService metadataSearchService,
        MetadataExecutionPlanner metadataExecutionPlanner,
        CloudPivotConnector cloudPivotConnector,
        WorkflowCenterService workflowCenterService,
        CloudPivotRecordDisplayPolicy recordDisplayPolicy,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        YunshuInsightReportService insightReportService,
        McpTaskExperienceRenderer experienceRenderer
    ) {
        this.metadataSearchService = metadataSearchService;
        this.metadataExecutionPlanner = metadataExecutionPlanner;
        this.cloudPivotConnector = cloudPivotConnector;
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
        BoundCloudPivotConnection connection = context.require("cloudPivotConnection", BoundCloudPivotConnection.class);
        return handleInternal(taskId, connection, request.userGoal(), request.context(), request.taskSpec(), progress);
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
        emit(progress, trace, taskId, 5, "理解业务目标", "正在结合请求、上下文和已同步元数据判断处理方式。", "running");
        String query = normalize(taskSpec.goal().isBlank() ? request : taskSpec.goal());
        if (!taskSpec.constraints().isEmpty()) query = query + " " + taskSpec.constraints().entrySet().stream().limit(8).map(entry -> entry.getKey() + ":" + entry.getValue()).reduce("", (left, right) -> left + " " + right);
        if (query.isBlank()) {
            return finish(taskId, "clarification_required", "请描述希望查询、分析或处理的业务目标。", Map.of(), trace, progress, 100, "请求内容为空，无法开始业务对象定位。");
        }
        Optional<ConversationRouteResult> routed = routeConversation(query, context);
        if (routed.isPresent() && routed.get().isConversation()) {
            String answer = routed.get().answer();
            emit(progress, trace, taskId, 80, "完成对话回应", "当前内容不需要访问云枢业务 Skill。", "completed");
            return finish(taskId, "completed", answer, Map.of("answer", answer, "mode", "conversation"), trace, progress, 100, "已直接完成通用对话回应。");
        }
        String intent = classify(query);
        MetadataSearchResult match = metadataSearchService.bestMatch(query);
        MetadataExecutionPlanner.MetadataExecutionPlan plan = metadataExecutionPlanner.plan(query, match);
        OptionalModelPlan modelPlan = modelPlan(query, context, match, plan);
        if (modelPlan.usable() && isReadIntent(intent)) {
            intent = normalizeIntent(modelPlan.value().intent(), intent);
        }
        emit(progress, trace, taskId, 25, "匹配业务元数据", metadataSummary(match, plan), "completed");
        Map<String, Object> matched = metadata(match);

        if (isWriteIntent(intent)) {
            return finishWithConfirmation(taskId, intent, matched, trace, progress, query);
        }
        if ("clarify_intent".equals(intent) || !isUsableMatch(match)) {
            String message = !isUsableMatch(match)
                ? "暂未从已同步元数据中唯一定位业务对象，请补充应用名、业务对象名或更具体的查询目标。"
                : "当前请求需要补充业务对象或处理范围后才能安全执行。";
            return finish(taskId, "clarification_required", message, Map.of("question", message), trace, progress, 100, "未执行云枢调用。");
        }
        if (isWorkflowIntent(intent)) {
            return queryWorkflow(taskId, connection, query, matched, trace, progress);
        }
        if (!isReadIntent(intent)) {
            return finish(taskId, "clarification_required", "请说明要查询、分析还是执行业务操作。", Map.of("question", "请补充希望完成的动作。"), trace, progress, 100, "动作类型不明确，未执行云枢调用。");
        }
        if (isAnalysisIntent(intent) && insightReportService.supports(query, intent)) {
            return executeInsight(taskId, connection, query, match, taskSpec, trace, progress);
        }
        emit(progress, trace, taskId, 45, "确定读取范围", "已根据元数据确定业务对象和可用读取能力。", "completed");
        try {
            int pageSize = isAnalysisIntent(intent) ? 50 : 20;
            CloudPivotRuntimeQueryResult result = cloudPivotConnector.queryRecords(connection.baseUrl(), connection.username(), connection.password(), match.code(), pageSize, true, pageSize);
            emit(progress, trace, taskId, 75, "读取云枢数据", "已从绑定账号可访问的云枢业务对象读取数据。", "completed");
            CloudPivotRecordDisplayPolicy.DisplayContext displayContext = recordDisplayPolicy.context(match.code());
            List<Map<String, Object>> cards = result.records().stream()
                .map(record -> Map.<String, Object>of("summary", recordDisplayPolicy.summarize(displayContext, record)))
                .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cardType", isAnalysisIntent(intent) ? "analysis-data" : "data-table");
            data.put("entityName", match.name());
            data.put("total", result.total());
            data.put("returned", cards.size());
            data.put("records", cards);
            String answer = isAnalysisIntent(intent)
                ? "已根据“" + match.name() + "”的真实数据生成分析基础结果，共 " + result.total() + " 条记录。"
                : "已查询“" + match.name() + "”，共 " + result.total() + " 条记录，本次返回 " + cards.size() + " 条。";
            return finish(taskId, "completed", answer, Map.of("answer", answer, "result", data), trace, progress, 100, "结果已核验，未返回原始敏感字段。");
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
        emit(progress, trace, taskId, 40, "规划数据洞察", "已验证主业务对象，正在按已同步字段、关联关系和数据口径生成分析方案。", "running");
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
            emit(progress, trace, taskId, 92, "生成业务分析结果", "已生成指标、图表数据、结论和数据口径提示。", "completed");
            return finish(
                taskId,
                "completed",
                rendered.markdown(),
                Map.of(
                    "answer", rendered.markdown(),
                    "artifact", rendered.artifact(),
                    "dataRange", Map.of("scope", execution.report().scopeLabel(), "period", execution.report().periodLabel()),
                    "followUps", execution.report().relatedQuestions()
                    , "evidence", execution.evidence()
                    , "completion", completion(execution, taskSpec)
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

    private Map<String, Object> completion(InsightExecutionResult execution, TaskSpec spec) {
        if (spec == null || spec.deliverables().isEmpty()) return Map.of();
        Map<String, String> states = new LinkedHashMap<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        Map<String, Object> evidence = execution.evidence();
        for (var deliverable : spec.deliverables()) {
            String id = deliverable.id();
            boolean fulfilled = switch (id) {
                case "overall_summary" -> !execution.report().kpis().isEmpty();
                case "risk_findings" -> !list(evidence.get("riskSignals")).isEmpty();
                case "high_risk_owner_map" -> !list(evidence.get("highRiskOwnerMap")).isEmpty();
                case "scope_and_caveats" -> evidence.containsKey("scope");
                default -> evidence.containsKey(id);
            };
            states.put(id, fulfilled ? "fulfilled" : "missing");
            if (!fulfilled && deliverable.required()) missing.add(Map.of("deliverableId", id, "reason", "Skill 未能从已核验数据提供逐项证据"));
        }
        String state = missing.isEmpty() ? "complete" : "partial";
        return Map.of("state", state, "answerReady", true, "deliverables", states, "missingEvidence", missing, "continuationAllowed", false, "terminal", true);
    }

    private List<?> list(Object value) { return value instanceof List<?> values ? values : List.of(); }

    private Optional<ConversationRouteResult> routeConversation(String query, List<String> context) {
        try {
            return modelGateway.routeConversation(null, query, context == null ? List.of() : context, true);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Map<String, Object> queryWorkflow(String taskId, BoundCloudPivotConnection connection, String query, Map<String, Object> matched, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress) {
        String apiCode = workflowApiCode(query);
        emit(progress, trace, taskId, 55, "查询流程中心", "正在读取当前账号可见的流程数据。", "running");
        try {
            WorkflowReadResult result = workflowCenterService.queryForBinding(apiCode, 20, connection.baseUrl(), connection.username(), connection.password());
            Map<String, Object> data = Map.of("cardType", "workflow-list", "total", result.total(), "items", result.items());
            emit(progress, trace, taskId, 85, "核验流程结果", "流程数据读取完成，已按只读契约核验返回范围。", "completed");
            String answer = "已查询流程中心，返回 " + result.total() + " 条记录。";
            return finish(taskId, "completed", answer, Map.of("answer", answer, "result", data, "matchedMetadata", matched), trace, progress, 100, "流程只读结果已核验。");
        } catch (RuntimeException exception) {
            return finish(taskId, "failed", safeMessage(exception), Map.of("error", safeMessage(exception), "apiCode", apiCode), trace, progress, 100, "流程只读契约不可用或本次读取失败。");
        }
    }

    private Map<String, Object> finishWithConfirmation(String taskId, String intent, Map<String, Object> matched, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress, String query) {
        String message = "识别到需要写入或处理业务数据的请求。请回到 CPClaw 确认影响范围后执行；当前 MCP 不会自行确认写操作。";
        emit(progress, trace, taskId, 80, "生成确认计划", message, "needs_input");
        return finish(taskId, "confirmation_required", message, Map.of("intent", intent, "confirmationRequired", true, "requestSummary", limit(query, 240), "matchedMetadata", matched), trace, progress, 100, "写操作已被确认门禁拦截。");
    }

    private Map<String, Object> finish(String taskId, String status, String answer, Map<String, Object> payload, List<Map<String, Object>> trace, Consumer<TaskProgressEvent> progress, int percent, String finalMessage) {
        emit(progress, trace, taskId, percent, "完成任务核验", finalMessage, "completed".equals(status) ? "completed" : status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", status);
        result.put("understandingSummary", answer);
        result.put("executionTrace", List.copyOf(trace));
        result.putAll(payload);
        return result;
    }

    private void emit(Consumer<TaskProgressEvent> consumer, List<Map<String, Object>> trace, String taskId, int percent, String title, String message, String state) {
        TaskProgressEvent event = new TaskProgressEvent(percent, "execution", title, message, state);
        trace.add(Map.of("phase", "execution", "percent", percent, "title", title, "message", message, "state", state));
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

    private String classify(String query) {
        if (contains(query, "待办", "未完成", "已办", "已处理", "我发起", "流程", "审批")) return "workflow_query";
        if (contains(query, "删除", "移除", "作废")) return "delete_data";
        if (contains(query, "新增", "创建", "填单", "填表")) return "create_data";
        if (contains(query, "修改", "更新", "变更", "保存")) return "update_data";
        if (contains(query, "分析", "报告", "趋势", "分布", "占比", "对比", "排名")) return "analyze_data";
        if (contains(query, "查", "查询", "统计", "多少", "几条", "列表", "数据", "情况")) return "query_data";
        return "clarify_intent";
    }

    private String normalizeIntent(String value, String fallback) { String intent = normalize(value); return List.of("query_data", "analyze_data", "create_data", "update_data", "delete_data", "clarify_intent").contains(intent) ? intent : fallback; }
    private boolean isUsableMatch(MetadataSearchResult match) { return match != null && "entity".equals(match.objectType()) && !safe(match.code()).isBlank(); }
    private boolean isReadIntent(String intent) { return "query_data".equals(intent) || "analyze_data".equals(intent); }
    private boolean isAnalysisIntent(String intent) { return "analyze_data".equals(intent); }
    private boolean isWriteIntent(String intent) { return List.of("create_data", "update_data", "delete_data").contains(intent); }
    private boolean isWorkflowIntent(String intent) { return "workflow_query".equals(intent); }
    private String workflowApiCode(String query) { if (contains(query, "已办", "已处理")) return "workflow_list_finished"; if (contains(query, "我发起", "发起")) return "workflow_list_started"; return "workflow_list_pending"; }
    private boolean contains(String value, String... terms) { for (String term : terms) if (value.contains(term)) return true; return false; }
    private String metadataSummary(MetadataSearchResult match, MetadataExecutionPlanner.MetadataExecutionPlan plan) { if (!isUsableMatch(match)) return "尚未唯一定位到可执行业务对象，将先请求补充信息。"; return "已定位“" + safe(match.name()) + "”，并读取其字段、关联和 API 能力用于确定安全执行范围。"; }
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
