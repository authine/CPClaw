package com.cpclaw.audit;

import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.audit.entity.ToolCall;
import com.cpclaw.audit.repository.AgentRunRepository;
import com.cpclaw.audit.repository.ConfirmationRepository;
import com.cpclaw.audit.repository.ToolCallRepository;
import com.cpclaw.common.security.SensitiveDataMasker;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final int MAX_AUDIT_TEXT_LENGTH = 20000;

    private final AgentRunRepository agentRunRepository;
    private final ToolCallRepository toolCallRepository;
    private final ConfirmationRepository confirmationRepository;
    private final SensitiveDataMasker masker;

    public AuditService(
        AgentRunRepository agentRunRepository,
        ToolCallRepository toolCallRepository,
        ConfirmationRepository confirmationRepository,
        SensitiveDataMasker masker
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
        this.confirmationRepository = confirmationRepository;
        this.masker = masker;
    }

    public AgentRun createAgentRun(String conversationId, String userMessageId, String intent, String riskLevel, String planJson) {
        Instant now = Instant.now();
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID().toString());
        run.setConversationId(conversationId);
        run.setUserMessageId(userMessageId);
        run.setIntentSummary(intent);
        run.setRiskLevel(riskLevel);
        run.setStatus("completed");
        run.setPlanJson(maskAndTruncate(planJson));
        run.setReflectionJson("{\"status\":\"mvp-reflection-placeholder\"}");
        run.setCreatedAt(now);
        run.setCompletedAt(now);
        return agentRunRepository.save(run);
    }

    public AgentRun updateReflection(String agentRunId, String reflectionJson) {
        AgentRun run = agentRunRepository.findById(agentRunId)
            .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        run.setReflectionJson(maskAndTruncate(reflectionJson));
        run.setCompletedAt(Instant.now());
        return agentRunRepository.save(run);
    }

    public ToolCall recordToolCall(String agentRunId, String toolName, String inputJson, String outputJson) {
        Instant now = Instant.now();
        ToolCall toolCall = new ToolCall();
        toolCall.setId(UUID.randomUUID().toString());
        toolCall.setAgentRunId(agentRunId);
        toolCall.setToolName(toolName);
        toolCall.setInputJsonMasked(maskAndTruncate(inputJson));
        toolCall.setOutputJsonMasked(maskAndTruncate(outputJson));
        toolCall.setStatus("completed");
        toolCall.setCreatedAt(now);
        toolCall.setCompletedAt(now);
        return toolCallRepository.save(toolCall);
    }

    public Confirmation createConfirmation(String conversationId, String agentRunId, String riskLevel, String summary, String changesJson) {
        Instant now = Instant.now();
        Confirmation confirmation = new Confirmation();
        confirmation.setId(UUID.randomUUID().toString());
        confirmation.setConversationId(conversationId);
        confirmation.setAgentRunId(agentRunId);
        confirmation.setPlanId(UUID.randomUUID().toString());
        confirmation.setRiskLevel(riskLevel);
        confirmation.setSummary(summary);
        confirmation.setAffectedObjectsJson("[]");
        confirmation.setChangesJsonMasked(maskAndTruncate(changesJson));
        confirmation.setStatus("pending");
        confirmation.setCreatedAt(now);
        confirmation.setExpiresAt(now.plusSeconds(1800));
        return confirmationRepository.save(confirmation);
    }

    public Map<String, Object> confirm(String confirmationId) {
        Confirmation confirmation = confirmationRepository.findById(confirmationId)
            .orElseThrow(() -> new IllegalArgumentException("Confirmation not found"));
        confirmation.setStatus("confirmed");
        confirmation.setConfirmedAt(Instant.now());
        confirmationRepository.save(confirmation);
        return Map.of(
            "id", confirmation.getId(),
            "status", confirmation.getStatus(),
            "agentRunId", confirmation.getAgentRunId()
        );
    }

    public Map<String, Object> getAgentRunPlaceholder(String id) {
        AgentRun run = agentRunRepository.findById(id).orElse(null);
        if (run == null) {
            return Map.of("id", id, "status", "not-found");
        }
        List<Map<String, Object>> tools = toolCallRepository.findByAgentRunIdOrderByCreatedAtAsc(id).stream()
            .map(tool -> Map.<String, Object>of(
                "id", tool.getId(),
                "toolName", tool.getToolName(),
                "status", tool.getStatus(),
                "inputJsonMasked", tool.getInputJsonMasked(),
                "outputJsonMasked", tool.getOutputJsonMasked()
            ))
            .toList();
        return Map.of(
            "id", run.getId(),
            "conversationId", run.getConversationId() == null ? "" : run.getConversationId(),
            "intent", run.getIntentSummary() == null ? "" : run.getIntentSummary(),
            "riskLevel", run.getRiskLevel() == null ? "low" : run.getRiskLevel(),
            "status", run.getStatus(),
            "planJson", run.getPlanJson() == null ? "{}" : run.getPlanJson(),
            "reflectionJson", run.getReflectionJson() == null ? "{}" : run.getReflectionJson(),
            "tools", tools
        );
    }

    private String maskAndTruncate(String value) {
        String masked = masker.mask(value);
        if (masked == null || masked.length() <= MAX_AUDIT_TEXT_LENGTH) {
            return masked;
        }
        return masked.substring(0, MAX_AUDIT_TEXT_LENGTH) + "...[truncated]";
    }
}
