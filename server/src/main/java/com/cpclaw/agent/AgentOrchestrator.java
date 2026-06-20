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

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, String modelConfigId, boolean thinkingEnabled, MessageItem assistantMessage) {
        String intent = detectIntent(content);
        MetadataSearchResult match = metadataSearchService.bestMatch(content);
        if (needsClarification(intent, match)) {
            intent = "clarify_intent";
        }
        boolean writeRisk = isWriteIntent(intent);
        String riskLevel = writeRisk ? "medium" : "low";
        String planSummary = buildPlanSummary(intent, match, writeRisk);
        String planJson = toJson(Map.of("intent", intent, "metadataObject", match.name(), "metadataCode", match.code(), "localMetadataOnly", false));
        AgentRun run = auditService.createAgentRun(conversationId, userMessageId, intent, riskLevel, planJson);
        auditService.recordToolCall(
            run.getId(),
            "metadata_search",
            toJson(Map.of("query", content == null ? "" : content)),
            toJson(Map.of("match", match.name(), "code", match.code(), "objectType", match.objectType()))
        );

        Confirmation confirmation = null;
        if ("clarify_intent".equals(intent)) {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match));
            planSummary = "当前信息不足以安全执行，已向用户发起澄清问题。";
        } else if (writeRisk) {
            confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, toJson(Map.of("operation", intent)));
            assistantMessage = withContent(assistantMessage, "我已理解你的操作请求。这个请求可能会修改云枢数据，需要你确认后再继续执行。");
        } else if (isDataReadIntent(intent)) {
            if (canQueryRuntime(match)) {
                CloudPivotQueryAnswer runtimeAnswer = cloudPivotRuntimeService.query(match, content, modelConfigId, thinkingEnabled);
                auditService.recordToolCall(
                    run.getId(),
                    "cloudpivot_runtime_query",
                    toJson(Map.of("schemaCode", runtimeAnswer.schemaCode())),
                    toJson(Map.of("total", runtimeAnswer.total(), "returnedRecords", runtimeAnswer.returnedRecords()))
                );
                assistantMessage = withContent(assistantMessage, runtimeAnswer.answer());
                planSummary = "analyze_data".equals(intent)
                    ? "已根据本地元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询数据，再生成业务分析。"
                    : "已根据本地元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询到真实数据。";
            } else {
                assistantMessage = withContent(assistantMessage, unmatchedReadMessage(intent, content));
                planSummary = "已识别为" + ("analyze_data".equals(intent) ? "分析" : "查询") + "类请求，但本地元数据中没有匹配到可直接查询的云枢模型，未调用云枢运行态接口。";
            }
        } else {
            assistantMessage = withContent(assistantMessage, clarificationMessage(content, match));
        }

        return new AgentResponse(
            run.getId(),
            intent,
            riskLevel,
            writeRisk,
            planSummary,
            match.reason(),
            List.of(new CandidateDto(match.name(), match.objectType(), match.reason())),
            List.of(
                new ExecutionStepDto("意图理解", intent),
                new ExecutionStepDto("本地元数据检索", match.name()),
                new ExecutionStepDto(writeRisk ? "生成风险确认" : ("clarify_intent".equals(intent) ? "向用户澄清意图" : ("analyze_data".equals(intent) ? "云枢运行态查询与分析" : "云枢运行态查询")), writeRisk ? "pending-confirmation" : "completed")
            ),
            confirmation == null ? null : confirmation.getId(),
            assistantMessage
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

    private String detectIntent(String content) {
        String value = content == null ? "" : content;
        if (value.contains("填写")) {
            return "fill_form_from_attachment";
        }
        if (value.contains("删除") || value.contains("作废")) {
            return "delete_data";
        }
        if (value.contains("新增") || value.contains("创建") || value.contains("写入") || value.contains("修改") || value.contains("提交")) {
            return "update_data";
        }
        if (value.contains("分析") || value.contains("洞察") || value.contains("诊断") || value.contains("趋势") || value.contains("建议") || value.contains("怎么看")) {
            return "analyze_data";
        }
        if (value.contains("查询") || value.contains("查") || value.contains("找") || value.contains("汇总") || value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少") || value.contains("了解") || value.contains("看一下")) {
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
        return match != null && "entity".equals(match.objectType()) && match.code() != null && !match.code().isBlank();
    }

    private boolean needsClarification(String intent, MetadataSearchResult match) {
        return "unknown".equals(intent) || (isDataReadIntent(intent) && !canQueryRuntime(match)) || (isWriteIntent(intent) && !canQueryRuntime(match));
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
            return "我理解你想分析“" + businessSubject(content) + "”相关数据，但当前本地 Metadata Index 还没有匹配到可查询的云枢表单/模型。请先在元数据页同步云枢元数据，或补充应用/表单名称；同步后我会先查询数据，再用模型生成分析结论。";
        }
        return "我已识别到这是查询请求，但还没有匹配到可查询的云枢表单/模型。请先初始化云枢元数据，或补充更明确的应用名称、表单名称，例如“查询系统商机”。";
    }

    private String businessSubject(String content) {
        String value = content == null ? "业务对象" : content;
        for (String noise : List.of("帮我", "请", "一下", "系统中的", "系统中", "系统", "信息", "数据", "情况", "进行", "做", "分析", "洞察", "诊断", "趋势", "建议", "的")) {
            value = value.replace(noise, " ");
        }
        String subject = value.trim().replaceAll("\\s+", " ");
        return subject.isBlank() ? "业务对象" : subject;
    }

    private String clarificationMessage(String content, MetadataSearchResult match) {
        StringBuilder message = new StringBuilder();
        message.append("我需要再确认一下你的意图，这样才能避免查错或误操作。\n\n");
        message.append("我目前理解到的内容：你可能想处理“").append(businessSubject(content)).append("”相关事项。\n");
        if (canQueryRuntime(match)) {
            message.append("我找到了一个可能相关的云枢对象：“").append(match.name()).append("”。\n");
        } else if (match != null && match.name() != null && !"未匹配到本地元数据".equals(match.name())) {
            message.append("我找到的候选还不能直接查询：“").append(match.name()).append("”。\n");
        } else {
            message.append("我暂时没有匹配到可直接查询的云枢对象。\n");
        }
        message.append("\n请你补充一个方向即可：\n");
        message.append("1. 你想查询/分析哪个应用或表单？例如：分析系统商机、查询销售订单。\n");
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

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize agent audit data", exception);
        }
    }
}
