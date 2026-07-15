package com.cpclaw.agent;

import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.CandidateDto;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.audit.AuditService;
import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.cloudpivot.CloudPivotQueryAnswer;
import com.cpclaw.cloudpivot.CloudPivotRuntimeService;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.search.MetadataSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final MetadataSearchService metadataSearchService;
    private final CloudPivotRuntimeService cloudPivotRuntimeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AgentOrchestrator(
        MetadataSearchService metadataSearchService,
        CloudPivotRuntimeService cloudPivotRuntimeService,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.metadataSearchService = metadataSearchService;
        this.cloudPivotRuntimeService = cloudPivotRuntimeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, String modelConfigId, boolean thinkingEnabled, MessageItem assistantMessage, List<MessageItem> conversationContext) {
        return handleMessage(conversationId, userMessageId, content, modelConfigId, thinkingEnabled, assistantMessage, conversationContext, step -> {
        });
    }

    public AgentResponse handleMessage(
        String conversationId,
        String userMessageId,
        String content,
        String modelConfigId,
        boolean thinkingEnabled,
        MessageItem assistantMessage,
        List<MessageItem> conversationContext,
        Consumer<ExecutionStepDto> progressListener
    ) {
        List<ExecutionStepDto> steps = new ArrayList<>();
        AgentObservation observation = observe(content, conversationContext);
        AgentThought thought = think(content, observation);
        String intent = thought.intent();
        MetadataSearchResult match = thought.match();
        completeStep(
            steps,
            progressListener,
            "理解请求",
            "结合当前输入和最近 " + observation.recentMessages().size() + " 条对话，识别用户希望执行的业务动作。",
            "识别为“" + thought.slots().actionLabel() + "”，目标是“" + thought.slots().businessObject() + "”，意图=" + intent + "。"
        );
        completeStep(
            steps,
            progressListener,
            "定位业务对象",
            "使用用户原话和业务同义词检索已同步的云枢 Metadata Index。",
            metadataConclusion(match)
        );
        boolean writeRisk = isWriteIntent(intent);
        String riskLevel = writeRisk ? "medium" : "low";
        String planSummary = buildPlanSummary(intent, match, writeRisk);
        AgentPlan plan = plan(observation, thought, riskLevel, planSummary);
        String planJson = toJson(planAuditMap(observation, thought, plan));
        AgentRun run = auditService.createAgentRun(conversationId, userMessageId, intent, riskLevel, planJson);
        auditService.recordToolCall(
            run.getId(),
            "metadata_search",
            toJson(Map.of(
                "query", content == null ? "" : content,
                "effectiveQuery", observation.effectiveUserGoal()
            )),
            toJson(Map.of("match", match.name(), "code", match.code(), "objectType", match.objectType()))
        );

        ActResult actResult;
        if ("clarify_intent".equals(intent)) {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match, thought.missingSlots()));
            planSummary = "当前信息不足以安全执行，已向用户发起澄清问题。";
            actResult = new ActResult(assistantMessage, null, planSummary, null, "clarification_sent");
            completeStep(
                steps,
                progressListener,
                "确认缺失信息",
                "检查执行所需的对象、动作、内容和筛选条件是否完整。",
                thought.missingSlots().isEmpty()
                    ? "当前表达仍不足以确定唯一执行路径，已请求用户补充。"
                    : "还需要补充：" + String.join("、", thought.missingSlots()) + "。"
            );
        } else if (writeRisk) {
            Confirmation confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, toJson(Map.of("operation", intent)));
            assistantMessage = withContent(assistantMessage, "我已理解你的操作请求。这个请求可能会修改云枢数据，需要你确认后再继续执行。");
            actResult = new ActResult(assistantMessage, confirmation, planSummary, null, "pending_confirmation");
            completeStep(
                steps,
                progressListener,
                "检查操作风险",
                "判断本次动作是否会新增、修改或推进云枢业务数据。",
                "该动作会改变业务数据，已生成确认请求，确认前不会执行写入。"
            );
        } else if (isDataReadIntent(intent)) {
            if (canQueryRuntime(match)) {
                String actionStepId = UUID.randomUUID().toString();
                publishStep(
                    progressListener,
                    new ExecutionStepDto(
                        actionStepId,
                        "查询云枢数据",
                        "running",
                        "正在使用 schemaCode=`" + match.code() + "` 调用云枢运行态接口。",
                        "等待云枢返回真实数据。"
                    )
                );
                try {
                    CloudPivotQueryAnswer runtimeAnswer = cloudPivotRuntimeService.query(match, observation.effectiveUserGoal(), modelConfigId, thinkingEnabled);
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
                    assistantMessage = withContent(assistantMessage, runtimeAnswer.answer(), runtimeMetadataJson(runtimeAnswer));
                    planSummary = "analyze_data".equals(intent)
                        ? "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询数据，再生成业务分析。"
                        : "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询到真实数据。";
                    actResult = new ActResult(assistantMessage, null, planSummary, runtimeAnswer, "runtime_query_completed");
                    completeStep(
                        steps,
                        progressListener,
                        new ExecutionStepDto(
                            actionStepId,
                            "查询云枢数据",
                            "completed",
                            "使用 schemaCode=`" + runtimeAnswer.schemaCode() + "` 调用云枢运行态接口，来源=`" + runtimeAnswer.sourceEndpoint() + "`。",
                            "查询完成：总数 " + runtimeAnswer.total() + " 条，本次返回 " + runtimeAnswer.returnedRecords() + " 条。"
                        )
                    );
                } catch (RuntimeException exception) {
                    auditService.recordToolCall(
                        run.getId(),
                        "cloudpivot_runtime_query",
                        toJson(Map.of("schemaCode", match.code(), "action", "runtime_query_failed")),
                        toJson(Map.of("error", exception.getMessage() == null ? "云枢运行态查询失败" : exception.getMessage()))
                    );
                    assistantMessage = withContent(assistantMessage, runtimeFailureMessage(content, match, exception));
                    planSummary = "已理解为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类任务，并匹配到候选“" + match.name() + "”，但真实云枢运行态查询失败，未生成业务结论。";
                    actResult = new ActResult(assistantMessage, null, planSummary, null, "runtime_query_failed");
                    completeStep(
                        steps,
                        progressListener,
                        new ExecutionStepDto(
                            actionStepId,
                            "查询云枢数据",
                            "failed",
                            "已尝试使用 schemaCode=`" + match.code() + "` 调用云枢运行态接口。",
                            "查询失败，未使用不完整或演示数据生成业务结论。"
                        )
                    );
                }
            } else {
                assistantMessage = withContent(assistantMessage, unmatchedReadMessage(intent, content));
                planSummary = "已识别为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类请求，但本地元数据中没有匹配到可直接查询的云枢模型，未调用云枢运行态接口。";
                actResult = new ActResult(assistantMessage, null, planSummary, null, "no_runtime_candidate");
                completeStep(
                    steps,
                    progressListener,
                    "判断可执行性",
                    "检查候选对象是否为真实云枢业务模型，并且包含可调用的 schemaCode。",
                    "当前没有可直接查询的真实业务模型，已停止执行并提示重新同步或补充对象。"
                );
            }
        } else {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match));
            actResult = new ActResult(assistantMessage, null, planSummary, null, "fallback_clarification");
            completeStep(
                steps,
                progressListener,
                "确定下一步",
                "检查当前请求是否具备明确、可安全执行的路径。",
                "没有形成可靠执行路径，已转为澄清。"
            );
        }
        AgentReflection reflection = reflect(thought, plan, actResult);
        auditService.updateReflection(run.getId(), toJson(reflectionAuditMap(reflection)));
        if (isDataReadIntent(intent) && canQueryRuntime(match)) {
            completeStep(
                steps,
                progressListener,
                "校验结果",
                "检查返回来源、对象编码和执行状态，确认是否可以形成最终回答。",
                reflection.summary()
            );
        }
        MessageItem responseAssistantMessage = withAgentRunMetadata(actResult.assistantMessage(), run.getId(), defaultMetadataSource(actResult));

        return new AgentResponse(
            run.getId(),
            intent,
            riskLevel,
            writeRisk,
            actResult.planSummary(),
            match.reason(),
            List.of(new CandidateDto(match.name(), match.objectType(), match.reason())),
            List.copyOf(steps),
            actResult.confirmation() == null ? null : actResult.confirmation().getId(),
            responseAssistantMessage
        );
    }

    public Map<String, Object> previewPlaceholderPlan() {
        return Map.of(
            "planId", UUID.randomUUID().toString(),
            "intent", "query_data",
            "status", "agent-plan-placeholder",
            "requiresConfirmation", false
        );
    }

    private AgentObservation observe(String content, List<MessageItem> conversationContext) {
        List<MessageItem> safeContext = conversationContext == null ? List.of() : conversationContext;
        RuntimeContextObject runtimeContext = lastRuntimeContext(safeContext);
        String normalizedGoal = content == null ? "" : content.trim();
        boolean inheritedRuntimeObject = shouldInheritRuntimeObject(normalizedGoal, runtimeContext);
        String effectiveGoal = inheritedRuntimeObject ? inheritedEffectiveGoal(runtimeContext, normalizedGoal) : normalizedGoal;
        List<String> recentMessages = safeContext.stream()
            .filter(message -> message.content() != null && !message.content().isBlank())
            .skip(Math.max(0, safeContext.size() - 6))
            .map(message -> message.role() + ":" + shortText(message.content(), 80))
            .toList();
        boolean hasPreviousAssistantResult = safeContext.stream()
            .filter(message -> "assistant".equals(message.role()))
            .anyMatch(message -> message.content() != null && (message.content().contains("前 ") || message.content().contains("总计")));
        boolean referencesPreviousResult = containsAny(content, List.of("第一条", "第二条", "上一条", "刚才", "这些", "这个", "它", "它们", "他们", "上述"));
        return new AgentObservation(normalizedGoal, effectiveGoal, recentMessages, hasPreviousAssistantResult, referencesPreviousResult, runtimeContext, inheritedRuntimeObject);
    }

    private AgentThought think(String content, AgentObservation observation) {
        String query = observation.effectiveUserGoal();
        String detectedIntent = detectIntent(query);
        MetadataSearchResult match = observation.inheritedRuntimeObject()
            ? inheritedMetadataMatch(observation.runtimeContext())
            : metadataSearchService.bestMatch(query);
        IntentSlots slots = extractIntentSlots(query, detectedIntent);
        List<String> missingSlots = missingSlots(detectedIntent, match, observation);
        String intent = missingSlots.isEmpty() ? detectedIntent : "clarify_intent";
        if (needsClarification(intent, match)) {
            intent = "clarify_intent";
        }
        double confidence = confidence(intent, match, missingSlots);
        String reasoning = reasoningSummary(intent, detectedIntent, match, missingSlots, observation, slots);
        return new AgentThought(detectedIntent, intent, match, slots, missingSlots, confidence, reasoning);
    }

    private AgentPlan plan(AgentObservation observation, AgentThought thought, String riskLevel, String planSummary) {
        List<String> actions = new ArrayList<>();
        actions.add("metadata_search");
        if ("clarify_intent".equals(thought.intent())) {
            actions.add("ask_clarifying_question");
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
        return new AgentPlan(planSummary, riskLevel, isWriteIntent(thought.intent()), actions);
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
        if (isDataReadIntent(thought.intent())) {
            boolean hasAnswer = actResult.runtimeAnswer() != null && actResult.runtimeAnswer().total() >= 0;
            checks.add("runtime_query=" + hasAnswer);
            if (hasAnswer) {
                checks.add("real_cloudpivot_schema=" + !"local-fallback".equals(actResult.runtimeAnswer().sourceEndpoint()));
            }
            return new AgentReflection(hasAnswer ? "completed" : "needs_user_input", hasAnswer, !hasAnswer, checks, hasAnswer ? "读数据任务已完成，结果可返回用户。" : "没有拿到真实云枢运行态数据，已停止生成业务结论。");
        }
        return new AgentReflection("completed", true, false, checks, "已完成安全兜底处理。");
    }

    private String detectIntent(String content) {
        String value = compact(content);
        if (containsAny(value, "填写", "填报", "填一下", "补全", "根据附件", "从附件", "识别发票")) {
            return "fill_form_from_attachment";
        }
        if (containsAny(value, "删除", "删掉", "移除", "作废", "取消")) {
            return "delete_data";
        }
        if (isNewOldCustomerComparisonIntent(value)) {
            return "analyze_data";
        }
        if (containsAny(value, "新增", "新建", "创建", "录入", "登记", "写入", "修改", "更新", "调整", "变更", "编辑", "保存", "提交", "发起", "分配", "转移", "关闭", "推进")
            || value.contains("写一条跟进")
            || (value.contains("跟进记录") && containsAny(value, "新增", "新建", "创建", "写", "记录一条", "补一条", "提交"))) {
            return "update_data";
        }
        if (containsAny(value, "分析", "洞察", "诊断", "趋势", "建议", "怎么看", "情况怎么样", "怎么样", "按年", "每年", "年度", "阶段", "状态", "分布", "分别", "省份", "所属省", "哪些省", "哪个省", "地区", "区域", "城市", "地域", "归属地", "所在地", "新客户", "老客户", "新老", "存量客户", "哪个多", "谁多", "更多", "对比", "比较", "占比", "比例", "还是")) {
            return "analyze_data";
        }
        if (containsAny(value, "查询", "查", "查看", "找", "搜索", "检索", "看看", "看一下", "帮我看", "列出", "展示", "打开", "给我看", "有哪些", "有没有", "多少", "几条", "几个", "几项", "几笔", "几份", "几单", "一共", "总共", "共有", "总计", "数量", "统计", "汇总", "明细", "列表", "清单", "情况", "数据", "了解")) {
            return "query_data";
        }
        if (containsBusinessObject(value)) {
            return "query_data";
        }
        return "unknown";
    }
    private boolean containsBusinessObject(String value) {
        return containsAny(value, "crm", "客户关系", "销售管理", "客户", "联系人", "商机", "机会", "销售机会", "客户机会", "线索", "合同", "订单", "项目", "报价", "回款", "收款", "发票", "待办", "任务", "流程", "审批");
    }

    private boolean isNewOldCustomerComparisonIntent(String value) {
        boolean mentionsNewOldCustomer = containsAny(value, "新客户", "老客户", "新老客户", "新老", "新增客户", "存量客户")
            || (value.contains("客户") && value.contains("新") && value.contains("老"));
        boolean asksComparison = containsAny(value, "多", "哪个", "哪类", "谁", "更多", "占比", "比例", "对比", "比较", "分布", "统计", "数量", "情况", "还是");
        return mentionsNewOldCustomer && asksComparison;
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
        return !isDataReadIntent(intent) && !"unknown".equals(intent) && !"clarify_intent".equals(intent);
    }

    private boolean isDataReadIntent(String intent) {
        return "query_data".equals(intent) || "analyze_data".equals(intent);
    }

    private boolean canQueryRuntime(MetadataSearchResult match) {
        return match != null
            && "entity".equals(match.objectType())
            && match.code() != null
            && !match.code().isBlank()
            && !isKnownDemoBusinessCode(match.code());
    }

    private boolean isKnownDemoBusinessCode(String code) {
        return "system_opportunity".equals(code) || "system_customer".equals(code);
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
        if (isWriteIntent(detectedIntent) && observation.referencesPreviousResult() && !observation.hasPreviousAssistantResult()) {
            slots.add("上下文记录引用");
        }
        if (isWriteIntent(detectedIntent) && containsAny(observation.normalizedUserGoal(), List.of("跟进记录", "写一条跟进")) && !containsAny(observation.normalizedUserGoal(), List.of("跟进内容", "沟通", "回访", "电话", "邮件", "下周", "今天", "明天"))) {
            slots.add("跟进内容");
        }
        return slots.stream().distinct().toList();
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
            case "update_data" -> "修改/写入";
            case "delete_data" -> "删除/作废";
            case "fill_form_from_attachment" -> "附件填单";
            default -> "未明确";
        };
        return new IntentSlots(action, businessSubject(content), analysisDimension(content), filterSummary(content));
    }

    private String analysisDimension(String content) {
        if (containsAny(content, List.of("每年", "按年", "年度", "年份"))) {
            return "年份";
        }
        if (containsAny(content, List.of("阶段", "状态"))) {
            return "阶段/状态";
        }
        if (containsAny(content, List.of("省份", "所属省", "哪些省", "哪个省", "地区", "区域", "城市", "地域", "归属地", "所在地", "省市"))) {
            return "省份/区域";
        }
        if (containsAny(content, List.of("新客户", "老客户", "新老", "新增客户", "存量客户", "哪个多", "谁多", "更多", "对比", "比较", "占比", "比例", "还是"))) {
            return "新老客户";
        }
        if (containsAny(content, List.of("负责人", "销售", "人员"))) {
            return "负责人";
        }
        if (containsAny(content, List.of("金额", "收入", "合同额"))) {
            return "金额";
        }
        return "无明确维度";
    }
    private String filterSummary(String content) {
        if (containsAny(content, List.of("本月", "这个月"))) {
            return "本月";
        }
        if (containsAny(content, List.of("今年", "本年"))) {
            return "今年";
        }
        if (containsAny(content, List.of("去年"))) {
            return "去年";
        }
        return "无明确筛选条件";
    }

    private String buildPlanSummary(String intent, MetadataSearchResult match, boolean writeRisk) {
        if (writeRisk) {
            return "已识别为 " + intent + "，匹配本地元数据“" + match.name() + "”。修改类操作需要确认后再继续。";
        }
        if ("clarify_intent".equals(intent) || "unknown".equals(intent)) {
            return "当前请求信息不足，需要先向用户澄清业务对象、动作或筛选条件。";
        }
        if ("analyze_data".equals(intent)) {
            return "已识别为分析类请求，匹配本地元数据“" + match.name() + "”，将先查询云枢运行态数据，再生成业务分析。";
        }
        return "已识别为查询类请求，匹配本地元数据“" + match.name() + "”，将调用云枢运行态接口查询真实数据。";
    }

    private String unmatchedReadMessage(String intent, String content) {
        if ("analyze_data".equals(intent)) {
            return "我理解你想分析“" + businessSubject(content) + "”相关数据，但当前本地 Metadata Index 还没有匹配到真实可查询的云枢应用模型。请先在元数据页同步真实云枢元数据，或补充应用/表单名称；同步后我会按“真实 schemaCode -> 运行态查询 -> 大模型分析”的链路返回结论。";
        }
        return "我已识别到这是查询请求，目标对象是“" + businessSubject(content) + "”，但当前没有从真实云枢元数据中匹配到可查询的应用模型。请先同步真实云枢元数据，或补充更明确的应用名称、表单名称，例如“查询 CRM 商机”。我不会用本地演示数据返回业务结果。";
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
        message.append("1. 你想查询/分析哪个应用或表单？例如：CRM 商机、客户管理客户、销售订单。\n");
        message.append("2. 你想做什么动作？例如：查询、统计、分析、修改、新增。\n");
        message.append("3. 有没有筛选条件？例如：本月、负责人、阶段、客户名称。\n");
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
        return new MessageItem(message.id(), message.role(), content, message.createdAt(), metadataJson);
    }

    private MessageItem withAgentRunMetadata(MessageItem message, String agentRunId, String defaultSource) {
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
        return withContent(message, message.content(), toJson(metadata));
    }

    private String defaultMetadataSource(ActResult actResult) {
        return "runtime_query_completed".equals(actResult.status()) ? "runtime-query" : "runtime-agent";
    }

    private String metadataConclusion(MetadataSearchResult match) {
        if (match == null || "unknown".equals(match.objectType()) || match.code() == null || match.code().isBlank()) {
            return "没有匹配到可直接执行的真实云枢业务对象。";
        }
        return "命中“" + match.name() + "”，类型=" + match.objectType() + "，schemaCode=`" + match.code() + "`。";
    }

    private void completeStep(
        List<ExecutionStepDto> steps,
        Consumer<ExecutionStepDto> progressListener,
        String title,
        String process,
        String conclusion
    ) {
        completeStep(steps, progressListener, new ExecutionStepDto(UUID.randomUUID().toString(), title, "completed", process, conclusion));
    }

    private void completeStep(List<ExecutionStepDto> steps, Consumer<ExecutionStepDto> progressListener, ExecutionStepDto step) {
        steps.removeIf(existing -> existing.id().equals(step.id()));
        steps.add(step);
        publishStep(progressListener, step);
    }

    private void publishStep(Consumer<ExecutionStepDto> progressListener, ExecutionStepDto step) {
        if (progressListener != null) {
            progressListener.accept(step);
        }
    }

    private Map<String, Object> planAuditMap(AgentObservation observation, AgentThought thought, AgentPlan plan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mode", "react-reflection-mvp");
        value.put("observe", Map.of(
            "normalizedUserGoal", observation.normalizedUserGoal(),
            "effectiveUserGoal", observation.effectiveUserGoal(),
            "recentMessageCount", observation.recentMessages().size(),
            "referencesPreviousResult", observation.referencesPreviousResult(),
            "hasPreviousAssistantResult", observation.hasPreviousAssistantResult(),
            "inheritedRuntimeObject", observation.inheritedRuntimeObject(),
            "runtimeContextSchema", observation.runtimeContext().schemaCode()
        ));
        Map<String, Object> think = new LinkedHashMap<>();
        think.put("detectedIntent", thought.detectedIntent());
        think.put("intent", thought.intent());
        think.put("confidence", thought.confidence());
        think.put("action", thought.slots().actionLabel());
        think.put("businessObject", thought.slots().businessObject());
        think.put("dimension", thought.slots().dimension());
        think.put("filters", thought.slots().filters());
        think.put("metadataObject", thought.match().name());
        think.put("metadataCode", thought.match().code());
        think.put("missingSlots", thought.missingSlots());
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

    private String toJson(Map<String, Object> value) {
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
                if (!"runtime-query".equals(String.valueOf(metadata.getOrDefault("source", "")))) {
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
        boolean followUpDimensionQuestion = containsAny(value, "这些", "上述", "上面", "刚才", "上一轮", "它", "它们", "他们", "该", "都", "分别", "各", "每个", "阶段", "状态", "分布", "占比", "比例", "来源", "负责人", "金额", "按", "有哪些", "哪些", "多少", "属于", "省份", "所属省", "哪些省", "哪个省", "地区", "区域", "城市", "地域", "归属地", "所在地", "省市", "新客户", "老客户", "新老", "新增客户", "存量客户", "哪个多", "谁多", "更多", "对比", "比较", "多", "还是");
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

    private String runtimeMetadataJson(CloudPivotQueryAnswer answer) {
        return toJson(Map.of(
            "source", "runtime-query",
            "entityName", answer.entityName(),
            "schemaCode", answer.schemaCode(),
            "total", answer.total(),
            "returnedRecords", answer.returnedRecords(),
            "sourceEndpoint", answer.sourceEndpoint()
        ));
    }

    private record AgentObservation(String normalizedUserGoal, String effectiveUserGoal, List<String> recentMessages, boolean hasPreviousAssistantResult, boolean referencesPreviousResult, RuntimeContextObject runtimeContext, boolean inheritedRuntimeObject) {
    }

    private record RuntimeContextObject(String entityName, String schemaCode) {
    }

    private record AgentThought(String detectedIntent, String intent, MetadataSearchResult match, IntentSlots slots, List<String> missingSlots, double confidence, String reasoningSummary) {
    }

    private record IntentSlots(String actionLabel, String businessObject, String dimension, String filters) {
    }

    private record AgentPlan(String summary, String riskLevel, boolean requiresConfirmation, List<String> actions) {
    }

    private record ActResult(MessageItem assistantMessage, Confirmation confirmation, String planSummary, CloudPivotQueryAnswer runtimeAnswer, String status) {
    }

    private record AgentReflection(String status, boolean passed, boolean needsUserInput, List<String> checks, String summary) {
    }
}
