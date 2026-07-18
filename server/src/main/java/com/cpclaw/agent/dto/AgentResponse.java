package com.cpclaw.agent.dto;

import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.insight.dto.InsightReportDto;
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
    long thinkingElapsedMs,
    long answerElapsedMs,
    InsightReportDto insightReport,
    MessageItem assistantMessage
) {
}
