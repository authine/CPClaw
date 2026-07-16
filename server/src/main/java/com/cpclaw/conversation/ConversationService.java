package com.cpclaw.conversation;

import com.cpclaw.agent.AgentOrchestrator;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AgentExecutionCancelledException;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentOrchestrator agentOrchestrator;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ModelUsageContext modelUsageContext;

    public ConversationService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        @Lazy AgentOrchestrator agentOrchestrator,
        ObjectMapper objectMapper,
        SensitiveDataMasker sensitiveDataMasker,
        ModelUsageContext modelUsageContext
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.agentOrchestrator = agentOrchestrator;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.modelUsageContext = modelUsageContext;
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
        conversationRepository.deleteById(id);
    }

    public List<MessageItem> listMessages(String conversationId) {
        return messageRepository.findByConversationIdInDisplayOrder(conversationId).stream().map(this::toMessageItem).toList();
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
        List<MessageItem> conversationContext = messageRepository.findByConversationIdInDisplayOrder(conversation.getId()).stream()
            .map(this::toMessageItem)
            .toList();
        modelUsageContext.beginCapture();
        AgentResponse response;
        TokenUsage tokenUsage;
        boolean cancelled = false;
        try {
            response = agentOrchestrator.handleMessage(
                conversation.getId(),
                userMessage.getId(),
                content,
                request.modelConfigId(),
                request.thinkingEnabled(),
                toMessageItem(assistantMessage),
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
        List<ExecutionStepDto> timelineSnapshot = maskTimeline(cancelled ? cancelledTimeline(rawTimelineSnapshot) : rawTimelineSnapshot);
        MessageItem responseMessage = withExecutionTimeline(withTokenUsage(response.assistantMessage(), tokenUsage), timelineSnapshot);
        AgentResponse responseWithTimeline = withExecutionTimeline(response, responseMessage, timelineSnapshot);
        assistantMessage.setContent(responseMessage.content());
        assistantMessage.setMetadataJson(responseMessage.metadataJson());
        messageRepository.save(assistantMessage);

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
