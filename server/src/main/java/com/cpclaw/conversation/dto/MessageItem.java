package com.cpclaw.conversation.dto;

public record MessageItem(
    String id,
    String role,
    String content,
    String createdAt,
    String metadataJson
) {
}
