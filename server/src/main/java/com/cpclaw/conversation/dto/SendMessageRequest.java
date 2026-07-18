package com.cpclaw.conversation.dto;

import java.util.List;

public record SendMessageRequest(
    String conversationId,
    String content,
    String modelConfigId,
    boolean thinkingEnabled,
    List<String> attachmentIds,
    String executionId
) {
}
