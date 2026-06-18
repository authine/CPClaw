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

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, MessageItem assistantMessage) {
        String intent = detectIntent(content);
        boolean writeRisk = isWriteIntent(intent);
        String riskLevel = writeRisk ? "medium" : "low";
        MetadataSearchResult match = metadataSearchService.bestMatch(content);
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
        if (writeRisk) {
            confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, toJson(Map.of("operation", intent)));
            assistantMessage = withContent(assistantMessage, "我已理解你的操作请求。这个请求可能会修改云枢数据，需要你确认后再继续执行。");
        } else if ("query_data".equals(intent)) {
            if (canQueryRuntime(match)) {
                CloudPivotQueryAnswer runtimeAnswer = cloudPivotRuntimeService.query(match, content);
                auditService.recordToolCall(
                    run.getId(),
                    "cloudpivot_runtime_query",
                    toJson(Map.of("schemaCode", runtimeAnswer.schemaCode())),
                    toJson(Map.of("total", runtimeAnswer.total(), "returnedRecords", runtimeAnswer.returnedRecords()))
                );
                assistantMessage = withContent(assistantMessage, runtimeAnswer.answer());
                planSummary = "已根据本地元数据匹配到“" + runtimeAnswer.entityName() + "”，并调用云枢运行态接口查询到真实数据。";
            } else {
                assistantMessage = withContent(assistantMessage, "我已识别到这是查询请求，但还没有匹配到可查询的云枢表单/模型。请先初始化云枢元数据，或补充更明确的应用名称、表单名称，例如“查询系统商机”。");
                planSummary = "已识别为查询类请求，但本地元数据中没有匹配到可直接查询的云枢模型，未调用云枢运行态接口。";
            }
        } else {
            assistantMessage = withContent(assistantMessage, "我还没有理解你要操作的云枢对象。请补充应用名称、表单名称或更具体的查询条件。");
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
                new ExecutionStepDto(writeRisk ? "生成风险确认" : "云枢运行态查询", writeRisk ? "pending-confirmation" : "completed")
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
        if (value.contains("查询") || value.contains("查") || value.contains("找") || value.contains("汇总") || value.contains("统计") || value.contains("数量") || value.contains("总计") || value.contains("多少")) {
            return "query_data";
        }
        return "unknown";
    }

    private boolean isWriteIntent(String intent) {
        return !"query_data".equals(intent) && !"unknown".equals(intent);
    }

    private boolean canQueryRuntime(MetadataSearchResult match) {
        return match != null && "entity".equals(match.objectType()) && match.code() != null && !match.code().isBlank();
    }

    private String buildPlanSummary(String intent, MetadataSearchResult match, boolean writeRisk) {
        if (writeRisk) {
            return "已识别为 " + intent + "，匹配本地元数据“" + match.name() + "”。修改类操作需要确认后再继续。";
        }
        return "已识别为查询类请求，匹配本地元数据“" + match.name() + "”，将调用云枢运行态接口查询真实数据。";
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
