package com.cpclaw.conversation;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.CandidateDto;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Presentation-only Web adapter for the shared task experience contract. */
@Service
public class WebTaskExperienceAdapter {
    private final ObjectMapper objectMapper;

    public WebTaskExperienceAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void forward(TaskProgressEvent event, AgentProgressListener progress) {
        progress.checkCancelled();
        progress.onExecution(event.title(), event.message(), Map.of("phase", event.phase()), event.state());
        progress.checkCancelled();
    }

    public AgentResponse adapt(TaskExperienceEnvelope envelope, MessageItem pendingMessage, AgentProgressListener progress) {
        String content = text(envelope.output().get("message"));
        Object resultValue = envelope.output().get("result");
        if (resultValue instanceof Map<?, ?> result && result.get("answer") != null) {
            content = text(result.get("answer"));
        }
        MessageItem message = withRuntimeMetadata(pendingMessage, envelope, content);
        List<ExecutionStepDto> steps = envelope.visibleTrace().stream()
            .map(event -> new ExecutionStepDto(event.title(), event.message(), event.phase(), event.state(), "execution", Map.of(), 0L))
            .toList();
        boolean confirmation = "confirmation_required".equals(envelope.task().status());
        String intent = text(envelope.output().get("intent"));
        if (intent.isBlank()) intent = text(envelope.summary().get("intent"));
        if ("workflow_query".equals(intent)) intent = "query_workflow";
        if ("conversation".equals(text(envelope.output().get("mode"))) || "conversation".equals(text(envelope.output().get("intent")))) intent = "conversation";
        if ("needs_input".equals(envelope.task().status())) intent = "clarify_intent";
        if (intent.isBlank()) intent = confirmation ? "confirmation_required" : "yunshu_task";
        String planSummary = "conversation".equals(intent)
            ? "已按通用对话模式直接回答，未调用云枢业务能力。"
            : content;
        if (!content.isBlank()) {
            progress.onAnswerStart("yunshu-runtime");
            AnswerStreamSupport.emitReadableChunks(content, progress::onAnswerChunk);
            progress.onAnswerComplete("yunshu-runtime");
        }
        String riskLevel = "workflow_action".equals(intent) ? "high" : (confirmation ? "medium" : "low");
        List<CandidateDto> candidates = candidate(envelope, intent);
        return new AgentResponse(
            envelope.task().id(), intent, riskLevel, confirmation,
            planSummary, "", candidates, steps, null, 0L, 0L, null, message, envelope
        );
    }

    private MessageItem withRuntimeMetadata(MessageItem pending, TaskExperienceEnvelope envelope, String content) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "yunshu-skill-runtime");
        metadata.put("taskId", envelope.task().id());
        metadata.put("agentRunId", envelope.task().id());
        metadata.put("taskStatus", envelope.task().status());
        metadata.put("taskExperienceVersion", envelope.experienceVersion());
        try {
            return new MessageItem(pending.id(), pending.role(), content, pending.createdAt(), objectMapper.writeValueAsString(metadata));
        } catch (Exception ignored) {
            return new MessageItem(pending.id(), pending.role(), content, pending.createdAt(), "{\"source\":\"yunshu-skill-runtime\"}");
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private List<CandidateDto> candidate(TaskExperienceEnvelope envelope, String intent) {
        if ("conversation".equals(intent) || "clarify_intent".equals(intent)) return List.of();
        String name = text(envelope.summary().get("matchedObject"));
        if (name.isBlank()) return List.of();
        return List.of(new CandidateDto(name, "entity", "由统一 Runtime 的已同步元数据定位。"));
    }
}
