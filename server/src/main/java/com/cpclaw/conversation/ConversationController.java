package com.cpclaw.conversation;

import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.conversation.dto.ConversationDetail;
import com.cpclaw.conversation.dto.ConversationSummary;
import com.cpclaw.conversation.dto.CreateConversationRequest;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.conversation.dto.SendMessageRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
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
}
