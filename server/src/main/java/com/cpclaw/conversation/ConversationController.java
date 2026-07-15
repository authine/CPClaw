package com.cpclaw.conversation;

import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.conversation.dto.ConversationDetail;
import com.cpclaw.conversation.dto.ConversationSummary;
import com.cpclaw.conversation.dto.CreateConversationRequest;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.dto.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final SensitiveDataMasker masker;

    public ConversationController(ConversationService conversationService, ObjectMapper objectMapper, SensitiveDataMasker masker) {
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.masker = masker;
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

    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageItem>> messages(@PathVariable String id) {
        return ApiResponse.ok(conversationService.listMessages(id));
    }

    @PostMapping("/messages")
    public ApiResponse<AgentResponse> sendMessage(@RequestBody SendMessageRequest request) {
        return ApiResponse.ok(conversationService.sendMessage(request));
    }

    @PostMapping(value = "/messages/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public StreamingResponseBody streamMessage(@RequestBody SendMessageRequest request) {
        return outputStream -> {
            try {
                AgentResponse response = conversationService.sendMessage(
                    request,
                    step -> writeEvent(outputStream, "step", step)
                );
                writeEvent(outputStream, "metadata", metadataOnly(response));
                streamAnswer(outputStream, response.assistantMessage().content());
                writeEvent(outputStream, "done", Map.of("messageId", response.assistantMessage().id()));
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (RuntimeException exception) {
                writeEvent(outputStream, "error", Map.of("message", safeErrorMessage(exception)));
            }
        };
    }

    private AgentResponse metadataOnly(AgentResponse response) {
        MessageItem message = response.assistantMessage();
        return new AgentResponse(
            response.agentRunId(),
            response.intent(),
            response.riskLevel(),
            response.requiresConfirmation(),
            response.planSummary(),
            response.matchReason(),
            response.candidates(),
            response.steps(),
            response.confirmationId(),
            new MessageItem(message.id(), message.role(), "", message.createdAt(), message.metadataJson())
        );
    }

    private void streamAnswer(OutputStream outputStream, String content) throws IOException {
        String value = content == null ? "" : content;
        int offset = 0;
        while (offset < value.length()) {
            int end = offset;
            for (int count = 0; count < 8 && end < value.length(); count++) {
                end += Character.charCount(value.codePointAt(end));
            }
            writeEvent(outputStream, "content", Map.of("delta", value.substring(offset, end)));
            offset = end;
            try {
                Thread.sleep(16);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("流式输出被中断", exception);
            }
        }
    }

    private void writeEvent(OutputStream outputStream, String type, Object data) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Map.of("type", type, "data", data));
            outputStream.write(payload);
            outputStream.write('\n');
            outputStream.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String safeErrorMessage(RuntimeException exception) {
        String message = exception.getMessage() == null ? "请求处理失败" : masker.mask(exception.getMessage());
        return message != null && message.length() > 300 ? message.substring(0, 300) + "...[truncated]" : message;
    }
}
