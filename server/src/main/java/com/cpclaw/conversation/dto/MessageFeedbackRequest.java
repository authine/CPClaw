package com.cpclaw.conversation.dto;

public record MessageFeedbackRequest(
    String feedbackType,
    String reason
) {
}
