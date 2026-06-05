package com.cpclaw.conversation.dto;

public record CreateConversationRequest(
    String title,
    String modelConfigId,
    boolean thinkingEnabled
) {
}
