package com.cpclaw.model;

import java.util.Map;

public record TokenUsage(long promptTokens, long completionTokens, long cachedTokens, long totalTokens) {

    public TokenUsage(long promptTokens, long completionTokens, long totalTokens) {
        this(promptTokens, completionTokens, 0, totalTokens);
    }

    public TokenUsage {
        promptTokens = Math.max(0, promptTokens);
        completionTokens = Math.max(0, completionTokens);
        cachedTokens = Math.min(promptTokens, Math.max(0, cachedTokens));
        totalTokens = Math.max(totalTokens, promptTokens + completionTokens);
    }

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0, 0);
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
            promptTokens + other.promptTokens,
            completionTokens + other.completionTokens,
            cachedTokens + other.cachedTokens,
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
            "cached_tokens", cachedTokens,
            "total_tokens", totalTokens
        );
    }
}
