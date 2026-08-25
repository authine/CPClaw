package com.cpclaw.conversation.dto;

public record MessageItem(
    String id,
    String role,
    String content,
    String createdAt,
    String metadataJson,
    String feedbackType
) {

    public MessageItem(String id, String role, String content, String createdAt, String metadataJson) {
        this(id, role, content, createdAt, metadataJson, null);
    }
}
