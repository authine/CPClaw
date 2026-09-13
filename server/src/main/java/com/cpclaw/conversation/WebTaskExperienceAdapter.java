package com.cpclaw.conversation;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.CandidateDto;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.yunshu.protocol.YunshuTaskEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
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
        InsightReportDto insightReport = insightReport(envelope.output().get("artifact"));
        MessageItem message = withRuntimeMetadata(pendingMessage, envelope, content, insightReport);
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
            planSummary, "", candidates, steps, null, 0L, 0L, insightReport, message, envelope
        );
    }

    /** Adapts the canonical form-task envelope without leaking transport details to the Web host. */
    public AgentResponse adaptFormTask(YunshuTaskEnvelope form, MessageItem pendingMessage, AgentProgressListener progress) {
        String status = form.task().status().name();
        String intent = status.equals("needs_input") || status.equals("validation_error") ? "create_data" : "confirmation_required";
        String message = status.equals("needs_input")
                ? "已识别需要填写的业务信息，请补充标记为必填的内容。"
                : form.error() == null ? "已生成业务信息，请核对后继续。" : form.error().message();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("message", message);
        output.put("intent", intent);
        output.put("draft", form.draft());
        output.put("interaction", form.interaction());
        output.put("validation", form.validation());
        output.put("next", form.next());
        output.put("completion", form.completion());
        output.put("hostAction", form.hostAction());
        if (form.error() != null) output.put("error", form.error().message());
        TaskExperienceEnvelope envelope = new TaskExperienceEnvelope(
                "2.0",
                new TaskExperienceEnvelope.Task(form.task().id(), status, form.task().updatedAt(), form.task().retryable()),
                Map.of("intent", intent, "matchedObject", form.draft().formRef().getOrDefault("formCode", "业务表单")),
                List.of(), output,
                Map.of("type", form.interaction().type().name(), "questions", form.interaction().questions()),
                Map.of("type", form.hostAction().type().name(), "payload", form.hostAction().payload()),
                Map.of("state", form.completion().state(), "missing", form.completion().missing()),
                form.evidence(),
                Map.of("taskId", form.task().id(), "continuationToken", form.next().continuationToken() == null ? "" : form.next().continuationToken())
        );
        return adapt(envelope, pendingMessage, progress);
    }

    private MessageItem withRuntimeMetadata(MessageItem pending, TaskExperienceEnvelope envelope, String content, InsightReportDto insightReport) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "yunshu-skill-runtime");
        metadata.put("taskId", envelope.task().id());
        metadata.put("agentRunId", envelope.task().id());
        metadata.put("taskStatus", envelope.task().status());
        metadata.put("taskExperienceVersion", envelope.experienceVersion());
        if (insightReport != null) metadata.put("insightReport", insightReport);
        try {
            return new MessageItem(pending.id(), pending.role(), content, pending.createdAt(), objectMapper.writeValueAsString(metadata));
        } catch (Exception ignored) {
            return new MessageItem(pending.id(), pending.role(), content, pending.createdAt(), "{\"source\":\"yunshu-skill-runtime\"}");
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private InsightReportDto insightReport(Object artifact) {
        if (!(artifact instanceof Map<?, ?> source) || !"data_insight".equals(text(source.get("type")))) return null;
        try {
            return new InsightReportDto(
                "", "", 0,
                text(source.get("title")),
                text(source.get("subject")),
                text(source.get("period")),
                text(source.get("scope")),
                text(source.get("confidence")),
                convert(source.get("kpis"), new TypeReference<List<InsightReportDto.Kpi>>() { }),
                convert(source.get("charts"), new TypeReference<List<InsightReportDto.Chart>>() { }),
                convert(source.get("sections"), new TypeReference<List<InsightReportDto.Section>>() { }),
                convert(source.get("followUps"), new TypeReference<List<String>>() { }),
                convert(source.get("sources"), new TypeReference<List<String>>() { }),
                convert(source.get("warnings"), new TypeReference<List<String>>() { })
            );
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private <T> List<T> convert(Object value, TypeReference<List<T>> targetType) {
        if (value == null) return List.of();
        return objectMapper.convertValue(value, targetType);
    }

    private List<CandidateDto> candidate(TaskExperienceEnvelope envelope, String intent) {
        if ("conversation".equals(intent) || "clarify_intent".equals(intent)) return List.of();
        String name = text(envelope.summary().get("matchedObject"));
        if (name.isBlank()) return List.of();
        return List.of(new CandidateDto(name, "entity", "由统一 Runtime 的已同步元数据定位。"));
    }
}
