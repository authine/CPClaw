package com.cpclaw.conversation;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AgentExecutionCancelledException;
import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.conversation.dto.ConversationDetail;
import com.cpclaw.conversation.dto.ConversationSummary;
import com.cpclaw.conversation.dto.CreateConversationRequest;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.dto.SendMessageRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationExecutionRegistry executionRegistry;
    private final ObjectMapper objectMapper;
    private final long streamTimeoutMs;

    public ConversationController(
        ConversationService conversationService,
        ConversationExecutionRegistry executionRegistry,
        ObjectMapper objectMapper,
        @Value("${cpclaw.conversation.stream-timeout-ms:600000}") long streamTimeoutMs
    ) {
        this.conversationService = conversationService;
        this.executionRegistry = executionRegistry;
        this.objectMapper = objectMapper;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    @GetMapping
    public ApiResponse<List<ConversationSummary>> list() {
        return ApiResponse.ok(conversationService.listConversations());
    }

    @PostMapping
    public ApiResponse<ConversationSummary> create(@RequestBody(required = false) CreateConversationRequest request) {
        CreateConversationRequest safeRequest = request == null ? new CreateConversationRequest("新会话", null, false) : request;
        return ApiResponse.ok(conversationService.createConversation(safeRequest));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationDetail> detail(@PathVariable String id) {
        return ApiResponse.ok(conversationService.getConversation(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        conversationService.deleteConversation(id);
        return ApiResponse.ok(null);
    }

    @DeleteMapping
    public ApiResponse<Void> deleteMissingId() {
        throw new IllegalArgumentException("会话ID缺失，无法删除");
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageItem>> messages(@PathVariable String id) {
        return ApiResponse.ok(conversationService.listMessages(id));
    }

    @PostMapping("/messages")
    public ApiResponse<AgentResponse> sendMessage(@RequestBody SendMessageRequest request) {
        return ApiResponse.ok(conversationService.sendMessage(request));
    }

    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AtomicBoolean streamOpen = new AtomicBoolean(true);
        emitter.onCompletion(() -> streamOpen.set(false));
        emitter.onTimeout(() -> streamOpen.set(false));
        emitter.onError(ignored -> streamOpen.set(false));
        ConversationExecutionRegistry.ExecutionHandle execution = executionRegistry.register(request == null ? null : request.executionId());
        executionRegistry.submit(execution, () -> {
            try {
                AgentResponse response = conversationService.sendMessage(request, streamListener(emitter, execution, streamOpen));
                if (execution.isCancelled()) {
                    throw new AgentExecutionCancelledException();
                }
                if (sendEvent(emitter, streamOpen, "final", response)) {
                    emitter.complete();
                }
            } catch (AgentExecutionCancelledException exception) {
                completeSafely(emitter, streamOpen);
            } catch (Exception exception) {
                if (execution.isCancelled()) {
                    completeSafely(emitter, streamOpen);
                    return;
                }
                if (sendEvent(
                    emitter,
                    streamOpen,
                    "error",
                    Map.of("message", exception.getMessage() == null ? "流式对话执行失败" : exception.getMessage())
                )) {
                    emitter.complete();
                }
            } finally {
                executionRegistry.complete(execution);
            }
        });
        return emitter;
    }

    @PostMapping("/executions/{executionId}/cancel")
    public ApiResponse<Map<String, Object>> cancelExecution(@PathVariable String executionId) {
        ConversationExecutionRegistry.CancellationResult result = executionRegistry.cancel(executionId);
        return ApiResponse.ok(Map.of("cancelled", result.cancelled(), "state", result.state()));
    }

    private AgentProgressListener streamListener(
        SseEmitter emitter,
        ConversationExecutionRegistry.ExecutionHandle execution,
        AtomicBoolean streamOpen
    ) {
        return new AgentProgressListener() {
            @Override
            public boolean isCancelled() {
                return execution.isCancelled() || Thread.currentThread().isInterrupted();
            }

            @Override
            public boolean tryBeginCommit() {
                return execution.tryBeginCommit();
            }

            @Override
            public void markCompleted() {
                execution.markCompleted();
            }

            @Override
            public void markCancelled() {
                execution.markCancelled();
            }

            @Override
            public void onProgress(String title, String status) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "progress", Map.of("title", title, "status", status));
            }

            @Override
            public void onThought(String phase, String title, String status, String state) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "thought", Map.of("phase", phase, "title", title, "status", status, "state", state));
            }

            @Override
            public void onExecution(String title, String status, Map<String, Object> data, String state) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "execution", Map.of(
                    "title", title,
                    "status", status,
                    "data", data == null ? Map.of() : data,
                    "state", state
                ));
            }

            @Override
            public void onAnswerStart(String mode) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "answer_start", Map.of("mode", mode));
            }

            @Override
            public void onAnswerChunk(String content) {
                checkCancelled();
                if (content != null && !content.isEmpty()) {
                    sendEvent(emitter, streamOpen, "answer_delta", Map.of("content", content));
                }
            }

            @Override
            public void onAnswerReset(String reason) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "answer_reset", Map.of("reason", reason == null ? "" : reason));
            }

            @Override
            public void onAnswerComplete(String mode) {
                checkCancelled();
                sendEvent(emitter, streamOpen, "answer_end", Map.of("mode", mode));
            }
        };
    }

    private boolean sendEvent(SseEmitter emitter, AtomicBoolean streamOpen, String eventName, Object payload) {
        if (!streamOpen.get()) {
            return false;
        }
        String json = toJson(payload);
        try {
            emitter.send(SseEmitter.event().name(eventName).data(json));
            return true;
        } catch (IOException | IllegalStateException exception) {
            streamOpen.set(false);
            return false;
        }
    }

    private void completeSafely(SseEmitter emitter, AtomicBoolean streamOpen) {
        if (streamOpen.getAndSet(false)) {
            emitter.complete();
        }
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize stream event", exception);
        }
    }
}
