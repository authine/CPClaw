package com.cpclaw.agent;

import com.cpclaw.agent.dto.ExecutionStepDto;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@FunctionalInterface
public interface AgentProgressListener {

    AgentProgressListener NOOP = (title, status) -> { };

    void onProgress(String title, String status);

    default boolean isCancelled() {
        return Thread.currentThread().isInterrupted();
    }

    default void checkCancelled() {
        if (isCancelled()) {
            throw new AgentExecutionCancelledException();
        }
    }

    default boolean tryBeginCommit() {
        checkCancelled();
        return true;
    }

    default void markCompleted() {
    }

    default void markCancelled() {
    }

    default void onThought(String phase, String title, String status, String state) {
        onProgress(title, status);
    }

    default void onExecution(String title, String status, Map<String, Object> data, String state) {
        onProgress(title, status);
    }

    default void onAnswerStart(String mode) {
    }

    default void onAnswerChunk(String content) {
    }

    default void onAnswerReset(String reason) {
    }

    default void onAnswerComplete(String mode) {
    }

    static AgentProgressListener recording(AgentProgressListener delegate, Consumer<ExecutionStepDto> eventConsumer) {
        return recording(delegate, eventConsumer, ignored -> { });
    }

    static AgentProgressListener recording(
        AgentProgressListener delegate,
        Consumer<ExecutionStepDto> eventConsumer,
        Consumer<String> answerChunkConsumer
    ) {
        AgentProgressListener downstream = delegate == null ? NOOP : delegate;
        Consumer<ExecutionStepDto> recorder = eventConsumer == null ? ignored -> { } : eventConsumer;
        Consumer<String> answerRecorder = answerChunkConsumer == null ? ignored -> { } : answerChunkConsumer;
        long startedAtNanos = System.nanoTime();
        return new AgentProgressListener() {
            @Override
            public boolean isCancelled() {
                return downstream.isCancelled() || Thread.currentThread().isInterrupted();
            }

            @Override
            public boolean tryBeginCommit() {
                return downstream.tryBeginCommit();
            }

            @Override
            public void markCompleted() {
                downstream.markCompleted();
            }

            @Override
            public void markCancelled() {
                downstream.markCancelled();
            }

            @Override
            public void onProgress(String title, String status) {
                recorder.accept(event("progress", null, title, status, null, Map.of(), startedAtNanos));
                downstream.onProgress(title, status);
            }

            @Override
            public void onThought(String phase, String title, String status, String state) {
                recorder.accept(event("thought", phase, title, status, state, Map.of(), startedAtNanos));
                downstream.onThought(phase, title, status, state);
            }

            @Override
            public void onExecution(String title, String status, Map<String, Object> data, String state) {
                recorder.accept(event("execution", null, title, status, state, data, startedAtNanos));
                downstream.onExecution(title, status, data, state);
            }

            @Override
            public void onAnswerStart(String mode) {
                recorder.accept(event("answer_start", "answer", "正式回答", mode, "running", Map.of("mode", safe(mode)), startedAtNanos));
                downstream.onAnswerStart(mode);
            }

            @Override
            public void onAnswerChunk(String content) {
                answerRecorder.accept(content == null ? "" : content);
                downstream.onAnswerChunk(content);
            }

            @Override
            public void onAnswerReset(String reason) {
                recorder.accept(event("answer_reset", "answer", "重置回答", reason, "fallback", Map.of("reason", safe(reason)), startedAtNanos));
                downstream.onAnswerReset(reason);
            }

            @Override
            public void onAnswerComplete(String mode) {
                recorder.accept(event("answer_end", "answer", "正式回答", mode, "completed", Map.of("mode", safe(mode)), startedAtNanos));
                downstream.onAnswerComplete(mode);
            }
        };
    }

    private static ExecutionStepDto event(
        String kind,
        String phase,
        String title,
        String status,
        String state,
        Map<String, Object> data,
        long startedAtNanos
    ) {
        Map<String, Object> eventData = data == null || data.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(Math.max(0L, System.nanoTime() - startedAtNanos));
        return new ExecutionStepDto(title, status, phase, state, kind, eventData, elapsedMs);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
