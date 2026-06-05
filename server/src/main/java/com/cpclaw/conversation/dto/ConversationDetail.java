package com.cpclaw.conversation.dto;

import java.util.List;

public record ConversationDetail(
    ConversationSummary conversation,
    List<MessageItem> messages
) {
}
