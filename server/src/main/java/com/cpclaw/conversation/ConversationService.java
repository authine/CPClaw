package com.cpclaw.conversation;

import com.cpclaw.agent.AgentOrchestrator;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.agent.dto.ExecutionStepDto;
import com.cpclaw.conversation.dto.ConversationDetail;
import com.cpclaw.conversation.dto.ConversationSummary;
import com.cpclaw.conversation.dto.CreateConversationRequest;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.dto.SendMessageRequest;
import com.cpclaw.conversation.entity.Conversation;
import com.cpclaw.conversation.entity.Message;
import com.cpclaw.conversation.repository.ConversationRepository;
import com.cpclaw.conversation.repository.MessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AgentOrchestrator agentOrchestrator;

    public ConversationService(
        ConversationRepository conversationRepository,
        MessageRepository messageRepository,
        @Lazy AgentOrchestrator agentOrchestrator
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.agentOrchestrator = agentOrchestrator;
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

    public List<MessageItem> listMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream().map(this::toMessageItem).toList();
    }

    @Transactional
    public AgentResponse sendMessage(SendMessageRequest request) {
        return sendMessage(request, step -> {
        });
    }

    @Transactional
    public AgentResponse sendMessage(SendMessageRequest request, Consumer<ExecutionStepDto> progressListener) {
        if (request == null || !hasText(request.content())) {
            throw new IllegalArgumentException("请输入要处理的内容");
        }
        Conversation conversation = resolveConversation(request);
        Instant now = Instant.now();
        String content = request.content().trim();
        Message userMessage = createMessage(conversation.getId(), "user", content, request.modelConfigId(), request.thinkingEnabled(), null, now);
        messageRepository.save(userMessage);

        Message assistantMessage = createMessage(conversation.getId(), "assistant", "", request.modelConfigId(), request.thinkingEnabled(), "{\"source\":\"runtime-agent\"}", now.plusMillis(1));
        List<MessageItem> conversationContext = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
            .map(this::toMessageItem)
            .toList();
        AgentResponse response = agentOrchestrator.handleMessage(
            conversation.getId(),
            userMessage.getId(),
            content,
            request.modelConfigId(),
            request.thinkingEnabled(),
            toMessageItem(assistantMessage),
            conversationContext,
            progressListener
        );
        assistantMessage.setContent(response.assistantMessage().content());
        assistantMessage.setMetadataJson(response.assistantMessage().metadataJson());
        messageRepository.save(assistantMessage);

        conversation.setTitle(buildConversationTitle(conversation.getTitle(), content));
        conversation.setUpdatedAt(Instant.now());
        conversationRepository.save(conversation);

        return response;
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
