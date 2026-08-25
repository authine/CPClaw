package com.cpclaw.conversation;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.conversation.dto.MessageFeedbackRequest;
import com.cpclaw.conversation.dto.MessageFeedbackResult;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.entity.Message;
import com.cpclaw.conversation.entity.MessageFeedbackEvent;
import com.cpclaw.conversation.repository.MessageFeedbackEventRepository;
import com.cpclaw.conversation.repository.MessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageFeedbackService {

    private static final String ACTOR_TYPE = "local-user";
    private static final String ACTOR_ID = "default";
    private static final int MAX_REASON_LENGTH = 500;

    private final MessageRepository messageRepository;
    private final MessageFeedbackEventRepository eventRepository;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ObjectMapper objectMapper;

    public MessageFeedbackService(
        MessageRepository messageRepository,
        MessageFeedbackEventRepository eventRepository,
        SensitiveDataMasker sensitiveDataMasker,
        ObjectMapper objectMapper
    ) {
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MessageFeedbackResult update(String messageId, MessageFeedbackRequest request) {
        Message message = messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("消息不存在，无法记录反馈"));
        if (!"assistant".equals(message.getRole())) {
            throw new IllegalArgumentException("只有助手回答可以提交反馈");
        }
        String requestedType = normalizeFeedbackType(request == null ? null : request.feedbackType());
        String currentType = currentFeedbackType(messageId);
        Instant now = Instant.now();
        if (java.util.Objects.equals(requestedType, currentType)) {
            return new MessageFeedbackResult(messageId, currentType, now);
        }

        MessageFeedbackEvent event = new MessageFeedbackEvent();
        event.setId(UUID.randomUUID().toString());
        event.setConversationId(message.getConversationId());
        event.setMessageId(messageId);
        event.setAgentRunId(readAgentRunId(message.getMetadataJson()));
        event.setActorType(ACTOR_TYPE);
        event.setActorId(ACTOR_ID);
        event.setActionType(requestedType == null ? "clear" : "set");
        event.setFeedbackType(requestedType);
        event.setReason(sanitizeReason(request == null ? null : request.reason()));
        event.setCreatedAt(now);
        eventRepository.save(event);
        return new MessageFeedbackResult(messageId, requestedType, now);
    }

    public List<MessageItem> enrich(String conversationId, List<MessageItem> messages) {
        Map<String, String> currentByMessage = currentFeedbackTypes(conversationId);
        return messages.stream()
            .map(message -> new MessageItem(
                message.id(), message.role(), message.content(), message.createdAt(), message.metadataJson(), currentByMessage.get(message.id())
            ))
            .toList();
    }

    public void deleteByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        eventRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).forEach(eventRepository::delete);
    }

    private Map<String, String> currentFeedbackTypes(String conversationId) {
        Map<String, String> result = new HashMap<>();
        eventRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId).forEach(event -> {
            if (event.getFeedbackType() == null || event.getFeedbackType().isBlank()) {
                result.remove(event.getMessageId());
            } else {
                result.put(event.getMessageId(), event.getFeedbackType());
            }
        });
        return result;
    }

    private String currentFeedbackType(String messageId) {
        return eventRepository.findByConversationIdOrderByCreatedAtAscIdAsc(messageRepository.findById(messageId).orElseThrow().getConversationId()).stream()
            .filter(event -> messageId.equals(event.getMessageId()))
            .reduce((first, second) -> second)
            .map(MessageFeedbackEvent::getFeedbackType)
            .orElse(null);
    }

    private String normalizeFeedbackType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"like".equals(normalized) && !"dislike".equals(normalized)) {
            throw new IllegalArgumentException("反馈类型仅支持 like 或 dislike");
        }
        return normalized;
    }

    private String sanitizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String masked = sensitiveDataMasker.mask(value.trim());
        return masked.length() > MAX_REASON_LENGTH ? masked.substring(0, MAX_REASON_LENGTH) : masked;
    }

    private String readAgentRunId(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            JsonNode value = node == null ? null : node.get("agentRunId");
            return value == null || value.isNull() ? null : value.asText();
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }
}
