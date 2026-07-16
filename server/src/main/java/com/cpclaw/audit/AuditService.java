package com.cpclaw.audit;

import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.audit.entity.ToolCall;
import com.cpclaw.audit.repository.AgentRunRepository;
import com.cpclaw.audit.repository.ConfirmationRepository;
import com.cpclaw.audit.repository.ToolCallRepository;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AgentRunRepository agentRunRepository;
    private final ToolCallRepository toolCallRepository;
    private final ConfirmationRepository confirmationRepository;
    private final ConfirmedOperationExecutor confirmedOperationExecutor;
    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;

    public AuditService(
        AgentRunRepository agentRunRepository,
        ToolCallRepository toolCallRepository,
        ConfirmationRepository confirmationRepository,
        ConfirmedOperationExecutor confirmedOperationExecutor,
        SensitiveDataMasker masker,
        ObjectMapper objectMapper
    ) {
        this.agentRunRepository = agentRunRepository;
        this.toolCallRepository = toolCallRepository;
        this.confirmationRepository = confirmationRepository;
        this.confirmedOperationExecutor = confirmedOperationExecutor;
        this.masker = masker;
        this.objectMapper = objectMapper;
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
        run.setPlanJson(masker.mask(planJson));
        run.setReflectionJson("{\"status\":\"mvp-reflection-placeholder\"}");
        run.setCreatedAt(now);
        run.setCompletedAt(now);
        return agentRunRepository.save(run);
    }

    public AgentRun updateReflection(String agentRunId, String reflectionJson) {
        AgentRun run = agentRunRepository.findById(agentRunId)
            .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        run.setReflectionJson(masker.mask(reflectionJson));
        run.setCompletedAt(Instant.now());
        return agentRunRepository.save(run);
    }

    public ToolCall recordToolCall(String agentRunId, String toolName, String inputJson, String outputJson) {
        Instant now = Instant.now();
        ToolCall toolCall = new ToolCall();
        toolCall.setId(UUID.randomUUID().toString());
        toolCall.setAgentRunId(agentRunId);
        toolCall.setToolName(toolName);
        toolCall.setInputJsonMasked(masker.mask(inputJson));
        toolCall.setOutputJsonMasked(masker.mask(outputJson));
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
        confirmation.setChangesJsonMasked(masker.mask(changesJson));
        confirmation.setStatus("pending");
        confirmation.setCreatedAt(now);
        confirmation.setExpiresAt(now.plusSeconds(1800));
        return confirmationRepository.save(confirmation);
    }

    @Transactional
    public Map<String, Object> confirm(String confirmationId) {
        Confirmation confirmation = confirmationRepository.findById(confirmationId)
            .orElseThrow(() -> new IllegalArgumentException("Confirmation not found"));
        if (!"pending".equals(confirmation.getStatus())) {
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", confirmationStatusMessage(confirmation.getStatus())
            );
        }
        Instant now = Instant.now();
        if (confirmation.getExpiresAt() != null && !confirmation.getExpiresAt().isAfter(now)) {
            confirmation.setStatus("expired");
            confirmation = confirmationRepository.save(confirmation);
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", "确认单已过期，未执行云枢操作。请重新发起操作。"
            );
        }
        confirmation.setStatus("confirmed");
        confirmation.setConfirmedAt(now);
        confirmation = confirmationRepository.save(confirmation);
        ConfirmedOperationExecutor.ConfirmedOperationResult result;
        try {
            result = confirmedOperationExecutor.execute(confirmation);
        } catch (RuntimeException exception) {
            confirmation.setStatus("failed");
            confirmation = confirmationRepository.save(confirmation);
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", exception.getMessage() == null ? "云枢操作执行失败" : exception.getMessage()
            );
        }
        if (result.executed()) {
            recordToolCall(
                confirmation.getAgentRunId(),
                "cloudpivot_runtime_delete",
                confirmation.getChangesJsonMasked(),
                toJson(result.output())
            );
            confirmation.setStatus("executed");
            confirmation = confirmationRepository.save(confirmation);
        } else if (!"unsupported".equals(result.operation())) {
            confirmation.setStatus("failed");
            confirmation = confirmationRepository.save(confirmation);
        }
        return Map.of(
            "id", confirmation.getId(),
            "status", confirmation.getStatus(),
            "agentRunId", confirmation.getAgentRunId(),
            "executed", result.executed(),
            "message", result.message()
        );
    }

    private String confirmationStatusMessage(String status) {
        return switch (status == null ? "" : status) {
            case "executed" -> "该确认单已经执行过，本次不会重复执行。";
            case "confirmed" -> "该确认单已经确认过，本次不会重复执行。";
            case "expired" -> "该确认单已过期，未执行云枢操作。";
            case "failed" -> "该确认单此前执行失败，请重新发起操作。";
            default -> "该确认单当前状态不允许再次确认。";
        };
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
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
}
