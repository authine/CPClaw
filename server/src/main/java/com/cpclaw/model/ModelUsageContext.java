package com.cpclaw.model;

import org.springframework.stereotype.Component;

@Component
public class ModelUsageContext {

    private final ThreadLocal<TokenUsage> current = new ThreadLocal<>();

    public void beginCapture() {
        current.set(TokenUsage.empty());
    }

    public void record(TokenUsage usage) {
        TokenUsage captured = current.get();
        if (captured == null || usage == null || usage.isEmpty()) {
            return;
        }
        current.set(captured.plus(usage));
    }

    public TokenUsage finishCapture() {
        TokenUsage usage = current.get();
        current.remove();
        return usage == null ? TokenUsage.empty() : usage;
    }
}
