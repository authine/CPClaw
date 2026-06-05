package com.cpclaw.agent;

import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.CandidateDto;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.audit.AuditService;
import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.search.MetadataSearchService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private final MetadataSearchService metadataSearchService;
    private final AuditService auditService;

    public AgentOrchestrator(MetadataSearchService metadataSearchService, AuditService auditService) {
        this.metadataSearchService = metadataSearchService;
        this.auditService = auditService;
    }

    public AgentResponse handleMessage(String conversationId, String userMessageId, String content, MessageItem assistantMessage) {
        String intent = detectIntent(content);
        boolean writeRisk = isWriteIntent(intent);
        String riskLevel = writeRisk ? "medium" : "low";
        MetadataSearchResult match = metadataSearchService.bestMatch(content);
        String planSummary = buildPlanSummary(intent, match, writeRisk);
        String planJson = "{\"intent\":\"" + intent + "\",\"metadataObject\":\"" + match.name() + "\",\"localMetadataOnly\":true}";
        AgentRun run = auditService.createAgentRun(conversationId, userMessageId, intent, riskLevel, planJson);
        auditService.recordToolCall(run.getId(), "metadata_search", "{\"query\":\"" + content + "\"}", "{\"match\":\"" + match.name() + "\"}");

        Confirmation confirmation = null;
        if (writeRisk) {
            confirmation = auditService.createConfirmation(conversationId, run.getId(), riskLevel, planSummary, "{\"operation\":\"" + intent + "\"}");
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
                new ExecutionStepDto(writeRisk ? "生成风险确认" : "生成查询结果", writeRisk ? "pending-confirmation" : "completed")
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
        if (value.contains("查询") || value.contains("查") || value.contains("找") || value.contains("汇总")) {
            return "query_data";
        }
        return "unknown";
    }

    private boolean isWriteIntent(String intent) {
        return !"query_data".equals(intent) && !"unknown".equals(intent);
    }

    private String buildPlanSummary(String intent, MetadataSearchResult match, boolean writeRisk) {
        if (writeRisk) {
            return "已识别为 " + intent + "，匹配本地元数据“" + match.name() + "”。MVP 阶段仅生成确认卡片，不执行真实云枢写入。";
        }
        return "已识别为查询类请求，匹配本地元数据“" + match.name() + "”，将返回结果预览和匹配原因。";
    }
}
