package com.cpclaw.model;

/** Result of the pre-agent conversation/task routing call. */
public record ConversationRouteResult(
    String mode,
    String skillId,
    String answer,
    String reasoning,
    double confidence
) {
    public static ConversationRouteResult unavailable() {
        return new ConversationRouteResult("", "", "", "", 0D);
    }

    public boolean isConversation() {
        return "conversation".equalsIgnoreCase(mode) && answer != null && !answer.isBlank();
    }

    public boolean isTask() {
        return "task".equalsIgnoreCase(mode);
    }
}
