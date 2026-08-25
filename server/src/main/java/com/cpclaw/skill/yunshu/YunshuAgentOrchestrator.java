package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.agent.MetadataExecutionPlanner;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.CandidateDto;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.agent.MetadataExecutionPlanner.MetadataExecutionPlan;
import com.cpclaw.audit.AuditService;
import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.cloudpivot.CloudPivotQueryAnswer;
import com.cpclaw.cloudpivot.CloudPivotRuntimeService;
import com.cpclaw.cloudpivot.CloudPivotRuntimeService.RuntimeRecordTarget;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.QueryResultReferenceService;
import com.cpclaw.conversation.entity.QueryResultReference;
import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.skill.yunshu.YunshuInsightReportService;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.memory.MemoryService;
import com.cpclaw.model.IntentPlanningResult;
import com.cpclaw.model.ConversationRouteResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.skill.SkillCatalog;
import com.cpclaw.skill.SkillQuestionSemantics;
import com.cpclaw.cloudpivot.WorkflowReadResult;
import com.cpclaw.workflow.WorkflowCenterService;
import com.cpclaw.identity.PrincipalContextService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YunshuAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(YunshuAgentOrchestrator.class);

    private final MetadataSearchService metadataSearchService;
    private final CloudPivotRuntimeService cloudPivotRuntimeService;
    private final YunshuInsightReportService insightReportService;
    private final MetadataExecutionPlanner metadataExecutionPlanner;
    private final ModelGateway modelGateway;
    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotAppRepository appRepository;
    private final AuditService auditService;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ObjectMapper objectMapper;
    private final QueryResultReferenceService queryResultReferenceService;
    private final MemoryService memoryService;
    private final SkillCatalog skillCatalog;
    private final WorkflowCenterService workflowCenterService;
    private final PrincipalContextService principalContextService;
    private final SkillQuestionSemantics questionSemantics;

    public YunshuAgentOrchestrator(
        MetadataSearchService metadataSearchService,
        MetadataExecutionPlanner metadataExecutionPlanner,
        CloudPivotRuntimeService cloudPivotRuntimeService,
        YunshuInsightReportService insightReportService,
        ModelGateway modelGateway,
        CloudPivotEntityRepository entityRepository,
        CloudPivotAppRepository appRepository,
        AuditService auditService,
        SensitiveDataMasker sensitiveDataMasker,
        ObjectMapper objectMapper,
        QueryResultReferenceService queryResultReferenceService,
        MemoryService memoryService,
        SkillCatalog skillCatalog,
        WorkflowCenterService workflowCenterService,
        PrincipalContextService principalContextService,
        SkillQuestionSemantics questionSemantics
    ) {
        this.metadataSearchService = metadataSearchService;
        this.metadataExecutionPlanner = metadataExecutionPlanner;
        this.cloudPivotRuntimeService = cloudPivotRuntimeService;
        this.insightReportService = insightReportService;
        this.modelGateway = modelGateway;
        this.entityRepository = entityRepository;
        this.appRepository = appRepository;
        this.auditService = auditService;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.objectMapper = objectMapper;
        this.queryResultReferenceService = queryResultReferenceService;
        this.memoryService = memoryService;
        this.skillCatalog = skillCatalog;
        this.workflowCenterService = workflowCenterService;
        this.principalContextService = principalContextService;
        this.questionSemantics = questionSemantics;
    }

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, String modelConfigId, boolean thinkingEnabled, MessageItem assistantMessage, List<MessageItem> conversationContext) {
        return handleMessage(conversationId, userMessageId, content, modelConfigId, thinkingEnabled, assistantMessage, conversationContext, AgentProgressListener.NOOP);
    }

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, String modelConfigId, boolean thinkingEnabled, MessageItem assistantMessage, List<MessageItem> conversationContext, AgentProgressListener progressListener) {
        AgentProgressListener progress = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        AgentProgressListener executionProgress = AgentProgressListener.withoutAnswerEvents(progress);
        long startedAtNanos = System.nanoTime();
        List<Map<String, Object>> recalledMemory = memoryService.recallForPrincipal(conversationId, principalContextService.current());
        AgentObservation observation = observe(content, conversationContext, recalledMemory);

        // Do not force every input through metadata lookup or a second model-routing
        // call. Low-risk, non-business input gets one direct model response;
        // explicit business/system requests remain on the governed Skill path below.
        if (shouldRouteConversation(observation)) {
            return handleDirectConversation(
                conversationId, userMessageId, assistantMessage, observation,
                directConversationResult(modelConfigId, observation, thinkingEnabled, "未识别到需要调用业务 Skill 的明确系统操作，按通用对话直接回答。"),
                thinkingEnabled, progress, startedAtNanos
            );
        }
        if (!skillCatalog.isRegistered(SkillCatalog.YUNSHU_BUSINESS_SYSTEM)) {
            throw new IllegalStateException("云枢业务技能未注册，不能进入业务执行链");
        }
        progress.onThought("understand", "理解用户意图", "正在结合用户目标、会话上下文和已同步业务元数据分析处理方式。", "running");
        AgentThought thought = think(content, modelConfigId, thinkingEnabled, observation);
        progress.onThought("understand", "理解用户意图", modelReasoningStepSummary(thought), "completed");
        String intent = thought.intent();
        MetadataSearchResult match = thought.match();
        emitDynamicPlan(progress, thought);
        boolean writeRisk = isWriteIntent(intent);
        boolean workflowWriteRisk = "workflow_action".equals(intent);
        String riskLevel = workflowWriteRisk ? "high" : (writeRisk ? "medium" : "low");
        String planSummary = buildPlanSummary(intent, match, writeRisk);
        AgentPlan plan = plan(observation, thought, riskLevel, planSummary);
        String planJson = toJson(planAuditMap(observation, thought, plan));
        AgentRun run = auditService.createAgentRun(conversationId, userMessageId, intent, riskLevel, planJson);
        run.setBusinessIntent(resolveBusinessIntent(content, thought));
        auditService.saveAgentRun(run);
        auditService.recordToolCall(
            run.getId(),
            "metadata_search",
            toJson(Map.of(
                "query", content == null ? "" : content,
                "effectiveQuery", observation.effectiveUserGoal(),
                "executionPlan", thought.executionPlan().summary()
            )),
            toJson(Map.of("match", match.name(), "code", match.code(), "objectType", match.objectType(), "apiHints", thought.executionPlan().apiHints().stream().map(MetadataExecutionPlanner.ApiHint::summary).toList()))
        );
        long thinkingElapsedMs = elapsedMillis(startedAtNanos);

        ActResult actResult;
        if ("clarify_intent".equals(intent)) {
            progress.onExecution("生成澄清问题", "元数据无法唯一匹配，正在生成补充问题", Map.of("missingSlots", thought.missingSlots()), "running");
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match, thought.missingSlots()));
            progress.onExecution("生成澄清问题", "已生成需要用户补充的信息", Map.of("missingSlots", thought.missingSlots()), "needs_input");
            planSummary = "当前信息不足以安全执行，已向用户发起澄清问题。";
            actResult = new ActResult(assistantMessage, null, planSummary, null, null, "clarification_sent");
        } else if (writeRisk) {
                progress.onExecution("核对操作影响", "识别到数据修改操作，正在生成待确认计划。", Map.of("entityName", safeContextValue(match.name())), "running");
            try {
                Map<String, Object> writePlan = writeOperationPlan(conversationId, intent, match, observation.effectiveUserGoal());
                Confirmation confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, toJson(writePlan));
                assistantMessage = withContent(assistantMessage, confirmationRequiredMessage(intent, writePlan));
                progress.onExecution("核对操作影响", "待确认计划已生成，等待用户确认。", Map.of("entityName", safeContextValue(match.name())), "needs_input");
                actResult = new ActResult(assistantMessage, confirmation, planSummary, null, null, "pending_confirmation");
            } catch (RuntimeException exception) {
                assistantMessage = withContent(assistantMessage, writeClarificationMessage(intent, content, match, exception));
                progress.onExecution("核对操作影响", "缺少可验证的操作目标，尚未生成确认单。", Map.of(), "needs_input");
                planSummary = "写操作目标未能绑定到当前会话中已验证的记录，未创建确认单。";
                actResult = new ActResult(assistantMessage, null, planSummary, null, null, "write_target_unresolved");
            }
        } else if (isWorkflowIntent(intent)) {
            if ("workflow_action".equals(intent)) {
                assistantMessage = withContent(assistantMessage, workflowCapabilityMessage(content, intent));
                planSummary = "流程写接口尚未通过真实登录态契约验证；未创建不可执行的确认单，也未调用云枢写接口。";
                progress.onExecution("核对流程操作能力", "当前仅支持流程只读查询；处理动作保持禁用，未生成确认单。", Map.of("workflowCatalog", "workflow_center"), "needs_input");
                actResult = new ActResult(assistantMessage, null, planSummary, null, null, "workflow_action_unavailable");
            } else {
                String workflowApiCode = workflowApiCode(content);
                progress.onExecution("查询流程中心", "正在使用已验证的流程只读接口读取当前用户可见数据。", Map.of("apiCode", workflowApiCode), "running");
                try {
                    WorkflowReadResult workflowResult = workflowCenterService.query(workflowApiCode, 20);
                    assistantMessage = withContent(assistantMessage, workflowAnswer(content, workflowResult));
                    planSummary = "已通过已验证的流程中心只读接口查询真实数据。";
                    progress.onExecution("查询流程中心", "已返回流程中心真实查询结果。", Map.of("total", workflowResult.total(), "returned", workflowResult.items().size()), "completed");
                    auditService.recordToolCall(run.getId(), "workflow_read_query", toJson(Map.of("apiCode", workflowApiCode, "sourceEndpoint", workflowResult.sourceEndpoint())), toJson(Map.of("total", workflowResult.total(), "returned", workflowResult.items().size(), "responseShape", workflowResult.responseShape())));
                    actResult = new ActResult(assistantMessage, null, planSummary, null, null, "workflow_query_completed");
                } catch (RuntimeException exception) {
                    assistantMessage = withContent(assistantMessage, workflowFailureMessage(workflowApiCode, exception));
                    planSummary = "流程中心只读接口尚未完成验证，或本次真实查询失败；未调用任何流程写接口。";
                    actResult = new ActResult(assistantMessage, null, planSummary, null, null, "workflow_contract_unverified");
                }
            }
        } else if (isDataReadIntent(intent)) {
            if (canQueryRuntime(match)) {
                try {
                    if (insightReportService.supports(observation.effectiveUserGoal(), intent)) {
                        InsightExecutionResult insightResult = insightReportService.execute(
                            match,
                            observation.effectiveUserGoal(),
                            modelConfigId,
                            thinkingEnabled,
                            executionProgress
                        );
                        auditService.recordToolCall(
                            run.getId(),
                            "cloudpivot_insight_report",
                            toJson(Map.of(
                                "schemaCode", insightResult.primarySchemaCode(),
                                "question", observation.effectiveUserGoal(),
                                "reportSkillId", insightResult.report().skillId(),
                                "reportSkillVersion", insightResult.report().templateVersion()
                            )),
                            toJson(Map.of(
                                "primaryCount", insightResult.primaryCount(),
                                "sourceEndpoints", insightResult.sourceEndpoints(),
                                "report", insightResult.report()
                            ))
                        );
                        assistantMessage = withContent(assistantMessage, insightResult.answer(), insightMetadataJson(insightResult));
                        planSummary = insightResult.planSummary();
                        actResult = new ActResult(assistantMessage, null, planSummary, null, insightResult, "insight_report_completed");
                    } else {
                        List<RuntimeQueryFilter> runtimeFilters = buildRuntimeFilters(observation.effectiveUserGoal(), thought.executionPlan(), thought.modelPlan());
                        List<String> metricFieldCodes = buildMetricFieldCodes(observation.effectiveUserGoal(), thought.executionPlan(), thought.modelPlan());
                        Map<String, Object> reasoningContext = runtimeReasoningContext(observation, thought, runtimeFilters, metricFieldCodes);
                        CloudPivotQueryAnswer runtimeAnswer = cloudPivotRuntimeService.query(match, observation.effectiveUserGoal(), modelConfigId, thinkingEnabled, runtimeFilters, metricFieldCodes, reasoningContext, executionProgress);
                        auditService.recordToolCall(
                            run.getId(),
                            "cloudpivot_runtime_query",
                            toJson(Map.of("schemaCode", runtimeAnswer.schemaCode(), "action", runtimeAnswer.actionSummary())),
                            toJson(Map.of(
                                "total", runtimeAnswer.total(),
                                "returnedRecords", runtimeAnswer.returnedRecords(),
                                "sourceEndpoint", runtimeAnswer.sourceEndpoint(),
                                "rawDataSummary", runtimeAnswer.rawDataSummary(),
                                "conclusionSummary", runtimeAnswer.conclusionSummary()
                            ))
                        );
                        List<QueryResultReference> references = queryResultReferenceService.storeDisplayedRecords(
                            conversationId,
                            assistantMessage.id(),
                            run.getId(),
                            resolveAppCode(match, runtimeAnswer.schemaCode()),
                            runtimeAnswer.schemaCode(),
                            runtimeAnswer.displayedRecords()
                        );
                        assistantMessage = withContent(assistantMessage, runtimeAnswer.answer(), runtimeMetadataJson(runtimeAnswer, references));
                        planSummary = "analyze_data".equals(intent)
                            ? "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询数据，再生成业务分析。"
                            : "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询到真实数据。";
                        actResult = new ActResult(assistantMessage, null, planSummary, runtimeAnswer, null, "runtime_query_completed");
                    }
                } catch (RuntimeException exception) {
                    progress.checkCancelled();
                    log.error("CloudPivot read or insight report execution failed, schemaCode={}", match.code(), exception);
                    progress.onExecution(
                        "调用云枢运行态接口",
                        "查询失败，已停止生成业务结论",
                        Map.of("schemaCode", safeContextValue(match.code()), "error", safeContextValue(exception.getMessage())),
                        "fallback"
                    );
                    auditService.recordToolCall(
                        run.getId(),
                        "cloudpivot_runtime_query",
                        toJson(Map.of("schemaCode", match.code(), "action", "runtime_query_failed")),
                        toJson(Map.of("error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()))
                    );
                    assistantMessage = withContent(assistantMessage, runtimeFailureMessage(content, match, exception));
                    planSummary = "已理解为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类任务，并匹配到候选“" + match.name() + "”，但真实云枢运行态查询失败，未生成业务结论。";
                    actResult = new ActResult(assistantMessage, null, planSummary, null, null, "runtime_query_failed");
                }
            } else {
                assistantMessage = withContent(assistantMessage, unmatchedReadMessage(intent, content));
                planSummary = "已识别为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类请求，但本地元数据中没有匹配到可直接查询的云枢模型，未调用云枢运行态接口。";
                actResult = new ActResult(assistantMessage, null, planSummary, null, null, "no_runtime_candidate");
            }
        } else {
            progress.onExecution("生成澄清问题", "当前意图仍不明确，正在生成补充问题", Map.of(), "running");
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match));
            progress.onExecution("生成澄清问题", "已生成需要用户补充的信息", Map.of(), "needs_input");
            actResult = new ActResult(assistantMessage, null, planSummary, null, null, "fallback_clarification");
        }
        AgentReflection reflection = reflect(thought, plan, actResult);
        MemoryService.MemoryDecision memoryDecision = memoryService.evaluateWrite(
            "agent_run",
            reflection.passed(),
            false,
            content != null && content.matches("(?i).*\\b(password|token|cookie|api[-_ ]?key)\\b.*")
        );
        memoryService.rememberSuccessfulMapping(
            conversationId,
            run.getId(),
            thought.match().name(),
            thought.match().code(),
            memoryDecision.allowed() && isDataReadIntent(thought.intent())
        );
        streamDirectAnswer(progress, actResult.assistantMessage().content());
        auditService.updateReflection(run.getId(), toJson(reflectionAuditMap(reflection)));
        auditService.updateExecutionStatus(run.getId(), actResult.status());
        long answerElapsedMs = Math.max(0, elapsedMillis(startedAtNanos) - thinkingElapsedMs);
        MessageItem responseAssistantMessage = withAgentRunMetadata(actResult.assistantMessage(), run.getId(), defaultMetadataSource(actResult), thinkingElapsedMs, answerElapsedMs);
        List<ExecutionStepDto> safeSteps = reactSteps(observation, thought, actResult, reflection).stream()
            .map(step -> new ExecutionStepDto(maskText(step.title()), maskText(step.status())))
            .toList();

        return new AgentResponse(
            run.getId(),
            intent,
            riskLevel,
            actResult.confirmation() != null,
            maskText(actResult.planSummary()),
            maskText(match.reason()),
            List.of(new CandidateDto(match.name(), match.objectType(), maskText(match.reason()))),
            safeSteps,
            actResult.confirmation() == null ? null : actResult.confirmation().getId(),
            thinkingElapsedMs,
            answerElapsedMs,
            actResult.insightResult() == null ? null : actResult.insightResult().report(),
            responseAssistantMessage
        );
    }

    private boolean shouldRouteConversation(AgentObservation observation) {
        if (observation == null || observation.inheritedRuntimeObject()) {
            return false;
        }
        String value = compact(observation.normalizedUserGoal());
        if (isGeneralKnowledgeDiscussion(value)) {
            return true;
        }
        // A registered Skill owns domain language such as analysis/question
        // phrasing. If it recognizes a task but metadata is empty, enter the
        // governed path so the user receives clarification instead of a chat
        // fallback that could be mistaken for business data.
        if (!"unknown".equals(questionSemantics.detectIntent(value))
            && (containsBusinessObject(value) || containsExplicitSystemOperation(value) || value.contains("系统") || value.contains("数据"))) {
            return false;
        }
        // Do not use broad analytic wording as a system signal; metadata match is the authority.
        // Only an explicit business object or system action may bypass the
        // generic conversation router.
        return !hasStrongBusinessSignal(value);
    }

    /**
     * Product/architecture/design discussions can mention business nouns without
     * requesting the live business system. Preserve explicit operations, metrics,
     * and a prior runtime-query context as stronger evidence for a governed Skill.
     */
    private boolean isGeneralKnowledgeDiscussion(String value) {
        return !containsAny(value,
                "查询", "查一下", "查看", "搜索", "检索", "统计", "汇总", "填单", "填写", "填报",
                "新增", "新建", "创建", "录入", "修改", "更新", "删除", "审批", "通过", "驳回", "转交",
                "发起流程", "待办", "已办", "我发起", "流程实例", "工作项", "处理一下", "操作系统", "查找", "查数据", "看一下数据",
                "多少", "几个", "几条", "当前", "现在", "有没有", "数据", "明细")
            && !looksLikeMetricOrBusinessAnalysis(value)
            && containsAny(value, "设计", "架构", "方案", "原型", "需求", "实现", "大纲", "说明文档", "说明书", "怎么做", "如何做");
    }

    private boolean hasDeterministicBusinessSignal(String content) {
        String value = compact(content);
        return hasStrongBusinessSignal(value);
    }

    /**
     * A business noun is not enough evidence that the user wants CloudPivot data.
     * Keep this gate narrow so
     * the model can understand the former instead of being trapped by keywords.
     */
    private boolean hasStrongBusinessSignal(String value) {
        if (containsExplicitSystemOperation(value) || looksLikeMetricOrBusinessAnalysis(value)) {
            return true;
        }
        return containsBusinessObject(value) && containsAny(value,
            "多少", "几个", "几条", "哪些", "哪个", "列表", "详情", "记录", "当前", "现在", "有没有", "存在", "情况",
            "信息", "数据", "内容", "明细", "概况", "分析", "报告"
        );
    }

    private boolean containsExplicitSystemOperation(String value) {
        return containsAny(value,
            "查询", "查看", "搜索", "检索", "列出", "展示", "统计", "汇总", "填单", "填写", "填报",
            "新增", "新建", "创建", "录入", "修改", "更新", "删除", "审批", "通过", "驳回", "转交",
            "发起流程", "待办", "已办", "我发起", "流程实例", "工作项", "处理一下", "操作系统", "查一下", "查找", "查数据", "看一下数据"
        );
    }

    private ConversationRouteResult directConversationResult(
        String modelConfigId,
        AgentObservation observation,
        boolean thinkingEnabled,
        String fallbackReason
    ) {
        String answer = modelGateway.answerGeneralConversation(
            modelConfigId, observation.contextualGoal(), observation.recentMessages(), thinkingEnabled
        ).orElse("通用对话模型暂时未在限定时间内返回结果，因此我不会用固定内容冒充回答。请稍后重试，或关闭深度模式后重试。");
        return new ConversationRouteResult("conversation", "none", answer, fallbackReason, 0.9D);
    }

    private AgentResponse handleDirectConversation(
        String conversationId,
        String userMessageId,
        MessageItem assistantMessage,
        AgentObservation observation,
        ConversationRouteResult route,
        boolean thinkingEnabled,
        AgentProgressListener progress,
        long startedAtNanos
    ) {
        AgentProgressListener conversationProgress = AgentProgressListener.withoutTimelineEvents(progress);
        String answer = maskText(route.answer());
        assistantMessage = withContent(assistantMessage, answer, toJson(Map.of(
            "source", "direct-conversation",
            "mode", "conversation",
            "thinkingEnabled", thinkingEnabled,
            "routeReasoningSummary", maskText(shortText(route.reasoning(), 160))
        )));
        conversationProgress.onAnswerStart("direct-conversation");
        AnswerStreamSupport.emitReadableChunks(answer, conversationProgress::onAnswerChunk);
        conversationProgress.onAnswerComplete("direct-conversation");
        AgentRun run = auditService.createAgentRun(
            conversationId,
            userMessageId,
            "conversation",
            "low",
            toJson(Map.of(
                "mode", "conversation",
                "routeConfidence", route.confidence(),
                "routeReasoning", maskText(route.reasoning()),
                "userGoal", maskText(observation.normalizedUserGoal()),
                "contextualGoal", maskText(observation.contextualGoal()),
                "previousAssistantFeedback", observation.previousAssistantFeedback() == null ? "" : observation.previousAssistantFeedback()
            ))
        );
        run.setBusinessIntent("通用对话");
        auditService.saveAgentRun(run);
        AgentReflection reflection = new AgentReflection(
            "completed",
            true,
            false,
            List.of("route=conversation", "cloudpivot_access=false"),
            "已完成通用对话回答，未调用业务技能。"
        );
        auditService.updateReflection(run.getId(), toJson(reflectionAuditMap(reflection)));
        auditService.updateExecutionStatus(run.getId(), "completed");
        long elapsedMs = elapsedMillis(startedAtNanos);
        long thinkingElapsedMs = thinkingEnabled ? elapsedMs : 0L;
        long answerElapsedMs = thinkingEnabled ? 0L : elapsedMs;
        MessageItem responseMessage = withAgentRunMetadata(assistantMessage, run.getId(), "direct-conversation", thinkingElapsedMs, answerElapsedMs);
        List<ExecutionStepDto> steps = List.of();
        return new AgentResponse(
            run.getId(),
            "conversation",
            "low",
            false,
            "已按通用对话模式直接回答，未调用云枢业务能力。",
            "通用对话",
            List.of(),
            steps,
            null,
            thinkingElapsedMs,
            answerElapsedMs,
            null,
            responseMessage
        );
    }

    private AgentResponse handleSkillClarification(
        String conversationId,
        String userMessageId,
        MessageItem assistantMessage,
        AgentObservation observation,
        ConversationRouteResult route,
        AgentProgressListener progress,
        long startedAtNanos
    ) {
        progress.onThought("route", "判断处理模式", "识别到系统任务，但对应 Skill 尚未注册。", "needs_input");
        String answer = "我理解你可能要操作系统，但当前请求对应的业务能力尚未注册。请明确说明要查询或操作哪个系统/业务对象。";
        assistantMessage = withContent(assistantMessage, answer, "{\"source\":\"skill-routing\",\"mode\":\"clarify\"}");
        AgentRun run = auditService.createAgentRun(
            conversationId,
            userMessageId,
            "clarify_intent",
            "low",
            toJson(Map.of("mode", "clarify", "requestedSkillId", route.skillId(), "routeReasoning", maskText(route.reasoning())))
        );
        run.setBusinessIntent("补充系统能力范围");
        auditService.saveAgentRun(run);
        auditService.updateReflection(run.getId(), toJson(Map.of(
            "mode", "skill-route-reflection",
            "status", "needs_user_input",
            "passed", false,
            "needsUserInput", true,
            "summary", "未注册的 Skill 不得进入执行链。"
        )));
        auditService.updateExecutionStatus(run.getId(), "clarification_sent");
        long elapsedMs = elapsedMillis(startedAtNanos);
        MessageItem responseMessage = withAgentRunMetadata(assistantMessage, run.getId(), "skill-routing", elapsedMs, 0L);
        List<ExecutionStepDto> steps = List.of(
            new ExecutionStepDto("请求补充信息", "请明确系统和业务对象。", "clarify", "needs_input", "execution", Map.of(), 0L)
        );
        return new AgentResponse(
            run.getId(), "clarify_intent", "low", false,
            "当前 Skill 未注册，已暂停执行并请求补充信息。", "未注册 Skill：" + route.skillId(),
            List.of(), steps, null, elapsedMs, 0L, null, responseMessage
        );
    }

    private void streamDirectAnswer(AgentProgressListener progress, String content) {
        progress.onAnswerStart("direct");
        AnswerStreamSupport.emitReadableChunks(content, progress::onAnswerChunk);
        progress.onAnswerComplete("direct");
    }

    public Map<String, Object> previewPlaceholderPlan() {
        return Map.of(
            "planId", UUID.randomUUID().toString(),
            "intent", "query_data",
            "status", "agent-plan-placeholder",
            "requiresConfirmation", false
        );
    }

    private AgentObservation observe(String content, List<MessageItem> conversationContext, List<Map<String, Object>> recalledMemory) {
        List<MessageItem> safeContext = conversationContext == null ? List.of() : conversationContext;
        RuntimeContextObject runtimeContext = lastRuntimeContext(safeContext);
        String normalizedGoal = content == null ? "" : content.trim();
        boolean inheritedRuntimeObject = shouldInheritRuntimeObject(normalizedGoal, runtimeContext);
        String effectiveGoal = inheritedRuntimeObject ? inheritedEffectiveGoal(runtimeContext, normalizedGoal) : normalizedGoal;
        List<String> recentMessages = safeContext.stream()
            .filter(message -> message.content() != null && !message.content().isBlank())
            .skip(Math.max(0, safeContext.size() - 6))
            .map(message -> message.role() + ":" + shortText(maskText(message.content()), 600)
                + ("assistant".equals(message.role()) && message.feedbackType() != null ? " [feedback=" + message.feedbackType() + "]" : ""))
            .toList();
        ContextualTurn previousTurn = previousTurn(safeContext);
        boolean contextualFollowUp = isContextualFollowUp(normalizedGoal, previousTurn);
        String contextualGoal = buildContextualGoal(normalizedGoal, effectiveGoal, previousTurn, contextualFollowUp);
        boolean hasPreviousAssistantResult = previousTurn.assistantAnswer() != null && !previousTurn.assistantAnswer().isBlank();
        boolean referencesPreviousResult = containsAny(content, List.of("第一条", "第二条", "上一条", "刚才", "这些", "这个", "它", "它们", "他们", "上述"));
        return new AgentObservation(
            normalizedGoal,
            effectiveGoal,
            contextualGoal,
            recentMessages,
            safeMemory(recalledMemory),
            hasPreviousAssistantResult,
            referencesPreviousResult || contextualFollowUp,
            runtimeContext,
            inheritedRuntimeObject,
            contextualFollowUp,
            previousTurn.userGoal(),
            previousTurn.assistantAnswer(),
            previousTurn.feedbackType()
        );
    }

    private ContextualTurn previousTurn(List<MessageItem> messages) {
        int assistantIndex = -1;
        for (int index = messages.size() - 1; index >= 0; index--) {
            MessageItem message = messages.get(index);
            if ("assistant".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                assistantIndex = index;
                break;
            }
        }
        if (assistantIndex < 0) {
            return ContextualTurn.empty();
        }
        String userGoal = "";
        for (int index = assistantIndex - 1; index >= 0; index--) {
            MessageItem message = messages.get(index);
            if ("user".equals(message.role()) && message.content() != null && !message.content().isBlank()) {
                userGoal = message.content();
                break;
            }
        }
        MessageItem assistant = messages.get(assistantIndex);
        return new ContextualTurn(
            shortText(maskText(userGoal), 1200),
            shortText(maskText(assistant.content()), 2400),
            assistant.feedbackType()
        );
    }

    private boolean isContextualFollowUp(String content, ContextualTurn previousTurn) {
        if (previousTurn == null || previousTurn.assistantAnswer().isBlank() || content == null || content.isBlank()) {
            return false;
        }
        String value = compact(content);
        boolean refinementSignal = containsAny(value,
            "再详细", "详细设计", "展开", "展开说", "完善", "补充", "深入", "优化", "改进", "重做", "重新设计",
            "不够", "不满意", "不完整", "太简单", "更具体", "讲具体", "继续", "上一轮", "上面的", "这个回答", "这份方案", "再说说"
        );
        if (refinementSignal) {
            return true;
        }
        return value.length() <= 24 && containsAny(value, "这个", "它", "上述", "刚才", "继续", "再来", "换一种");
    }

    private String buildContextualGoal(String normalizedGoal, String effectiveGoal, ContextualTurn previousTurn, boolean contextualFollowUp) {
        if (!contextualFollowUp || previousTurn == null || previousTurn.userGoal().isBlank()) {
            return normalizedGoal;
        }
        return "上一轮用户目标：" + previousTurn.userGoal()
            + "\n上一轮助手回答：" + previousTurn.assistantAnswer()
            + "\n上一轮反馈：" + (previousTurn.feedbackType() == null ? "未提交" : previousTurn.feedbackType())
            + "\n本轮追加要求：" + normalizedGoal
            + "\n请保持上一轮主题，针对追加要求重新组织并补足答案。";
    }

    private AgentThought think(String content, String modelConfigId, boolean thinkingEnabled, AgentObservation observation) {
        String query = observation.effectiveUserGoal();
        String detectedIntent = detectIntent(query);
        MetadataSearchResult candidate = observation.inheritedRuntimeObject()
            ? inheritedMetadataMatch(observation.runtimeContext())
            : metadataSearchService.bestMatch(query);
        MetadataExecutionPlan executionPlan = observation.inheritedRuntimeObject()
            ? metadataExecutionPlanner.inherited(candidate, query)
            : metadataExecutionPlanner.plan(query, candidate);
        MetadataSearchResult match = executionPlan.executableMatch();
        IntentSlots slots = extractIntentSlots(query, detectedIntent);
        String ruleIntent = detectedIntent;
        IntentPlanningResult modelPlan = maybePlanIntent(modelConfigId, thinkingEnabled, observation, detectedIntent, match, executionPlan, slots);
        boolean usedModelPlan = shouldUseModelPlan(modelPlan);
        if (usedModelPlan) {
            detectedIntent = normalizeIntent(modelPlan.intent(), detectedIntent);
            slots = mergeIntentSlots(slots, modelPlan);
        }
        // Destructive intent is a hard safety boundary: model planning may enrich slots,
        // but it must never downgrade an explicit delete request into a read operation.
        if (isDeleteIntent(compact(query))) {
            detectedIntent = "delete_data";
        }
        // Creation and modification are distinct user-visible business actions.
        // The planner may enrich their slots, but cannot rewrite an explicit verb.
        if (isCreateIntent(compact(query))) {
            detectedIntent = "create_data";
        } else if (isUpdateIntent(compact(query))) {
            detectedIntent = "update_data";
        }
        // A model plan may enrich a write request, but it must never downgrade
        // an explicit write into a read merely because the planner was uncertain.
        if (isWriteIntent(ruleIntent) && !isWriteIntent(detectedIntent)) {
            detectedIntent = ruleIntent;
        }
        // Workflow processing is gated by an authenticated endpoint contract. A model
        // may refine its business description but cannot convert it into a generic
        // data query or bypass the workflow capability gate.
        if (isWorkflowIntent(ruleIntent)) {
            detectedIntent = ruleIntent;
        }
        List<String> missingSlots = missingSlots(detectedIntent, match, observation);
        if (usedModelPlan && modelPlan.clarificationNeeded() && modelPlan.confidence() < 0.7) {
            missingSlots = mergeMissingSlots(missingSlots, "模型规划要求澄清");
        }
        String intent = missingSlots.isEmpty() ? detectedIntent : "clarify_intent";
        if (needsClarification(intent, match)) {
            intent = "clarify_intent";
        }
        double confidence = confidence(intent, match, missingSlots);
        if (usedModelPlan && modelPlan.confidence() > 0D) {
            confidence = Math.min(0.98, Math.max(confidence, modelPlan.confidence()));
        }
        String reasoning = reasoningSummary(intent, detectedIntent, match, missingSlots, observation, slots)
            + modelPlanReasoning(modelPlan, usedModelPlan)
            + "；执行计划=" + executionPlan.summary();
        return new AgentThought(detectedIntent, intent, match, executionPlan, slots, missingSlots, confidence, reasoning, usedModelPlan, modelPlan);
    }

    private AgentPlan plan(AgentObservation observation, AgentThought thought, String riskLevel, String planSummary) {
        List<String> actions = new ArrayList<>();
        actions.add("metadata_search");
        actions.add("metadata_execution_plan");
        if ("clarify_intent".equals(thought.intent())) {
            actions.add("ask_clarifying_question");
        } else if (isWorkflowIntent(thought.intent())) {
            actions.add("workflow_metadata_contract_gate");
            actions.add("ask_for_authenticated_read_probe");
        } else if (isWriteIntent(thought.intent())) {
            actions.add("create_confirmation");
        } else if (isDataReadIntent(thought.intent()) && canQueryRuntime(thought.match())) {
            actions.add("cloudpivot_runtime_query");
            if ("analyze_data".equals(thought.intent())) {
                actions.add("answer_analysis");
            } else {
                actions.add("answer_query_result");
            }
        } else {
            actions.add("safe_fallback_clarification");
        }
        return new AgentPlan(planSummary, riskLevel, isWriteIntent(thought.intent()) || "workflow_action".equals(thought.intent()), actions);
    }

    private AgentReflection reflect(AgentThought thought, AgentPlan plan, ActResult actResult) {
        List<String> checks = new ArrayList<>();
        checks.add("metadata_candidate=" + thought.match().name());
        checks.add("risk_level=" + plan.riskLevel());
        checks.add("tool_result=" + actResult.status());
        if ("clarify_intent".equals(thought.intent())) {
            checks.add("clarification_required=true");
            return new AgentReflection("needs_user_input", false, true, checks, "信息不足或对象不明确，已进入澄清，不执行云枢写操作。");
        }
        if (plan.requiresConfirmation()) {
            checks.add("confirmation_required=true");
            boolean hasConfirmation = actResult.confirmation() != null;
            return new AgentReflection(hasConfirmation ? "pending_confirmation" : "blocked", hasConfirmation, false, checks, "写操作必须等待用户确认后才能继续。");
        }
        if (isWorkflowIntent(thought.intent())) {
            boolean completed = "workflow_query_completed".equals(actResult.status());
            checks.add("workflow_contract_verified=" + completed);
            return new AgentReflection(completed ? "completed" : "needs_user_input", completed, !completed, checks, completed ? "流程中心只读查询已完成，结果可返回用户。" : "流程中心接口未完成只读契约验证，未查询或执行流程动作。");
        }
        if (isDataReadIntent(thought.intent())) {
            boolean hasRuntimeAnswer = actResult.runtimeAnswer() != null && actResult.runtimeAnswer().total() >= 0;
            boolean hasInsightAnswer = actResult.insightResult() != null && actResult.insightResult().report() != null;
            boolean hasAnswer = hasRuntimeAnswer || hasInsightAnswer;
            checks.add("runtime_query=" + hasAnswer);
            if (hasRuntimeAnswer) {
                checks.add("real_cloudpivot_schema=" + !"local-fallback".equals(actResult.runtimeAnswer().sourceEndpoint()));
            }
            if (hasInsightAnswer) checks.add("insight_report=true");
            return new AgentReflection(hasAnswer ? "completed" : "needs_user_input", hasAnswer, !hasAnswer, checks, hasAnswer ? "读数据任务已完成，结果可返回用户。" : "没有拿到真实云枢运行态数据，已停止生成业务结论。");
        }
        return new AgentReflection("completed", true, false, checks, "已完成安全兜底处理。");
    }

    private String detectIntent(String content) {
        String resolved = questionSemantics.detectIntent(content);
        return "unknown".equals(resolved) && containsBusinessObject(compact(content)) ? "query_data" : resolved;
    }

    private boolean isCreateIntent(String value) {
        return containsAny(value, "新增", "新建", "创建", "录入", "登记");
    }

    private boolean isUpdateIntent(String value) {
        return containsAny(value, "写入", "修改", "更新", "调整", "变更", "编辑", "保存", "提交", "发起", "分配", "转移", "关闭", "推进");
    }
    private boolean containsBusinessObject(String value) {
        if (value == null || value.isBlank()) return false;
        MetadataSearchResult candidate = metadataSearchService.bestMatch(value);
        return candidate != null && "entity".equals(candidate.objectType()) && candidate.code() != null && !candidate.code().isBlank();
    }

    private boolean isNewOldCustomerComparisonIntent(String value) {
        return false;
    }

    private boolean isStatusMetricAggregationIntent(String value) {
        return questionSemantics.isStatusAmountAggregationQuestion(value);
    }

    private boolean isMetricRankingIntent(String value) {
        return questionSemantics.isAmountRankingQuestion(value);
    }

    private boolean isDeleteIntent(String value) {
        if (containsAny(value, "删除", "删掉", "移除", "作废")) {
            return true;
        }
        if (!value.contains("取消")) {
            return false;
        }
        boolean asksMetric = containsAny(value, "多少", "几个", "几条", "数量", "金额", "总额", "合计", "汇总", "统计", "分析");
        boolean statusRead = containsAny(value, "取消的", "已取消", "被取消", "状态为取消");
        if (asksMetric || statusRead) {
            return false;
        }
        return containsAny(value, "取消这个", "取消这条", "取消第");
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String compact(String content) {
        return content == null ? "" : content.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean isWriteIntent(String intent) {
        return !isDataReadIntent(intent) && !isWorkflowIntent(intent) && !"unknown".equals(intent) && !"clarify_intent".equals(intent);
    }

    private boolean isDataReadIntent(String intent) {
        return "query_data".equals(intent) || "analyze_data".equals(intent);
    }

    private boolean isWorkflowIntent(String intent) {
        return "query_workflow".equals(intent) || "workflow_action".equals(intent);
    }

    private boolean canQueryRuntime(MetadataSearchResult match) {
        return match != null
            && "entity".equals(match.objectType())
            && match.code() != null
            && !match.code().isBlank()
            && !isKnownDemoBusinessCode(match.code());
    }

    private boolean isKnownDemoBusinessCode(String code) {
        return false;
    }

    private boolean needsClarification(String intent, MetadataSearchResult match) {
        return "unknown".equals(intent) || (isDataReadIntent(intent) && !canQueryRuntime(match)) || (isWriteIntent(intent) && !canQueryRuntime(match));
    }

    private List<String> missingSlots(String detectedIntent, MetadataSearchResult match, AgentObservation observation) {
        List<String> slots = new ArrayList<>();
        if ("unknown".equals(detectedIntent)) {
            slots.add("动作类型");
        }
        if ((isDataReadIntent(detectedIntent) || isWriteIntent(detectedIntent)) && !canQueryRuntime(match)) {
            slots.add("云枢对象");
        }
        if (isWriteIntent(detectedIntent) && !"delete_data".equals(detectedIntent) && observation.referencesPreviousResult() && !observation.hasPreviousAssistantResult()) {
            slots.add("上下文记录引用");
        }
        if ("delete_data".equals(detectedIntent) && !hasDeleteTargetReference(observation)) {
            slots.add("可删除记录目标（记录ID或第几条）");
        }
        if (isWriteIntent(detectedIntent) && containsAny(observation.normalizedUserGoal(), List.of("跟进记录", "写一条跟进")) && !containsAny(observation.normalizedUserGoal(), List.of("跟进内容", "沟通", "回访", "电话", "邮件", "下周", "今天", "明天"))) {
            slots.add("跟进内容");
        }
        return slots.stream().distinct().toList();
    }

    private boolean hasDeleteTargetReference(AgentObservation observation) {
        return hasDeleteTargetReference(observation == null ? null : observation.normalizedUserGoal())
            || hasDeleteTargetReference(observation == null ? null : observation.effectiveUserGoal())
            || (observation != null && observation.referencesPreviousResult());
    }

    private boolean hasDeleteTargetReference(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        if (value.isBlank()) {
            return false;
        }
        if (Pattern.compile("(?i)(bizObjectId|objectId|dataId|id)[^0-9a-zA-Z_-]?[0-9a-z_-]{6,}").matcher(value).find()) {
            return true;
        }
        String compactValue = compact(value);
        if (value.contains("\u9996") || value.contains("\u7b2c1") || value.contains("\u7b2c\u4e00")
            || compactValue.contains("first") || compactValue.contains("top1") || compactValue.contains("no.1")) {
            return true;
        }
        if (Pattern.compile("[\ufffd?]+\u04bb[\ufffd?]+").matcher(value).find()) {
            return true;
        }
        return Pattern.compile("[0-9]+(\u6761|\u4e2a|\u7b14|\u5355)").matcher(value).find()
            || Pattern.compile("[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u4e24]+(\u6761|\u4e2a|\u7b14|\u5355)").matcher(value).find();
    }

    private double confidence(String intent, MetadataSearchResult match, List<String> missingSlots) {
        if ("clarify_intent".equals(intent)) {
            return missingSlots.isEmpty() ? 0.45 : 0.35;
        }
        double score = 0.7;
        if (canQueryRuntime(match)) {
            score += 0.2;
        }
        if (missingSlots.isEmpty()) {
            score += 0.05;
        }
        return Math.min(0.95, score);
    }

    private IntentPlanningResult maybePlanIntent(
        String modelConfigId,
        boolean thinkingEnabled,
        AgentObservation observation,
        String detectedIntent,
        MetadataSearchResult match,
        MetadataExecutionPlan executionPlan,
        IntentSlots slots
    ) {
        if (!shouldAskModelPlanner(observation, detectedIntent, match, slots)) {
            return IntentPlanningResult.empty();
        }
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userGoal", observation.normalizedUserGoal());
        context.put("effectiveUserGoal", observation.effectiveUserGoal());
        context.put("contextualGoal", observation.contextualGoal());
        context.put("contextualFollowUp", observation.contextualFollowUp());
        context.put("previousUserGoal", observation.previousUserGoal());
        context.put("previousAssistantAnswer", observation.previousAssistantAnswer());
        context.put("previousAssistantFeedback", observation.previousAssistantFeedback() == null ? "" : observation.previousAssistantFeedback());
        context.put("deterministicIntent", detectedIntent);
        context.put("deterministicAction", slots.actionLabel());
        context.put("deterministicDimension", slots.dimension());
        context.put("deterministicFilters", slots.filters());
        context.put("entityName", match.name());
        context.put("schemaCode", match.code());
        context.put("metadataObjectType", match.objectType());
        context.put("metadataReason", match.reason());
        context.put("inheritedRuntimeObject", observation.inheritedRuntimeObject());
        context.put("runtimeContextEntity", observation.runtimeContext().entityName());
        context.put("runtimeContextSchema", observation.runtimeContext().schemaCode());
        context.put("recentMessages", observation.recentMessages());
        context.put("recalledMemory", observation.recalledMemory());
        context.put("fieldHints", executionPlan.fieldHints().stream().map(this::fieldHintSummary).toList());
        context.put("relationHints", executionPlan.relationHints().stream().map(MetadataExecutionPlanner.RelationHint::summary).toList());
        context.put("apiHints", executionPlan.apiHints().stream().map(MetadataExecutionPlanner.ApiHint::summary).toList());
        return modelGateway.planIntent(modelConfigId, context, thinkingEnabled).orElse(IntentPlanningResult.empty());
    }

    private List<RuntimeQueryFilter> buildRuntimeFilters(String userQuestion, MetadataExecutionPlan executionPlan) {
        return buildRuntimeFilters(userQuestion, executionPlan, IntentPlanningResult.empty());
    }

    private List<RuntimeQueryFilter> buildRuntimeFilters(String userQuestion, MetadataExecutionPlan executionPlan, IntentPlanningResult modelPlan) {
        if (executionPlan == null) {
            return List.of();
        }
        List<RuntimeQueryFilter> plannedFilters = plannedRuntimeFilters(modelPlan, executionPlan).stream()
            .filter(filter -> isGroundedRuntimeFilter(userQuestion, filter, executionPlan))
            .toList();
        if (!plannedFilters.isEmpty()) {
            return plannedFilters;
        }
        Optional<String> ownerName = requestedOwnerName(userQuestion).filter(this::isLikelyPersonName);
        if (ownerName.isEmpty()) {
            return List.of();
        }
        return executionPlan.fieldHints().stream()
            .filter(this::isOwnerFieldHint)
            .findFirst()
            .map(field -> List.of(new RuntimeQueryFilter(
                field.code(),
                field.name(),
                "like",
                ownerName.get(),
                "metadata_field_hint",
                0.86
            )))
            .orElse(List.of());
    }

    private List<String> buildMetricFieldCodes(String userQuestion, MetadataExecutionPlan executionPlan) {
        return buildMetricFieldCodes(userQuestion, executionPlan, IntentPlanningResult.empty());
    }

    private List<String> buildMetricFieldCodes(String userQuestion, MetadataExecutionPlan executionPlan, IntentPlanningResult modelPlan) {
        List<String> plannedMetricFields = safeModelMetricFieldCodes(modelPlan, executionPlan);
        if (!plannedMetricFields.isEmpty()) {
            return plannedMetricFields;
        }
        if (executionPlan == null || !asksAmountMetric(userQuestion)) {
            return List.of();
        }
        return executionPlan.fieldHints().stream()
            .filter(this::isAmountFieldHint)
            .map(MetadataExecutionPlanner.FieldHint::code)
            .filter(code -> code != null && !code.isBlank())
            .distinct()
            .limit(5)
            .toList();
    }

    private List<RuntimeQueryFilter> plannedRuntimeFilters(IntentPlanningResult modelPlan, MetadataExecutionPlan executionPlan) {
        if (modelPlan == null || modelPlan.runtimeFilters() == null || modelPlan.runtimeFilters().isEmpty() || executionPlan == null) {
            return List.of();
        }
        return modelPlan.runtimeFilters().stream()
            .map(filter -> toRuntimeFilter(filter, executionPlan))
            .flatMap(Optional::stream)
            .toList();
    }

    private boolean isGroundedRuntimeFilter(String userQuestion, RuntimeQueryFilter filter, MetadataExecutionPlan executionPlan) {
        String filterValue = compact(filter.value());
        if (List.of("现在", "当前", "系统", "全部", "所有", "目前").contains(filterValue)) {
            return false;
        }
        MetadataExecutionPlanner.FieldHint field = executionPlan.fieldHints().stream()
            .filter(hint -> filter.fieldCode().equalsIgnoreCase(hint.code()))
            .findFirst()
            .orElse(null);
        if (field == null || !isOwnerFieldHint(field)) {
            return true;
        }
        if (!isLikelyPersonName(filter.value())) {
            return false;
        }
        Optional<String> requestedOwner = requestedOwnerName(userQuestion).filter(this::isLikelyPersonName);
        return requestedOwner.isPresent() && requestedOwner.get().equalsIgnoreCase(filter.value());
    }

    private Optional<RuntimeQueryFilter> toRuntimeFilter(Map<String, Object> value, MetadataExecutionPlan executionPlan) {
        String fieldCode = stringValue(value.get("fieldCode"));
        if (fieldCode.isBlank() || !isKnownFieldCode(fieldCode, executionPlan)) {
            return Optional.empty();
        }
        String fieldName = stringValue(value.get("fieldName"));
        String operator = stringValue(value.get("operator"));
        String filterValue = stringValue(value.get("value"));
        if (filterValue.isBlank()) {
            return Optional.empty();
        }
        MetadataExecutionPlanner.FieldHint field = executionPlan.fieldHints().stream()
            .filter(hint -> fieldCode.equalsIgnoreCase(hint.code()))
            .findFirst()
            .orElse(null);
        return Optional.of(new RuntimeQueryFilter(
            fieldCode,
            fieldName.isBlank() && field != null ? field.name() : fieldName,
            operator.isBlank() ? "like" : operator,
            filterValue,
            "llm_execution_plan",
            0.9
        ));
    }

    private List<String> safeModelMetricFieldCodes(IntentPlanningResult modelPlan, MetadataExecutionPlan executionPlan) {
        if (modelPlan == null || modelPlan.metricFieldCodes() == null || modelPlan.metricFieldCodes().isEmpty() || executionPlan == null) {
            return List.of();
        }
        return modelPlan.metricFieldCodes().stream()
            .map(this::normalizeText)
            .filter(code -> !code.isBlank() && isKnownFieldCode(code, executionPlan))
            .distinct()
            .limit(8)
            .toList();
    }

    private boolean isKnownFieldCode(String code, MetadataExecutionPlan executionPlan) {
        return executionPlan.fieldHints().stream().anyMatch(hint -> code.equalsIgnoreCase(hint.code()));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean asksAmountMetric(String content) {
        String value = compact(content);
        return value.contains("\u91d1\u989d")
            || value.contains("\u5408\u540c\u989d")
            || value.contains("\u6536\u5165")
            || value.contains("\u603b\u989d")
            || value.contains("amount")
            || value.contains("money")
            || value.contains("revenue");
    }

    private boolean isAmountFieldHint(MetadataExecutionPlanner.FieldHint hint) {
        if (hint == null) {
            return false;
        }
        String value = compact(hint.name() + hint.code() + hint.description());
        return value.contains("\u91d1\u989d")
            || value.contains("\u5408\u540c\u989d")
            || value.contains("\u6536\u5165")
            || value.contains("\u603b\u989d")
            || value.contains("amount")
            || value.contains("money")
            || value.contains("revenue")
            || value.contains("amt");
    }

    private Optional<String> requestedOwnerName(String content) {
        String value = content == null ? "" : content.replaceAll("\\s+", "");
        if (value.isBlank()) {
            return Optional.empty();
        }
        Optional<String> unicodeOwner = requestedOwnerNameUnicode(value);
        if (unicodeOwner.isPresent()) {
            return unicodeOwner;
        }
        for (String suffix : List.of("名下有多少", "名下有几个", "名下有几条", "负责的", "负责多少", "销售的")) {
            Optional<String> name = nameBefore(value, suffix);
            if (name.isPresent()) {
                return name;
            }
        }
        java.util.regex.Matcher fieldMatcher = java.util.regex.Pattern.compile("(?:负责人|销售|业务员|归属销售|owner)(?:是|为|=|：|:)?([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9._-]{1,15})").matcher(value);
        if (fieldMatcher.find()) {
            return Optional.of(cleanOwnerName(fieldMatcher.group(1))).filter(text -> !text.isBlank());
        }
        java.util.regex.Matcher directMatcher = java.util.regex.Pattern.compile("^([\\p{IsHan}]{2,5}|[A-Za-z][A-Za-z0-9._-]{1,30})(?:有多少|有几个|有几条).*$").matcher(value);
        if (directMatcher.matches()) {
            return Optional.of(cleanOwnerName(directMatcher.group(1))).filter(text -> !text.isBlank());
        }
        return Optional.empty();
    }

    private Optional<String> nameBefore(String value, String suffix) {
        int index = value.indexOf(suffix);
        if (index <= 0) {
            return Optional.empty();
        }
        String before = value.substring(0, index);
        for (String prefix : List.of("请问", "帮我查", "帮我看看", "查询", "统计", "分析", "系统", "现在", "当前")) {
            if (before.startsWith(prefix) && before.length() > prefix.length()) {
                before = before.substring(prefix.length());
            }
        }
        return Optional.of(cleanOwnerName(before)).filter(text -> !text.isBlank());
    }

    private Optional<String> requestedOwnerNameUnicode(String value) {
        for (String suffix : List.of("\u540d\u4e0b\u6709\u591a\u5c11", "\u540d\u4e0b\u6709\u51e0\u4e2a", "\u540d\u4e0b\u6709\u51e0\u6761", "\u8d1f\u8d23\u7684", "\u9500\u552e\u7684")) {
            Optional<String> name = nameBeforeUnicode(value, suffix);
            if (name.isPresent()) {
                return name;
            }
        }
        java.util.regex.Matcher possessive = java.util.regex.Pattern
            .compile("^([\\p{IsHan}]{2,5}|[A-Za-z][A-Za-z0-9._-]{1,30})\u7684(?:\u5546\u673a|\u9879\u76ee|\u5ba2\u6237|\u7ebf\u7d22|\u8ba2\u5355).*(?:\u591a\u5c11|\u51e0\u4e2a|\u51e0\u6761|\u91d1\u989d).*$")
            .matcher(value);
        if (possessive.matches()) {
            return Optional.of(cleanOwnerNameUnicode(possessive.group(1))).filter(text -> !text.isBlank());
        }
        java.util.regex.Matcher direct = java.util.regex.Pattern
            .compile("^([\\p{IsHan}]{2,5}|[A-Za-z][A-Za-z0-9._-]{1,30})(?:\u6709\u591a\u5c11|\u6709\u51e0\u4e2a|\u6709\u51e0\u6761)(?:\u5546\u673a|\u9879\u76ee|\u5ba2\u6237|\u7ebf\u7d22|\u8ba2\u5355).*$")
            .matcher(value);
        if (direct.matches()) {
            return Optional.of(cleanOwnerNameUnicode(direct.group(1))).filter(text -> !text.isBlank());
        }
        java.util.regex.Matcher explicit = java.util.regex.Pattern
            .compile("(?:\u8d1f\u8d23\u4eba|\u9500\u552e|\u4e1a\u52a1\u5458|owner)(?:\u662f|\u4e3a|=|:|\uff1a)?([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z0-9._-]{1,15})")
            .matcher(value);
        if (explicit.find()) {
            return Optional.of(cleanOwnerNameUnicode(explicit.group(1))).filter(text -> !text.isBlank());
        }
        return Optional.empty();
    }

    private Optional<String> nameBeforeUnicode(String value, String suffix) {
        int index = value.indexOf(suffix);
        if (index <= 0) {
            return Optional.empty();
        }
        return Optional.of(cleanOwnerNameUnicode(value.substring(0, index))).filter(text -> !text.isBlank());
    }

    private String cleanOwnerNameUnicode(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
            .replaceAll("^(?:(\u8bf7\u95ee|\u5e2e\u6211\u67e5|\u67e5\u8be2|\u7edf\u8ba1|\u5206\u6790|\u7cfb\u7edf|\u73b0\u5728|\u5f53\u524d))+", "")
            .replaceAll("", "")
            .trim();
        return isLikelyPersonName(cleaned) ? cleaned : "";
    }

    private String cleanOwnerName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
            .replaceAll("^(?:(请问|帮我查|帮我看看|查询|统计|分析|系统|现在|当前))+", "")
            .replaceAll("(的)?业务对象.*$", "")
            .trim();
        return isLikelyPersonName(cleaned) ? cleaned : "";
    }

    private boolean isOwnerFieldHint(MetadataExecutionPlanner.FieldHint hint) {
        if (hint == null) {
            return false;
        }
        String value = compact(hint.name() + hint.code() + hint.description());
        return value.contains("负责人")
            || value.contains("销售")
            || value.contains("业务员")
            || value.contains("归属销售")
            || value.contains("owner")
            || value.contains("sales")
            || value.contains("seller");
    }

    private boolean shouldAskModelPlanner(AgentObservation observation, String detectedIntent, MetadataSearchResult match, IntentSlots slots) {
        // Every non-empty request is offered to the model planner. The planner can only
        // enrich a metadata-grounded candidate; it never bypasses metadata, risk, or confirmation checks.
        return observation != null && !normalizeText(observation.normalizedUserGoal()).isBlank();
    }

    private String fieldHintSummary(MetadataExecutionPlanner.FieldHint hint) {
        return hint.name() + "(" + hint.code() + ", " + hint.dataType() + (hint.reference() ? ", reference" : "") + ")";
    }

    private Map<String, Object> runtimeReasoningContext(AgentObservation observation, AgentThought thought, List<RuntimeQueryFilter> runtimeFilters, List<String> metricFieldCodes) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("pipeline", List.of(
            "user_input",
            "local_cloudpivot_metadata_search",
            "metadata_entity_field_relation_api_planning",
            "llm_execution_path_reasoning",
            "cloudpivot_runtime_api_call",
            "llm_result_summary"
        ));
        context.put("userInput", observation.normalizedUserGoal());
        context.put("effectiveUserGoal", observation.effectiveUserGoal());
        context.put("inheritedRuntimeObject", observation.inheritedRuntimeObject());
        context.put("intent", thought.intent());
        context.put("detectedIntent", thought.detectedIntent());
        context.put("confidence", thought.confidence());
        context.put("entity", Map.of(
            "name", safeContextValue(thought.match().name()),
            "schemaCode", safeContextValue(thought.match().code()),
            "objectType", safeContextValue(thought.match().objectType()),
            "reason", safeContextValue(thought.match().reason())
        ));
        context.put("dataItems", thought.executionPlan().fieldHints().stream().map(hint -> Map.of(
            "name", safeContextValue(hint.name()),
            "code", safeContextValue(hint.code()),
            "dataType", safeContextValue(hint.dataType()),
            "reference", hint.reference(),
            "description", safeContextValue(hint.description())
        )).toList());
        context.put("relations", thought.executionPlan().relationHints().stream().map(relation -> Map.of(
            "sourceEntityName", safeContextValue(relation.sourceEntityName()),
            "sourceSchemaCode", safeContextValue(relation.sourceSchemaCode()),
            "targetEntityName", safeContextValue(relation.targetEntityName()),
            "targetSchemaCode", safeContextValue(relation.targetSchemaCode()),
            "relationName", safeContextValue(relation.relationName()),
            "relationType", safeContextValue(relation.relationType()),
            "sourceDataItemId", safeContextValue(relation.sourceDataItemId())
        )).toList());
        context.put("apiActions", thought.executionPlan().apiHints().stream().map(api -> Map.of(
            "apiCode", safeContextValue(api.apiCode()),
            "name", safeContextValue(api.name()),
            "method", safeContextValue(api.method()),
            "path", safeContextValue(api.path()),
            "operationType", safeContextValue(api.operationType()),
            "riskLevel", safeContextValue(api.riskLevel()),
            "requiresConfirmation", api.requiresConfirmation(),
            "dataScope", safeContextValue(api.dataScope())
        )).toList());
        context.put("runtimeFilters", runtimeFilters == null ? List.of() : runtimeFilters.stream().map(filter -> Map.of(
            "fieldCode", safeContextValue(filter.fieldCode()),
            "fieldName", safeContextValue(filter.fieldName()),
            "operator", safeContextValue(filter.operator()),
            "value", safeContextValue(filter.value()),
            "source", safeContextValue(filter.source()),
            "confidence", filter.confidence()
        )).toList());
        context.put("metricFieldCodes", metricFieldCodes == null ? List.of() : metricFieldCodes);
        context.put("executionPlanSummary", thought.executionPlan().summary());
        context.put("modelPlanning", modelPlanningContext(thought));
        return context;
    }

    private Map<String, Object> modelPlanningContext(AgentThought thought) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("used", thought.usedModelPlan());
        value.put("intent", thought.modelPlan().intent());
        value.put("actionLabel", thought.modelPlan().actionLabel());
        value.put("businessObject", thought.modelPlan().businessObject());
        value.put("dimension", thought.modelPlan().dimension());
        value.put("filters", thought.modelPlan().filters());
        value.put("apiOperation", thought.modelPlan().apiOperation());
        value.put("executionSteps", thought.modelPlan().executionSteps());
        value.put("plannedRuntimeFilters", thought.modelPlan().runtimeFilters());
        value.put("plannedMetricFieldCodes", thought.modelPlan().metricFieldCodes());
        value.put("plannedGroupByFieldCodes", thought.modelPlan().groupByFieldCodes());
        value.put("plannedSortFields", thought.modelPlan().sortFields());
        value.put("resultLimit", thought.modelPlan().resultLimit());
        value.put("requiresConfirmation", thought.modelPlan().requiresConfirmation());
        value.put("reasoning", thought.modelPlan().reasoning());
        return value;
    }

    private String safeContextValue(String value) {
        return value == null ? "" : value;
    }

    private boolean isShortFollowUp(String value) {
        return value.length() <= 28 || containsAny(value, "这些", "这个", "上述", "刚才", "上一条", "它们", "都", "分别", "第一条", "第一个");
    }

    private boolean looksLikeMetricOrBusinessAnalysis(String value) {
        return questionSemantics.looksLikeAnalysis(value);
    }

    private boolean shouldUseModelPlan(IntentPlanningResult modelPlan) {
        return modelPlan != null && modelPlan.confidence() >= 0.65 && !normalizeText(modelPlan.intent()).isBlank();
    }

    private String normalizeIntent(String modelIntent, String fallback) {
        String value = normalizeText(modelIntent);
        return switch (value) {
            case "query_data", "analyze_data", "create_data", "update_data", "delete_data", "clarify_intent" -> value;
            default -> fallback;
        };
    }

    private IntentSlots mergeIntentSlots(IntentSlots deterministic, IntentPlanningResult modelPlan) {
        return new IntentSlots(
            firstText(modelPlan.actionLabel(), deterministic.actionLabel()),
            firstText(modelPlan.businessObject(), deterministic.businessObject()),
            mergeSlot(deterministic.dimension(), modelPlan.dimension(), "无明确维度", "业务概览"),
            mergeSlot(deterministic.filters(), modelPlan.filters(), "无明确筛选条件")
        );
    }

    private String mergeSlot(String deterministic, String modelValue, String... weakValues) {
        String ruleValue = normalizeText(deterministic);
        String plannedValue = normalizeText(modelValue);
        if (plannedValue.isBlank()) {
            return deterministic;
        }
        boolean ruleIsWeak = ruleValue.isBlank();
        for (String weakValue : weakValues) {
            if (weakValue.equals(ruleValue)) {
                ruleIsWeak = true;
                break;
            }
        }
        return ruleIsWeak ? plannedValue : deterministic;
    }

    private List<String> mergeMissingSlots(List<String> missingSlots, String slot) {
        List<String> values = new ArrayList<>(missingSlots == null ? List.of() : missingSlots);
        if (!values.contains(slot)) {
            values.add(slot);
        }
        return values;
    }

    private String modelPlanReasoning(IntentPlanningResult modelPlan, boolean usedModelPlan) {
        if (!usedModelPlan || modelPlan == null) {
            return "；模型规划=未触发或未采纳，使用确定性快速路径";
        }
        String reasoning = firstText(modelPlan.reasoning(), "模型基于上下文和元数据补全意图");
        return "；模型规划=已采纳，" + shortText(reasoning, 120);
    }

    /**
     * Returns an auditable decision summary, never provider reasoning tokens or a
     * hidden chain-of-thought. It is safe to show in the task timeline.
     */
    private String modelReasoningStepSummary(AgentThought thought) {
        if (thought == null) {
            return "未能形成可执行任务判断，后续将请求用户补充信息。";
        }
        String objectName = thought.match() == null ? "业务对象" : firstText(thought.match().name(), "业务对象");
        String base = "已识别为“" + intentDisplayName(thought.intent()) + "”，目标对象为“" + objectName + "”。";
        if (!thought.usedModelPlan()) {
            return base + "模型规划不可用或未采纳，已按元数据约束生成受限处理方案。";
        }
        String summary = firstText(thought.modelPlan().reasoning(), "模型已结合当前问题、会话上下文和可用元数据确定处理策略");
        return base + "模型分析要点：" + shortText(maskText(summary).replaceAll("\\s+", " "), 180)
            + "；该摘要仅说明决策依据，不展示模型原始思维链。";
    }

    private String firstText(String preferred, String fallback) {
        String value = normalizeText(preferred);
        return value.isBlank() ? fallback : value;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String reasoningSummary(String intent, String detectedIntent, MetadataSearchResult match, List<String> missingSlots, AgentObservation observation, IntentSlots slots) {
        if (!missingSlots.isEmpty()) {
            return "检测到动作=" + slots.actionLabel() + "、业务对象=" + slots.businessObject() + "，但缺少" + String.join("、", missingSlots) + "，因此先澄清。";
        }
        if ("clarify_intent".equals(intent)) {
            return "已尝试识别动作=" + slots.actionLabel() + "、业务对象=" + slots.businessObject() + "，但无法稳定匹配真实云枢模型，进入澄清。";
        }
        if (observation.inheritedRuntimeObject()) {
            return "已继承上一轮运行态对象=" + observation.runtimeContext().entityName() + "、schemaCode=" + observation.runtimeContext().schemaCode()
                + "；检测到动作=" + slots.actionLabel() + "、业务对象=" + slots.businessObject() + "、分析维度=" + slots.dimension() + "，匹配对象“" + match.name() + "”，可进入计划执行。";
        }
        return "检测到动作=" + slots.actionLabel() + "、业务对象=" + slots.businessObject() + "、分析维度=" + slots.dimension() + "，匹配对象“" + match.name() + "”，可进入计划执行。";
    }

    private IntentSlots extractIntentSlots(String content, String detectedIntent) {
        String action = switch (detectedIntent) {
            case "query_data" -> containsAny(content, List.of("多少", "几条", "几个", "数量", "统计", "总计", "一共", "总共", "共有")) ? "计数/统计" : "查询";
            case "analyze_data" -> "分析";
            case "create_data" -> "新增/填单";
            case "update_data" -> "修改/写入";
            case "delete_data" -> "删除/作废";
            case "fill_form_from_attachment" -> "附件填单";
            default -> "未明确";
        };
        return new IntentSlots(action, businessSubject(content), analysisDimension(content), filterSummary(content));
    }

    private String analysisDimension(String content) {
        return questionSemantics.analysisDimension(content);
    }
    private String filterSummary(String content) {
        return questionSemantics.filterSummary(content);
    }

    private String ownerFilterFromQuestion(String content) {
        return questionSemantics.ownerFilter(content);
    }

    private String cleanOwnerFilterName(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value
            .replaceAll("^(?:(请问|帮我查|帮我看看|查询|统计|分析|系统|现在|当前))+", "")
            .replaceAll("(名下|负责|销售|负责人|业务员|归属销售|有多少|有几个|有几条|多少|几个|几条|数据|信息|情况)+$", "")
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
        if (text.contains("应用") || text.contains("系统") || text.contains("数据") || text.contains("基础")
            || text.contains("现在") || text.contains("当前") || text.contains("今天") || text.contains("今年") || text.contains("本月") || text.contains("全部") || text.contains("所有")) {
            return false;
        }
        return text.matches("[\\p{IsHan}]{2,5}") || text.matches("[A-Za-z][A-Za-z0-9._-]{1,30}");
    }

    private String buildPlanSummary(String intent, MetadataSearchResult match, boolean writeRisk) {
        if (writeRisk) {
            return "已准备" + intentDisplayName(intent) + "“" + match.name() + "”。该操作会修改云枢数据，等待你确认后才会继续。";
        }
        if ("clarify_intent".equals(intent) || "unknown".equals(intent)) {
            return "当前请求信息不足，需要先向用户澄清业务对象、动作或筛选条件。";
        }
        if ("analyze_data".equals(intent)) {
            return "已识别为分析类请求，匹配本地元数据“" + match.name() + "”，将先查询云枢运行态数据，再生成业务分析。";
        }
        if (isWorkflowIntent(intent)) {
            return "已识别为流程中心任务，先经过登录环境接口契约验证和权限校验，再决定是否查询或生成确认计划。";
        }
        return "已识别为查询类请求，匹配本地元数据“" + match.name() + "”，将调用云枢运行态接口查询真实数据。";
    }

    private String unmatchedReadMessage(String intent, String content) {
        if ("analyze_data".equals(intent)) {
            return "我理解你想分析“" + businessSubject(content) + "”相关数据，但当前本地 Metadata Index 还没有匹配到真实可查询的云枢应用模型。请先在元数据页同步真实云枢元数据，或补充应用/表单名称；同步后我会按“真实 schemaCode -> 运行态查询 -> 大模型分析”的链路返回结论。";
        }
        return "我已识别到这是查询请求，目标对象是“" + businessSubject(content) + "”，但当前没有从真实云枢元数据中匹配到可查询的应用模型。请先同步真实云枢元数据，或补充更明确的应用名称、表单名称。我不会用本地演示数据返回业务结果。";
    }

    private String workflowCapabilityMessage(String content, String intent) {
        if ("workflow_action".equals(intent)) {
            return "我识别到这是流程处理请求（例如同意、驳回、转交或终止）。流程中心接口已纳入本地元数据，但当前尚未在登录后的云枢环境核验请求方法、参数、权限和意见规则，因此本轮不会执行任何流程写操作。完成只读契约验证后，系统会先展示具体工作项、影响范围和确认计划，再等待你的明确确认。";
        }
        return "我识别到这是流程中心查询请求。当前本地元数据已登记“我的待办、我的已办、我发起的流程、流程实例、流程节点”等对象；但由于提供的云枢页面尚未登录，接口的分页参数和返回字段仍未完成真实契约验证，本轮不会用猜测接口返回数据。请先完成云枢登录并允许只读探查，之后才能查询真实流程数据。";
    }

    private String workflowApiCode(String content) {
        String value = compact(content);
        if (containsAny(value, "已办", "已处理", "完成工作项")) return "workflow_list_finished";
        if (containsAny(value, "我发起", "我发起的流程", "发起的流程")) return "workflow_list_started";
        return "workflow_list_pending";
    }

    private String workflowAnswer(String content, WorkflowReadResult result) {
        String label = switch (result.apiCode()) {
            case "workflow_list_finished" -> "我的已办";
            case "workflow_list_started" -> "我发起的流程";
            default -> "我的待办";
        };
        StringBuilder answer = new StringBuilder("已通过云枢流程中心查询“").append(label).append("”，共 **").append(result.total()).append("** 条。\n\n");
        if (result.items().isEmpty()) return answer.append("当前没有返回可展示的记录。").toString();
        answer.append("| 流程/事项 | 状态 | 时间 |\n| --- | --- | --- |\n");
        result.items().stream().limit(10).forEach(item -> answer.append("| ")
            .append(maskText(firstWorkflowValue(item, "workflowName", "name", "title", "instanceName", "subject")))
            .append(" | ").append(maskText(firstWorkflowValue(item, "status", "workflowStatus", "state")))
            .append(" | ").append(maskText(firstWorkflowValue(item, "createdTime", "createTime", "startTime", "receiveTime"))).append(" |\n"));
        return answer.toString();
    }

    private String firstWorkflowValue(Map<String, Object> item, String... keys) {
        for (String key : keys) {
            Object value = item.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "—";
    }

    private String workflowFailureMessage(String apiCode, RuntimeException exception) {
        String message = exception.getMessage() == null ? "真实接口调用失败" : shortText(exception.getMessage(), 180);
        return "流程中心查询未完成（接口：" + apiCode + "）。系统没有猜测或调用写接口。\n\n错误摘要：" + message + "\n请先在系统设置中保存普通用户云枢连接，并执行流程只读接口探查。";
    }

    private String runtimeFailureMessage(String content, MetadataSearchResult match, RuntimeException exception) {
        String error = exception.getMessage() == null ? "云枢运行态查询失败" : exception.getMessage();
        return "我已经理解你的问题，但这次没有成功查到真实云枢数据，所以不能直接回答结果。请检查普通用户云枢连接、账号权限、运行态接口是否可访问，或重新同步元数据后再试。\n\n"
            + "错误摘要：" + shortText(error, 180);
    }

    private String businessSubject(String content) {
        String value = content == null ? "业务对象" : content;
        value = value.replaceAll("(一共|总共|共有)?有?多少[条个项笔份单]?", " ");
        value = value.replaceAll("(一共|总共|共有)?有?几[条个项笔份单]?", " ");
        for (String noise : List.of("帮我", "请", "一下", "系统中的", "系统中", "系统", "信息", "数据", "情况", "怎么样", "怎么", "如何", "进行", "处理", "操作", "做", "分析", "洞察", "诊断", "趋势", "建议", "查询", "查看", "统计", "汇总", "数量", "总计", "每年", "按年", "年度", "年份", "量", "的")) {
            value = value.replace(noise, " ");
        }
        value = value.replaceAll("[\\p{Punct}，。；：、？！]+", " ");
        String subject = value.trim().replaceAll("\\s+", " ");
        return subject.isBlank() ? "业务对象" : subject;
    }

    private String clarificationMessage(String content, MetadataSearchResult match) {
        return clarificationMessage(content, match, List.of());
    }

    private String clarificationMessage(String content, MetadataSearchResult match, List<String> missingSlots) {
        StringBuilder message = new StringBuilder();
        message.append("我需要再确认一下你的意图，这样才能避免查错或误操作。\n\n");
        message.append("我目前理解到的内容：你可能想处理“").append(businessSubject(content)).append("”相关事项。\n");
        if (!missingSlots.isEmpty()) {
            message.append("当前还缺少：").append(String.join("、", missingSlots)).append("。\n");
        }
        if (canQueryRuntime(match)) {
            message.append("我找到了一个可能相关的真实云枢对象：“").append(match.name()).append("”，编码 `").append(match.code()).append("`。\n");
        } else if (match != null && match.name() != null && !"未匹配到本地元数据".equals(match.name())) {
            message.append("我找到的候选还不能直接查询：“").append(match.name()).append("”。\n");
        } else {
            message.append("我暂时没有从已同步的真实云枢元数据中匹配到可直接查询的应用模型。\n");
        }
        message.append("\n请你补充一个方向即可：\n");
        message.append("1. 你想查询/分析哪个应用或表单？请提供应用名、表单名或业务对象名称。\n");
        message.append("2. 你想做什么动作？例如：查询、统计、分析、修改、新增。\n");
        message.append("3. 有没有筛选条件？例如：时间、负责人、阶段或其他字段。\n");
        String suggestions = metadataSuggestions();
        if (!suggestions.isBlank()) {
            message.append("\n当前我能看到的部分对象：").append(suggestions).append("。");
        }
        return message.toString();
    }

    private String metadataSuggestions() {
        return metadataSearchService.suggestAvailableMetadata(5).stream()
            .map(MetadataSearchResult::name)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .reduce((left, right) -> left + "、" + right)
            .orElse("");
    }

    private MessageItem withContent(MessageItem message, String content) {
        return withContent(message, content, message.metadataJson());
    }

    private MessageItem withContent(MessageItem message, String content, String metadataJson) {
        return new MessageItem(message.id(), message.role(), maskText(content), message.createdAt(), metadataJson);
    }

    private String maskText(String value) {
        return sensitiveDataMasker.mask(value);
    }

    private MessageItem withAgentRunMetadata(MessageItem message, String agentRunId, String defaultSource, long thinkingElapsedMs, long answerElapsedMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (message.metadataJson() != null && !message.metadataJson().isBlank()) {
            try {
                metadata.putAll(objectMapper.readValue(message.metadataJson(), new TypeReference<>() {}));
            } catch (JsonProcessingException ignored) {
                metadata.put("source", defaultSource);
            }
        }
        if (!metadata.containsKey("source") || String.valueOf(metadata.getOrDefault("source", "")).isBlank()) {
            metadata.put("source", defaultSource);
        }
        metadata.put("agentRunId", agentRunId);
        metadata.put("thinkingElapsedMs", thinkingElapsedMs);
        metadata.put("answerElapsedMs", answerElapsedMs);
        return withContent(message, message.content(), toJson(metadata));
    }

    private long elapsedMillis(long startedAtNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private String defaultMetadataSource(ActResult actResult) {
        if ("insight_report_completed".equals(actResult.status())) return "insight-report";
        return "runtime_query_completed".equals(actResult.status()) ? "runtime-query" : "runtime-agent";
    }
    private List<ExecutionStepDto> reactSteps(AgentObservation observation, AgentThought thought, ActResult actResult, AgentReflection reflection) {
        List<ExecutionStepDto> steps = new ArrayList<>();
        steps.add(new ExecutionStepDto(
            "理解用户意图",
            modelReasoningStepSummary(thought),
            "understand",
            "completed",
            "thought",
            Map.of("mode", thought.usedModelPlan() ? "ai-reasoning-summary" : "rule-fallback"),
            0L
        ));
        if (thought.usedModelPlan() && thought.modelPlan().executionSteps() != null && !thought.modelPlan().executionSteps().isEmpty()) {
            for (Map<String, Object> plannedStep : thought.modelPlan().executionSteps()) {
                String description = firstText(String.valueOf(plannedStep.getOrDefault("description", "")), "已形成受约束的业务任务节点");
                String title = firstText(String.valueOf(plannedStep.getOrDefault("title", "")),
                    firstText(String.valueOf(plannedStep.getOrDefault("name", "")),
                        firstText(String.valueOf(plannedStep.getOrDefault("step", "")), shortText(description, 28))));
                steps.add(new ExecutionStepDto(title, description, "plan", "completed", "thought", Map.of("mode", "ai-planned"), 0L));
            }
        } else {
            steps.add(new ExecutionStepDto("确定分析范围", "已根据可用业务数据和安全规则确定本轮分析范围。", "plan", "completed", "progress", Map.of("mode", "rule-fallback"), 0L));
        }
        steps.add(new ExecutionStepDto(executionOutcomeTitle(actResult), actStepSummary(actResult), "act", "completed", "execution", Map.of(), 0L));
        return steps;
    }

    private void emitDynamicPlan(AgentProgressListener progress, AgentThought thought) {
        if (thought.usedModelPlan() && thought.modelPlan().executionSteps() != null && !thought.modelPlan().executionSteps().isEmpty()) {
            for (Map<String, Object> plannedStep : thought.modelPlan().executionSteps()) {
                String description = firstText(String.valueOf(plannedStep.getOrDefault("description", "")), "已形成受约束的业务任务节点");
                String title = firstText(String.valueOf(plannedStep.getOrDefault("title", "")),
                    firstText(String.valueOf(plannedStep.getOrDefault("name", "")),
                        firstText(String.valueOf(plannedStep.getOrDefault("step", "")), shortText(description, 28))));
                progress.onExecution(title, description, Map.of("mode", "ai-planned"), "completed");
            }
            return;
        }
        progress.onExecution("确定分析范围", "已根据可用业务数据和安全规则确定本轮分析范围。", Map.of("mode", "rule-fallback"), "completed");
    }

    private String executionOutcomeTitle(ActResult actResult) {
        if (actResult.insightResult() != null) return "完成关联分析";
        if (actResult.runtimeAnswer() != null) return "返回业务数据";
        if (actResult.confirmation() != null) return "生成待确认操作";
        return "完成本轮执行";
    }

    private String observeStepSummary(AgentObservation observation) {
        return "已识别用户目标“" + shortText(observation.normalizedUserGoal(), 40) + "”；"
            + (observation.contextualFollowUp() ? "本轮是对上一轮回答的延续/深化，将结合上一轮主题和反馈重新组织。" : (observation.inheritedRuntimeObject() ? "本轮承接上一轮已确认的业务范围。" : "将根据本轮目标重新核验业务范围。"))
            + (observation.referencesPreviousResult() ? "问题引用了前序结果。" : "");
    }

    private String thinkStepSummary(AgentThought thought) {
        String slots = thought.missingSlots().isEmpty() ? "无" : String.join("、", thought.missingSlots());
        return "任务类型：" + intentDisplayName(thought.intent()) + "；目标对象：“" + thought.match().name()
            + "”；处理方式：" + thought.slots().actionLabel()
            + "；分析范围：" + thought.slots().dimension()
            + "；筛选范围：" + thought.slots().filters()
            + "；" + (thought.usedModelPlan() ? "AI 已基于问题和业务元数据拆解任务。" : "模型规划不可用，已生成受限任务方案。")
            + "；待补充信息：" + slots;
    }

    private String timelinePlanSummary(AgentThought thought) {
        String filters = thought.slots().filters();
        String filterSummary = filters == null || filters.isBlank() || filters.contains("无明确")
            ? "未设置筛选条件"
            : "筛选条件：" + filters;
        String dimension = thought.slots().dimension();
        String dimensionSummary = dimension == null || dimension.isBlank() || dimension.contains("无明确")
            ? ""
            : "；分析维度：" + dimension;
        return "已识别为“" + intentDisplayName(thought.intent()) + "”；处理对象：" + thought.match().name()
            + "；处理方式：" + thought.slots().actionLabel() + dimensionSummary + "；" + filterSummary
            + "；" + (thought.usedModelPlan() ? "已由 AI 结合当前问题拆解执行任务。" : "已按受限元数据规则生成执行方案。") + "。";
    }

    private String modelExecutionPlanSummary(IntentPlanningResult modelPlan) {
        if (modelPlan == null || modelPlan.confidence() <= 0D) {
            return "";
        }
        return "；AI执行计划：apiOperation=" + modelPlan.apiOperation()
            + "，runtimeFilters=" + modelPlan.runtimeFilters()
            + "，metricFieldCodes=" + modelPlan.metricFieldCodes()
            + "，groupByFieldCodes=" + modelPlan.groupByFieldCodes();
    }

    private String actStepSummary(ActResult actResult) {
        if (actResult.insightResult() != null) {
            InsightExecutionResult result = actResult.insightResult();
            return "已围绕“" + result.primaryEntityName() + "”完成关联数据读取与经营分析；已生成关键指标、图表、风险结论和关联追问。";
        }
        if (actResult.runtimeAnswer() != null) {
            CloudPivotQueryAnswer answer = actResult.runtimeAnswer();
            return "已获取“" + answer.entityName() + "”数据；数据总数=" + answer.total() + "；本次参与处理=" + answer.returnedRecords() + " 条。";
        }
        return "处理状态=" + statusDisplayName(actResult.status());
    }

    private String reflectStepSummary(AgentReflection reflection) {
        return "校验结果=" + statusDisplayName(reflection.status()) + "；数据可返回=" + yesNo(reflection.passed()) + "；需要补充信息=" + yesNo(reflection.needsUserInput()) + "；" + reflection.summary();
    }

    private String timelineReflectionSummary(AgentReflection reflection) {
        if (reflection.needsUserInput()) {
            return "当前结果还不能安全执行，需要用户补充或确认信息。";
        }
        if (!reflection.passed()) {
            return "执行结果校验未通过，系统已停止返回未经验证的业务结论。";
        }
        return "数据来源和执行结果已校验，可以返回用户。";
    }

    private String intentDisplayName(String intent) {
        return switch (intent == null ? "" : intent) {
            case "query_data" -> "查询数据";
            case "analyze_data" -> "分析数据";
            case "summarize_data" -> "汇总数据";
            case "create_data" -> "新增数据";
            case "update_data" -> "修改数据";
            case "delete_data" -> "删除数据";
            case "clarify_intent" -> "补充信息";
            default -> intent == null || intent.isBlank() ? "未识别" : intent;
        };
    }

    /**
     * The audit-facing business intent is separate from the internal execution category
     * used for policy and confirmation checks. It cannot grant a capability.
     */
    private String resolveBusinessIntent(String userMessage, AgentThought thought) {
        String planned = thought.usedModelPlan() ? normalizeText(thought.modelPlan().businessIntent()) : "";
        if (!planned.isBlank()) {
            return shortText(planned, 120);
        }
        String action = switch (thought.intent()) {
            case "analyze_data" -> "分析";
            case "query_data" -> "查询";
            case "create_data" -> "新增";
            case "delete_data" -> "删除";
            case "update_data" -> "处理";
            case "clarify_intent" -> "澄清";
            default -> "处理";
        };
        String object = normalizeText(thought.match() == null ? "" : thought.match().name());
        if (object.isBlank()) {
            object = shortText(normalizeText(businessSubject(userMessage)), 80);
        }
        return shortText(action + (object.isBlank() ? "业务请求" : object), 120);
    }

    private String statusDisplayName(String status) {
        return switch (status == null ? "" : status) {
            case "runtime_query_completed", "insight_report_completed", "completed" -> "已完成";
            case "runtime_query_failed" -> "云枢查询失败";
            case "clarification_sent", "needs_user_input" -> "需要用户补充";
            case "pending_confirmation" -> "等待用户确认";
            case "no_runtime_candidate" -> "未找到可查询对象";
            case "fallback_clarification" -> "已转入澄清";
            case "blocked" -> "已阻断";
            default -> status == null || status.isBlank() ? "未知" : status;
        };
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private Map<String, Object> planAuditMap(AgentObservation observation, AgentThought thought, AgentPlan plan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mode", "react-reflection-mvp");
        Map<String, Object> observe = new LinkedHashMap<>();
        observe.put("normalizedUserGoal", observation.normalizedUserGoal());
        observe.put("effectiveUserGoal", observation.effectiveUserGoal());
        observe.put("contextualGoal", observation.contextualGoal());
        observe.put("recentMessageCount", observation.recentMessages().size());
        observe.put("contextualFollowUp", observation.contextualFollowUp());
        observe.put("previousAssistantFeedback", observation.previousAssistantFeedback() == null ? "" : observation.previousAssistantFeedback());
        observe.put("referencesPreviousResult", observation.referencesPreviousResult());
        observe.put("hasPreviousAssistantResult", observation.hasPreviousAssistantResult());
        observe.put("inheritedRuntimeObject", observation.inheritedRuntimeObject());
        observe.put("runtimeContextSchema", observation.runtimeContext().schemaCode());
        observe.put("recalledMemoryCount", observation.recalledMemory().size());
        value.put("observe", observe);
        Map<String, Object> think = new LinkedHashMap<>();
        think.put("detectedIntent", thought.detectedIntent());
        think.put("intent", thought.intent());
        think.put("businessIntent", resolveBusinessIntent(observation.effectiveUserGoal(), thought));
        think.put("confidence", thought.confidence());
        think.put("action", thought.slots().actionLabel());
        think.put("businessObject", thought.slots().businessObject());
        think.put("dimension", thought.slots().dimension());
        think.put("filters", thought.slots().filters());
        think.put("metadataObject", thought.match().name());
        think.put("metadataCode", thought.match().code());
        think.put("missingSlots", thought.missingSlots());
        think.put("modelPlanUsed", thought.usedModelPlan());
        think.put("modelPlanIntent", thought.modelPlan().intent());
        think.put("modelPlanReasoning", thought.modelPlan().reasoning());
        think.put("modelPlanApiOperation", thought.modelPlan().apiOperation());
        think.put("modelPlanExecutionSteps", thought.modelPlan().executionSteps());
        think.put("modelPlanRuntimeFilters", thought.modelPlan().runtimeFilters());
        think.put("modelPlanMetricFieldCodes", thought.modelPlan().metricFieldCodes());
        think.put("modelPlanGroupByFieldCodes", thought.modelPlan().groupByFieldCodes());
        think.put("modelPlanSortFields", thought.modelPlan().sortFields());
        think.put("executionPlan", thought.executionPlan().summary());
        think.put("fieldHints", thought.executionPlan().fieldHints().stream().map(MetadataExecutionPlanner.FieldHint::displayName).toList());
        think.put("relationHints", thought.executionPlan().relationHints().stream().map(MetadataExecutionPlanner.RelationHint::summary).toList());
        think.put("apiHints", thought.executionPlan().apiHints().stream().map(MetadataExecutionPlanner.ApiHint::summary).toList());
        think.put("reasoningSummary", thought.reasoningSummary());
        value.put("think", think);
        value.put("plan", Map.of(
            "summary", plan.summary(),
            "riskLevel", plan.riskLevel(),
            "requiresConfirmation", plan.requiresConfirmation(),
            "actions", plan.actions()
        ));
        return value;
    }

    private Map<String, Object> reflectionAuditMap(AgentReflection reflection) {
        return Map.of(
            "mode", "react-reflection-mvp",
            "status", reflection.status(),
            "passed", reflection.passed(),
            "needsUserInput", reflection.needsUserInput(),
            "checks", reflection.checks(),
            "summary", reflection.summary()
        );
    }

    private Map<String, Object> writeOperationPlan(String conversationId, String intent, MetadataSearchResult match, String effectiveGoal) {
        if (!"delete_data".equals(intent)) {
            return Map.of(
                "operation", intent,
                "businessObject", match.name(),
                "action", intentDisplayName(intent)
            );
        }
        QueryResultReferenceService.ResultReferenceTarget reference = queryResultReferenceService.resolveOrdinal(conversationId, match.code(), effectiveGoal);
        RuntimeRecordTarget target = new RuntimeRecordTarget(reference.schemaCode(), reference.recordId());
        String appCode = resolveAppCode(match, target.schemaCode());
        if (appCode.isBlank()) {
            throw new IllegalArgumentException("未能从云枢元数据中找到业务对象所属应用编码，无法执行删除");
        }
        return Map.of(
            "operation", intent,
            "appCode", appCode,
            "schemaCode", target.schemaCode(),
            "bizObjectId", target.bizObjectId(),
            "referenceId", reference.referenceId(),
            "sourceMessageId", reference.messageId(),
            "sourceAgentRunId", reference.agentRunId(),
            "rowIndex", reference.rowIndex(),
            "referenceExpiresAt", reference.expiresAt().toString()
        );
    }

    private String resolveAppCode(MetadataSearchResult match, String schemaCode) {
        if (match != null && match.objectId() != null && !match.objectId().isBlank()) {
            java.util.Optional<String> appCode = entityRepository.findById(match.objectId())
                .flatMap(entity -> appRepository.findById(entity.getAppId()))
                .map(app -> app.getAppCode() == null ? "" : app.getAppCode().trim())
                .filter(value -> !value.isBlank());
            if (appCode.isPresent()) {
                return appCode.get();
            }
        }
        return entityRepository.findByEntityCodeIgnoreCase(schemaCode).stream()
            .findFirst()
            .flatMap(entity -> appRepository.findById(entity.getAppId()))
            .map(app -> app.getAppCode() == null ? "" : app.getAppCode().trim())
            .orElse("");
    }

    private String confirmationRequiredMessage(String intent, Map<String, Object> writePlan) {
        if ("delete_data".equals(intent)) {
            return "已定位到要删除的云枢记录：应用=" + writePlan.get("appCode")
                + "，业务对象=" + writePlan.get("schemaCode")
                + "，记录ID=" + writePlan.get("bizObjectId")
                + "。删除属于高风险操作，请确认后执行。";
        }
        return "已准备" + writePlan.getOrDefault("action", "执行") + "“" + writePlan.getOrDefault("businessObject", "业务对象")
            + "”。该操作会修改云枢数据，请确认后继续。";
    }

    private String writeClarificationMessage(String intent, String content, MetadataSearchResult match, RuntimeException exception) {
        if ("delete_data".equals(intent)) {
            return "删除操作需要明确到单条云枢记录后才能执行。当前已识别对象为“" + match.name()
                + "”，但还缺少可删除的记录ID或第几条记录信息。请补充记录ID，或先查询列表后再说“删除第几条”。\n\n原因："
                + (exception.getMessage() == null ? "目标记录不明确" : exception.getMessage());
        }
        return clarificationMessage(content, match);
    }

    private boolean containsAny(String content, List<String> tokens) {
        String value = content == null ? "" : content;
        return tokens.stream().anyMatch(value::contains);
    }

    private String shortText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize agent audit data", exception);
        }
    }

    private RuntimeContextObject lastRuntimeContext(List<MessageItem> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageItem message = messages.get(i);
            if (!"assistant".equals(message.role()) || message.metadataJson() == null || message.metadataJson().isBlank()) {
                continue;
            }
            try {
                Map<String, Object> metadata = objectMapper.readValue(message.metadataJson(), new TypeReference<>() {});
                String source = String.valueOf(metadata.getOrDefault("source", ""));
                if (!("runtime-query".equals(source) || "insight-report".equals(source))) {
                    continue;
                }
                String entityName = String.valueOf(metadata.getOrDefault("entityName", ""));
                String schemaCode = String.valueOf(metadata.getOrDefault("schemaCode", ""));
                if (!entityName.isBlank() && !schemaCode.isBlank()) {
                    return new RuntimeContextObject(entityName, schemaCode);
                }
            } catch (JsonProcessingException ignored) {
                // Ignore malformed legacy metadata and continue scanning older messages.
            }
        }
        return new RuntimeContextObject("", "");
    }

    private boolean shouldInheritRuntimeObject(String content, RuntimeContextObject context) {
        if (context == null || context.entityName().isBlank() || content == null || content.isBlank()) {
            return false;
        }
        String value = compact(content);
        boolean followUpDimensionQuestion = containsAny(value, "这些", "上述", "上面", "刚才", "上一轮", "上一条", "这个", "它", "它们", "他们", "该", "都", "分别", "各", "每个", "第一条", "第一个", "第二条", "第二个", "第三条", "第三个", "详情", "详细", "明细", "信息", "内容", "返回", "阶段", "状态", "进行中", "执行中", "实施中", "分布", "占比", "比例", "来源", "负责人", "金额", "收入", "总额", "合计", "汇总", "按", "有哪些", "哪些", "多少", "属于", "省份", "所属省", "哪些省", "哪个省", "地区", "区域", "城市", "地域", "归属地", "所在地", "省市", "哪个多", "谁多", "更多", "对比", "比较", "多", "还是", "分析", "洞察", "诊断", "趋势", "建议", "情况", "怎么样", "怎么看", "概览", "总结");
        if (!followUpDimensionQuestion) {
            return false;
        }
        if (containsBusinessObject(value) && !mentionsSameRuntimeEntity(value, context)) {
            return false;
        }
        return true;
    }
    private boolean mentionsSameRuntimeEntity(String compactContent, RuntimeContextObject context) {
        String entityName = compact(context.entityName());
        return entityName.isBlank() || compactContent.contains(entityName);
    }

    private MetadataSearchResult inheritedMetadataMatch(RuntimeContextObject context) {
        return new MetadataSearchResult(
            "entity",
            "",
            context.entityName(),
            context.schemaCode(),
            "",
            "low",
            "继承上一轮真实运行态对象，跳过重复元数据召回"
        );
    }

    private String inheritedEffectiveGoal(RuntimeContextObject context, String content) {
        StringBuilder goal = new StringBuilder();
        if (context.schemaCode() != null && !context.schemaCode().isBlank()) {
            goal.append(context.schemaCode()).append(' ');
        }
        if (context.entityName() != null && !context.entityName().isBlank()) {
            goal.append(context.entityName()).append(' ');
        }
        goal.append(content == null ? "" : content.trim());
        return goal.toString().trim();
    }

    private String runtimeMetadataJson(CloudPivotQueryAnswer answer, List<QueryResultReference> references) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "runtime-query");
        metadata.put("entityName", answer.entityName());
        metadata.put("schemaCode", answer.schemaCode());
        metadata.put("total", answer.total());
        metadata.put("returnedRecords", answer.returnedRecords());
        metadata.put("sourceEndpoint", answer.sourceEndpoint());
        metadata.put("queryResultReferenceIds", references == null ? List.of() : references.stream().map(QueryResultReference::getId).toList());
        return toJson(metadata);
    }

    private String insightMetadataJson(InsightExecutionResult result) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "insight-report");
        metadata.put("presentationVersion", 1);
        metadata.put("entityName", result.primaryEntityName());
        metadata.put("schemaCode", result.primarySchemaCode());
        metadata.put("total", result.primaryCount());
        metadata.put("returnedRecords", result.primaryCount());
        metadata.put("sourceEndpoint", String.join("、", result.sourceEndpoints()));
        metadata.put("reportSkillId", result.report().skillId());
        metadata.put("reportSkillName", result.report().skillName());
        metadata.put("reportSkillVersion", result.report().templateVersion());
        metadata.put("insightReport", result.report());
        return toJson(metadata);
    }

    private List<Map<String, Object>> safeMemory(List<Map<String, Object>> memory) {
        return memory == null ? List.of() : memory.stream().limit(5).toList();
    }

    private record AgentObservation(
        String normalizedUserGoal,
        String effectiveUserGoal,
        String contextualGoal,
        List<String> recentMessages,
        List<Map<String, Object>> recalledMemory,
        boolean hasPreviousAssistantResult,
        boolean referencesPreviousResult,
        RuntimeContextObject runtimeContext,
        boolean inheritedRuntimeObject,
        boolean contextualFollowUp,
        String previousUserGoal,
        String previousAssistantAnswer,
        String previousAssistantFeedback
    ) {
    }

    private record ContextualTurn(String userGoal, String assistantAnswer, String feedbackType) {
        private static ContextualTurn empty() {
            return new ContextualTurn("", "", null);
        }
    }

    private record RuntimeContextObject(String entityName, String schemaCode) {
    }

    private record AgentThought(String detectedIntent, String intent, MetadataSearchResult match, MetadataExecutionPlan executionPlan, IntentSlots slots, List<String> missingSlots, double confidence, String reasoningSummary, boolean usedModelPlan, IntentPlanningResult modelPlan) {
    }

    private record IntentSlots(String actionLabel, String businessObject, String dimension, String filters) {
    }

    private record AgentPlan(String summary, String riskLevel, boolean requiresConfirmation, List<String> actions) {
    }

    private record ActResult(MessageItem assistantMessage, Confirmation confirmation, String planSummary, CloudPivotQueryAnswer runtimeAnswer, InsightExecutionResult insightResult, String status) {
    }

    private record AgentReflection(String status, boolean passed, boolean needsUserInput, List<String> checks, String summary) {
    }
}
