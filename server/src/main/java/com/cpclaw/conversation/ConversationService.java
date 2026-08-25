package com.cpclaw.conversation;

import com.cpclaw.agent.AgentOrchestrator;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AgentExecutionCancelledException;
import com.cpclaw.audit.AuditService;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.conversation.dto.ConversationDetail;
import com.cpclaw.conversation.dto.ConversationSummary;
import com.cpclaw.conversation.dto.CreateConversationRequest;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.dto.SendMessageRequest;
import com.cpclaw.conversation.entity.Conversation;
import com.cpclaw.conversation.entity.Message;
import com.cpclaw.conversation.repository.ConversationRepository;
import com.cpclaw.conversation.repository.MessageRepository;
import com.cpclaw.model.ModelUsageContext;
import com.cpclaw.model.TokenUsage;
import com.cpclaw.memory.MemoryService;
import com.cpclaw.identity.PrincipalContextService;
import com.cpclaw.task.TaskGateway;
import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.task.dto.TaskSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final QueryResultReferenceService queryResultReferenceService;
    private final MessageFeedbackService messageFeedbackService;
    private final MemoryService memoryService;
    private final AgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ModelUsageContext modelUsageContext;
    private final AuditService auditService;
    private final TaskGateway taskGateway;
    private final PrincipalContextService principalContextService;

    public ConversationService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        QueryResultReferenceService queryResultReferenceService,
        MessageFeedbackService messageFeedbackService,
        MemoryService memoryService,
        @Lazy AgentOrchestrator agentOrchestrator,
        ObjectMapper objectMapper,
        SensitiveDataMasker sensitiveDataMasker,
        ModelUsageContext modelUsageContext,
        AuditService auditService,
        TaskGateway taskGateway,
        PrincipalContextService principalContextService
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.queryResultReferenceService = queryResultReferenceService;
        this.messageFeedbackService = messageFeedbackService;
        this.memoryService = memoryService;
        this.agentOrchestrator = agentOrchestrator;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.modelUsageContext = modelUsageContext;
        this.auditService = auditService;
        this.taskGateway = taskGateway;
        this.principalContextService = principalContextService;
    }

    public List<ConversationSummary> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toSummary).toList();
    }

    @Transactional
    public ConversationSummary createConversation(CreateConversationRequest request) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID().toString());
        conversation.setTitle(hasText(request.title()) ? request.title() : "新会话");
        conversation.setDefaultModelConfigId(request.modelConfigId());
        conversation.setDefaultThinkingEnabled(request.thinkingEnabled());
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        return toSummary(conversationRepository.save(conversation));
    }

    public ConversationDetail getConversation(String id) {
        Conversation conversation = conversationRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        return new ConversationDetail(toSummary(conversation), listMessages(id));
    }

    @Transactional
    public void deleteConversation(String id) {
        if (!conversationRepository.existsById(id)) {
            throw new IllegalArgumentException("Conversation not found");
        }
        messageRepository.deleteByConversationId(id);
        messageFeedbackService.deleteByConversationId(id);
        queryResultReferenceService.deleteByConversationId(id);
        memoryService.deleteByConversationId(id);
        conversationRepository.deleteById(id);
    }

    public List<MessageItem> listMessages(String conversationId) {
        List<MessageItem> items = messageRepository.findByConversationIdInDisplayOrder(conversationId).stream().map(this::toMessageItem).toList();
        return messageFeedbackService.enrich(conversationId, items);
    }

    @Transactional
    public AgentResponse sendMessage(SendMessageRequest request) {
        return sendMessage(request, AgentProgressListener.NOOP);
    }

    @Transactional
    public AgentResponse sendMessage(SendMessageRequest request, AgentProgressListener progressListener) {
        if (request == null || !hasText(request.content())) {
            throw new IllegalArgumentException("请输入要处理的内容");
        }
        long executionStartedAtNanos = System.nanoTime();
        List<ExecutionStepDto> executionTimeline = Collections.synchronizedList(new ArrayList<>());
        StringBuilder streamedAnswer = new StringBuilder();
        AgentProgressListener progress = AgentProgressListener.recording(progressListener, executionTimeline::add, streamedAnswer::append);
        progress.checkCancelled();
        Conversation conversation = resolveConversation(request);
        Instant now = Instant.now();
        String content = request.content().trim();
        Message userMessage = createMessage(conversation.getId(), "user", content, request.modelConfigId(), request.thinkingEnabled(), null, now);
        messageRepository.save(userMessage);

        Message assistantMessage = createMessage(conversation.getId(), "assistant", "", request.modelConfigId(), request.thinkingEnabled(), "{\"source\":\"runtime-agent\"}", now.plusMillis(1));
        // The Agent needs the same enriched history that the user sees. In particular,
        // the current feedback state on the previous assistant answer is a context signal
        // for follow-up requests such as “再详细设计一下”.
        List<MessageItem> conversationContext = listMessages(conversation.getId());
        modelUsageContext.beginCapture();
        AgentResponse response;
        TokenUsage tokenUsage;
        boolean cancelled = false;
        try {
            response = executeWebTurnThroughTaskGateway(
                conversation,
                userMessage,
                content,
                request,
                assistantMessage,
                conversationContext,
                progress
            );
            progress.checkCancelled();
            if (!progress.tryBeginCommit()) {
                throw new AgentExecutionCancelledException();
            }
            tokenUsage = modelUsageContext.finishCapture();
        } catch (RuntimeException exception) {
            tokenUsage = modelUsageContext.finishCapture();
            if (!(exception instanceof AgentExecutionCancelledException) && !progress.isCancelled()) {
                throw exception;
            }
            Thread.interrupted();
            cancelled = true;
            executionTimeline.add(cancelledStep(executionStartedAtNanos));
            response = cancelledResponse(toMessageItem(assistantMessage), !streamedAnswer.isEmpty(), executionStartedAtNanos);
        }
        List<ExecutionStepDto> rawTimelineSnapshot = timelineSnapshot(executionTimeline);
        List<ExecutionStepDto> timelineSnapshot = "conversation".equalsIgnoreCase(response.intent())
            ? List.of()
            : maskTimeline(cancelled ? cancelledTimeline(rawTimelineSnapshot) : rawTimelineSnapshot);
        MessageItem responseMessage = withExecutionTimeline(withTokenUsage(response.assistantMessage(), tokenUsage), timelineSnapshot);
        AgentResponse responseWithTimeline = withExecutionTimeline(response, responseMessage, timelineSnapshot);
        assistantMessage.setContent(responseMessage.content());
        assistantMessage.setMetadataJson(responseMessage.metadataJson());
        messageRepository.save(assistantMessage);
        auditService.finalizeAgentRun(
            response.agentRunId(),
            assistantMessage.getId(),
            request.modelConfigId(),
            content,
            responseMessage.content(),
            tokenUsage,
            response.thinkingElapsedMs() + response.answerElapsedMs(),
            cancelled ? "cancelled" : null
        );

        conversation.setTitle(buildConversationTitle(conversation.getTitle(), content));
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        if (cancelled) {
            progress.markCancelled();
        } else {
            progress.markCompleted();
        }

        return responseWithTimeline;
    }

    /**
     * Web is a transport adapter too.  Keep the existing AgentResponse contract
     * for the UI while routing lifecycle, persistence and future idempotency
     * through the channel-neutral TaskGateway.
     *
     * The web adapter intentionally does not expose the TaskExperienceEnvelope
     * to the UI yet; that is a compatibility boundary, not a second execution
     * path.  A later UI migration can consume the envelope directly without
     * moving the domain orchestrator again.
     */
    private AgentResponse executeWebTurnThroughTaskGateway(
        Conversation conversation,
        Message userMessage,
        String content,
        SendMessageRequest request,
        Message assistantMessage,
        List<MessageItem> conversationContext,
        AgentProgressListener progress
    ) {
        AtomicReference<AgentResponse> responseRef = new AtomicReference<>();
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
        var principal = principalContextService.current();
        SemanticTaskRequest taskRequest = new SemanticTaskRequest(
            "web",
            "cpclaw-web",
            principal.principalId(),
            "",
            "",
            content,
            List.of(),
            TaskSpec.empty(content, conversation.getId(), "", "")
        );
        TaskExperienceEnvelope envelope = taskGateway.execute(
            taskRequest,
            (taskId, taskProgress) -> {
                taskProgress.accept(new TaskProgressEvent(5, "web", "开始执行会话任务", "正在进入统一任务运行时。", "running"));
                try {
                    AgentResponse response = agentOrchestrator.handleMessage(
                        conversation.getId(),
                        userMessage.getId(),
                        content,
                        request.modelConfigId(),
                        request.thinkingEnabled(),
                        toMessageItem(assistantMessage),
                        conversationContext,
                        progress
                    );
                    responseRef.set(response);
                    taskProgress.accept(new TaskProgressEvent(100, "web", "完成会话任务", "Web Agent 已生成兼容 UI 的最终回答。", "completed"));
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", response == null || "cancelled".equalsIgnoreCase(response.intent()) ? "cancelled" : "completed");
                    result.put("understandingSummary", response == null || response.planSummary() == null ? "" : response.planSummary());
                    return result;
                } catch (RuntimeException exception) {
                    failureRef.set(exception);
                    if (exception instanceof AgentExecutionCancelledException) {
                        taskProgress.accept(new TaskProgressEvent(100, "web", "中止会话任务", "任务已由用户中止。", "cancelled"));
                        return Map.of("status", "cancelled", "understandingSummary", "用户已中止本次执行。", "retryable", false);
                    }
                    throw exception;
                }
            },
            ignored -> { }
        );
        RuntimeException failure = failureRef.get();
        if (failure != null) throw failure;
        AgentResponse response = responseRef.get();
        if (response == null) {
            throw new IllegalStateException("统一任务运行时未返回 Web AgentResponse（状态: " + (envelope == null ? "unknown" : envelope.task().status()) + "）");
        }
        return response;
    }

    private AgentResponse cancelledResponse(MessageItem assistantMessage, boolean discardedPartialAnswer, long startedAtNanos) {
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "runtime-agent");
        metadata.put("status", "cancelled");
        metadata.put("partialAnswerDiscarded", discardedPartialAnswer);
        metadata.put("cancelledAt", Instant.now().toString());
        MessageItem cancelledMessage;
        try {
            cancelledMessage = new MessageItem(
                assistantMessage.id(),
                assistantMessage.role(),
                "本次执行已由用户中止，未生成最终结论。",
                assistantMessage.createdAt(),
                objectMapper.writeValueAsString(metadata)
            );
        } catch (JsonProcessingException exception) {
            cancelledMessage = new MessageItem(
                assistantMessage.id(),
                assistantMessage.role(),
                "本次执行已由用户中止，未生成最终结论。",
                assistantMessage.createdAt(),
                "{\"source\":\"runtime-agent\",\"status\":\"cancelled\"}"
            );
        }
        return new AgentResponse(
            null,
            "cancelled",
            "low",
            false,
            "用户已中止本次执行。",
            "",
            List.of(),
            List.of(),
            null,
            elapsedMs,
            0L,
            null,
            cancelledMessage
        );
    }

    private ExecutionStepDto cancelledStep(long startedAtNanos) {
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
        return new ExecutionStepDto(
            "执行已中止",
            "用户已中止本次执行，后续步骤不再继续。",
            "end",
            "cancelled",
            "progress",
            Map.of(),
            elapsedMs
        );
    }

    private List<ExecutionStepDto> cancelledTimeline(List<ExecutionStepDto> executionTimeline) {
        List<ExecutionStepDto> result = new ArrayList<>(executionTimeline.size());
        for (int index = 0; index < executionTimeline.size(); index++) {
            ExecutionStepDto step = executionTimeline.get(index);
            if (!"running".equals(step.state()) || hasLaterTerminalStep(executionTimeline, index, step)) {
                result.add(step);
                continue;
            }
            result.add(new ExecutionStepDto(
                    step.title(),
                    step.status() + "（已中止）",
                    step.phase(),
                    "cancelled",
                    step.kind(),
                    step.data(),
                    step.elapsedMs()
            ));
        }
        return List.copyOf(result);
    }

    private boolean hasLaterTerminalStep(List<ExecutionStepDto> timeline, int currentIndex, ExecutionStepDto runningStep) {
        for (int index = currentIndex + 1; index < timeline.size(); index++) {
            ExecutionStepDto candidate = timeline.get(index);
            if (!java.util.Objects.equals(runningStep.title(), candidate.title())
                || !java.util.Objects.equals(runningStep.kind(), candidate.kind())
                || !java.util.Objects.equals(runningStep.phase(), candidate.phase())) {
                continue;
            }
            return !"running".equals(candidate.state());
        }
        return false;
    }

    private AgentResponse withExecutionTimeline(AgentResponse response, MessageItem assistantMessage, List<ExecutionStepDto> executionTimeline) {
        return new AgentResponse(
            response.agentRunId(),
            response.intent(),
            response.riskLevel(),
            response.requiresConfirmation(),
            response.planSummary(),
            response.matchReason(),
            response.candidates(),
            executionTimeline,
            response.confirmationId(),
            response.thinkingElapsedMs(),
            response.answerElapsedMs(),
            response.insightReport(),
            assistantMessage
        );
    }

    private MessageItem withExecutionTimeline(MessageItem message, List<ExecutionStepDto> executionTimeline) {
        String metadataJson = mergeExecutionTimeline(message.metadataJson(), executionTimeline);
        return new MessageItem(message.id(), message.role(), message.content(), message.createdAt(), metadataJson);
    }

    private MessageItem withTokenUsage(MessageItem message, TokenUsage tokenUsage) {
        TokenUsage safeUsage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (hasText(message.metadataJson())) {
            try {
                metadata.putAll(objectMapper.readValue(message.metadataJson(), new TypeReference<Map<String, Object>>() { }));
            } catch (JsonProcessingException exception) {
                metadata.put("legacyMetadataJson", message.metadataJson());
            }
        }
        metadata.put("usage", safeUsage.toMetadata());
        try {
            return new MessageItem(message.id(), message.role(), message.content(), message.createdAt(), objectMapper.writeValueAsString(metadata));
        } catch (JsonProcessingException exception) {
            return message;
        }
    }

    private String mergeExecutionTimeline(String metadataJson, List<ExecutionStepDto> executionTimeline) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (hasText(metadataJson)) {
            try {
                metadata.putAll(objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() { }));
            } catch (JsonProcessingException exception) {
                metadata.put("legacyMetadataJson", metadataJson);
            }
        }
        metadata.put("executionTimeline", executionTimeline);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize execution timeline", exception);
        }
    }

    private List<ExecutionStepDto> timelineSnapshot(List<ExecutionStepDto> executionTimeline) {
        synchronized (executionTimeline) {
            return List.copyOf(executionTimeline);
        }
    }

    private List<ExecutionStepDto> maskTimeline(List<ExecutionStepDto> timeline) {
        return timeline.stream()
            .map(step -> new ExecutionStepDto(
                sensitiveDataMasker.mask(step.title()),
                sensitiveDataMasker.mask(step.status()),
                step.phase(),
                step.state(),
                step.kind(),
                maskTimelineData(step.data()),
                step.elapsedMs()
            ))
            .toList();
    }

    private Map<String, Object> maskTimelineData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        try {
            String maskedJson = sensitiveDataMasker.mask(objectMapper.writeValueAsString(data));
            return objectMapper.readValue(maskedJson, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            return Map.of("summary", "执行数据已记录，但无法安全展示。");
        }
    }

    private Conversation resolveConversation(SendMessageRequest request) {
        if (!hasText(request.conversationId())) {
            return fromSummary(createConversation(new CreateConversationRequest("新会话", request.modelConfigId(), request.thinkingEnabled())));
        }
        return conversationRepository.findById(request.conversationId())
            .orElseGet(() -> fromSummary(createConversation(new CreateConversationRequest("新会话", request.modelConfigId(), request.thinkingEnabled()))));
    }

    private Message createMessage(String conversationId, String role, String content, String modelConfigId, boolean thinkingEnabled, String metadataJson, Instant createdAt) {
        Message message = new Message();
        message.setId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setModelConfigId(modelConfigId);
        message.setThinkingEnabled(thinkingEnabled);
        message.setMetadataJson(metadataJson);
        message.setCreatedAt(createdAt);
        return message;
    }


    private String buildConversationTitle(String currentTitle, String content) {
        if (currentTitle != null && !currentTitle.equals("新会话")) {
            return currentTitle;
        }
        String value = content == null || content.isBlank() ? "新会话" : content.trim();
        return value.length() > 20 ? value.substring(0, 20) : value;
    }

    private ConversationSummary toSummary(Conversation conversation) {
        return new ConversationSummary(conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt() == null ? null : conversation.getUpdatedAt().toString());
    }

    private MessageItem toMessageItem(Message message) {
        return new MessageItem(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt() == null ? null : message.getCreatedAt().toString(), message.getMetadataJson());
    }

    private Conversation fromSummary(ConversationSummary summary) {
        return conversationRepository.findById(summary.id()).orElseThrow();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
