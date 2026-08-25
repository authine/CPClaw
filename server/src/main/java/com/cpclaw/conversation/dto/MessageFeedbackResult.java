package com.cpclaw.conversation.dto;

import java.time.Instant;

public record MessageFeedbackResult(
    String messageId,
    String feedbackType,
    Instant updatedAt
) {
}
