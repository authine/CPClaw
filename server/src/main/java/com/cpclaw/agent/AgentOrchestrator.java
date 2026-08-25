package com.cpclaw.agent;

import com.cpclaw.agent.dto.AgentResponse;
import com.cpclaw.conversation.dto.MessageItem;
import com.cpclaw.skill.yunshu.YunshuAgentOrchestrator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Channel-neutral framework facade. Domain interpretation and execution live
 * in the resolved Skill implementation, never in this framework class.
 */
@Service
public class AgentOrchestrator {
    private final YunshuAgentOrchestrator yunshu;

    public AgentOrchestrator(@Lazy YunshuAgentOrchestrator yunshu) {
        this.yunshu = yunshu;
    }

    public AgentResponse handleMessage(
        String conversationId,
        String userMessageId,
        String content,
        String modelConfigId,
        boolean thinkingEnabled,
        MessageItem assistantMessage,
        List<MessageItem> conversationContext
    ) {
        return yunshu.handleMessage(conversationId, userMessageId, content, modelConfigId, thinkingEnabled, assistantMessage, conversationContext);
    }

    public AgentResponse handleMessage(
        String conversationId,
        String userMessageId,
        String content,
        String modelConfigId,
        boolean thinkingEnabled,
        MessageItem assistantMessage,
        List<MessageItem> conversationContext,
        AgentProgressListener progressListener
    ) {
        return yunshu.handleMessage(conversationId, userMessageId, content, modelConfigId, thinkingEnabled, assistantMessage, conversationContext, progressListener);
    }

    public Map<String, Object> previewPlaceholderPlan() {
        return yunshu.previewPlaceholderPlan();
    }
}
