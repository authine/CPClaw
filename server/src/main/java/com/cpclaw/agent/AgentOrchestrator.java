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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        AgentObservation observation = observe(content, conversationContext);
        AgentThought thought = think(content, observation);
        String intent = thought.intent();
        MetadataSearchResult match = thought.match();
        boolean writeRisk = isWriteIntent(intent);
        String riskLevel = writeRisk ? "medium" : "low";
        String planSummary = buildPlanSummary(intent, match, writeRisk);
        AgentPlan plan = plan(observation, thought, riskLevel, planSummary);
        String planJson = toJson(planAuditMap(observation, thought, plan));
        AgentRun run = auditService.createAgentRun(conversationId, userMessageId, intent, riskLevel, planJson);
        auditService.recordToolCall(
            run.getId(),
            "metadata_search",
            toJson(Map.of("query", content == null ? "" : content)),
            toJson(Map.of("match", match.name(), "code", match.code(), "objectType", match.objectType()))
        );

        ActResult actResult;
        if ("clarify_intent".equals(intent)) {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match, thought.missingSlots()));
            planSummary = "当前信息不足以安全执行，已向用户发起澄清问题。";
            actResult = new ActResult(assistantMessage, null, planSummary, null, "clarification_sent");
        } else if (writeRisk) {
            Confirmation confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, toJson(Map.of("operation", intent)));
            assistantMessage = withContent(assistantMessage, "我已理解你的操作请求。这个请求可能会修改云枢数据，需要你确认后再继续执行。");
            actResult = new ActResult(assistantMessage, confirmation, planSummary, null, "pending_confirmation");
        } else if (isDataReadIntent(intent)) {
            if (canQueryRuntime(match)) {
                try {
                    CloudPivotQueryAnswer runtimeAnswer = cloudPivotRuntimeService.query(match, content, modelConfigId, thinkingEnabled);
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
                    assistantMessage = withContent(assistantMessage, runtimeAnswer.answer());
                    planSummary = "analyze_data".equals(intent)
                        ? "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询数据，再生成业务分析。"
                        : "已根据真实云枢元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询到真实数据。";
                    actResult = new ActResult(assistantMessage, null, planSummary, runtimeAnswer, "runtime_query_completed");
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
                }
            } else {
                assistantMessage = withContent(assistantMessage, unmatchedReadMessage(intent, content));
                planSummary = "已识别为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类请求，但本地元数据中没有匹配到可直接查询的云枢模型，未调用云枢运行态接口。";
                actResult = new ActResult(assistantMessage, null, planSummary, null, "no_runtime_candidate");
            }
        } else {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match));
            actResult = new ActResult(assistantMessage, null, planSummary, null, "fallback_clarification");
        }
        AgentReflection reflection = reflect(thought, plan, actResult);
        auditService.updateReflection(run.getId(), toJson(reflectionAuditMap(reflection)));

        return new AgentResponse(
            run.getId(),
            intent,
            riskLevel,
            writeRisk,
            actResult.planSummary(),
            match.reason(),
            List.of(new CandidateDto(match.name(), match.objectType(), match.reason())),
            reactSteps(observation, thought, actResult, reflection),
            actResult.confirmation() == null ? null : actResult.confirmation().getId(),
            actResult.assistantMessage()
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
        List<String> recentMessages = safeContext.stream()
            .filter(message -> message.content() != null && !message.content().isBlank())
            .skip(Math.max(0, safeContext.size() - 6))
            .map(message -> message.role() + ":" + shortText(message.content(), 80))
            .toList();
        boolean hasPreviousAssistantResult = safeContext.stream()
            .filter(message -> "assistant".equals(message.role()))
            .anyMatch(message -> message.content() != null && (message.content().contains("前 ") || message.content().contains("总计")));
        boolean referencesPreviousResult = containsAny(content, List.of("第一条", "第二条", "上一条", "刚才", "这个", "它"));
        return new AgentObservation(content == null ? "" : content.trim(), recentMessages, hasPreviousAssistantResult, referencesPreviousResult);
    }

    private AgentThought think(String content, AgentObservation observation) {
        String detectedIntent = detectIntent(content);
        MetadataSearchResult match = metadataSearchService.bestMatch(content);
        IntentSlots slots = extractIntentSlots(content, detectedIntent);
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
        String value = content == null ? "" : content;
        if (value.contains("填写")) {
            return "fill_form_from_attachment";
        }
        if (value.contains("删除") || value.contains("作废")) {
            return "delete_data";
        }
        if (value.contains("新增") || value.contains("创建") || value.contains("写入") || value.contains("修改") || value.contains("提交") || value.contains("跟进记录") || value.contains("写一条跟进")) {
            return "update_data";
        }
        if (value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看") || value.contains("情况怎么样") || value.contains("怎么样") || value.contains("按年") || value.contains("每年") || value.contains("年度")) {
            return "analyze_data";
        }
        if (value.contains("查询") || value.contains("查") || value.contains("找") || value.contains("汇总") || value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("几条") || value.contains("几个") || value.contains("几项") || value.contains("几笔") || value.contains("几份") || value.contains("几单") || value.contains("一共") || value.contains("总共") || value.contains("共有") || value.contains("了解") || value.contains("看一下")) {
            return "query_data";
        }
        return "unknown";
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
        return "### 执行过程\n"
            + "- 意图理解：识别为数据读取任务，动作/对象为“" + businessSubject(content) + "”。\n"
            + "- 对象匹配：从真实云枢元数据候选中命中“" + match.name() + "”，schemaCode=`" + match.code() + "`。\n"
            + "- 数据动作：尝试调用云枢运行态查询，但接口返回失败。\n"
            + "- 反思检查：未拿到真实运行态数据，因此不生成数量、分析结论或业务建议，避免返回错误结果。\n\n"
            + "我已经理解你的问题，但这次没有成功查到真实云枢数据，所以不能直接回答结果。请检查普通用户云枢连接、账号权限、运行态接口是否可访问，或重新同步元数据后再试。\n\n"
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
        return new MessageItem(message.id(), message.role(), content, message.createdAt(), message.metadataJson());
    }

    private List<ExecutionStepDto> reactSteps(AgentObservation observation, AgentThought thought, ActResult actResult, AgentReflection reflection) {
        return List.of(
            new ExecutionStepDto("Observe 观察上下文", observeStepSummary(observation)),
            new ExecutionStepDto("Think 理解意图", thinkStepSummary(thought)),
            new ExecutionStepDto("Act 执行动作", actStepSummary(actResult)),
            new ExecutionStepDto("Reflect 反思检查", reflectStepSummary(reflection))
        );
    }

    private String observeStepSummary(AgentObservation observation) {
        return "用户目标=“" + shortText(observation.normalizedUserGoal(), 40) + "”；最近上下文=" + observation.recentMessages().size() + " 条；引用上一轮=" + yesNo(observation.referencesPreviousResult());
    }

    private String thinkStepSummary(AgentThought thought) {
        String slots = thought.missingSlots().isEmpty() ? "无" : String.join("、", thought.missingSlots());
        return "原始意图=" + thought.detectedIntent()
            + "；最终意图=" + thought.intent()
            + "；动作=" + thought.slots().actionLabel()
            + "；业务对象=" + thought.slots().businessObject()
            + "；分析维度=" + thought.slots().dimension()
            + "；筛选=" + thought.slots().filters()
            + "；候选对象=" + thought.match().name()
            + "；候选编码=" + thought.match().code()
            + "；缺失槽位=" + slots
            + "；置信度=" + thought.confidence();
    }

    private String actStepSummary(ActResult actResult) {
        if (actResult.runtimeAnswer() != null) {
            CloudPivotQueryAnswer answer = actResult.runtimeAnswer();
            return "工具=cloudpivot_runtime_query；" + schemaCodeLabel(answer) + "=" + answer.schemaCode() + "；来源=" + answer.sourceEndpoint() + "；total=" + answer.total() + "；returned=" + answer.returnedRecords() + "；原始摘要=" + shortText(answer.rawDataSummary(), 120);
        }
        return "状态=" + actResult.status();
    }

    private String schemaCodeLabel(CloudPivotQueryAnswer answer) {
        return answer != null && "local-fallback".equals(answer.sourceEndpoint()) ? "演示编码" : "schemaCode";
    }

    private String reflectStepSummary(AgentReflection reflection) {
        return "状态=" + reflection.status() + "；通过=" + yesNo(reflection.passed()) + "；需要用户补充=" + yesNo(reflection.needsUserInput()) + "；" + reflection.summary();
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private Map<String, Object> planAuditMap(AgentObservation observation, AgentThought thought, AgentPlan plan) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("mode", "react-reflection-mvp");
        value.put("observe", Map.of(
            "normalizedUserGoal", observation.normalizedUserGoal(),
            "recentMessageCount", observation.recentMessages().size(),
            "referencesPreviousResult", observation.referencesPreviousResult(),
            "hasPreviousAssistantResult", observation.hasPreviousAssistantResult()
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

    private record AgentObservation(String normalizedUserGoal, List<String> recentMessages, boolean hasPreviousAssistantResult, boolean referencesPreviousResult) {
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
