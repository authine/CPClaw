package com.cpclaw.model;

import java.util.Map;

public record TokenUsage(long promptTokens, long completionTokens, long totalTokens) {

    public TokenUsage {
        promptTokens = Math.max(0, promptTokens);
        completionTokens = Math.max(0, completionTokens);
        totalTokens = Math.max(totalTokens, promptTokens + completionTokens);
    }

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens,
            totalTokens + other.totalTokens
        );
    }

    public boolean isEmpty() {
        return totalTokens <= 0;
    }

    public Map<String, Object> toMetadata() {
        return Map.of(
            "prompt_tokens", promptTokens,
            "completion_tokens", completionTokens,
            "total_tokens", totalTokens
        );
    }
}
