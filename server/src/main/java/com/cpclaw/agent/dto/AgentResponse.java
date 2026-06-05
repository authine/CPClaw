package com.cpclaw.agent.dto;

import com.cpclaw.conversation.dto.MessageItem;
import java.util.List;

public record AgentResponse(
    String agentRunId,
    String intent,
    String riskLevel,
    boolean requiresConfirmation,
    String planSummary,
    String matchReason,
    List<CandidateDto> candidates,
    List<ExecutionStepDto> steps,
    String confirmationId,
    MessageItem assistantMessage
) {
}
