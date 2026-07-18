package com.cpclaw.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelUsageContextTests {

    @Test
    void accumulatesUsageWithinOneCaptureAndClearsAfterFinish() {
        ModelUsageContext context = new ModelUsageContext();

        context.beginCapture();
        context.record(new TokenUsage(120, 30, 150));
        context.record(new TokenUsage(80, 20, 100));

        TokenUsage usage = context.finishCapture();

        assertThat(usage.promptTokens()).isEqualTo(200);
        assertThat(usage.completionTokens()).isEqualTo(50);
        assertThat(usage.totalTokens()).isEqualTo(250);
        assertThat(context.finishCapture()).isEqualTo(TokenUsage.empty());
    }

    @Test
    void ignoresUsageWhenNoConversationCaptureIsActive() {
        ModelUsageContext context = new ModelUsageContext();

        context.record(new TokenUsage(100, 20, 120));

        assertThat(context.finishCapture()).isEqualTo(TokenUsage.empty());
    }
}
