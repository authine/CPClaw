package com.cpclaw.agent.dto;

import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
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
    MessageItem assistantMessage,
    TaskExperienceEnvelope taskExperience
) {
    /** Backward-compatible constructor for existing Web/Agent callers. */
    public AgentResponse(
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
        this(agentRunId, intent, riskLevel, requiresConfirmation, planSummary, matchReason,
            candidates, steps, confirmationId, thinkingElapsedMs, answerElapsedMs,
            insightReport, assistantMessage, null);
    }
}
