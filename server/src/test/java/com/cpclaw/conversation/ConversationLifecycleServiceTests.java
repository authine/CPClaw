package com.cpclaw.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpclaw.conversation.entity.Conversation;
import com.cpclaw.conversation.repository.ConversationRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationLifecycleServiceTests {

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationLifecycleService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new ConversationLifecycleService(conversationRepository);
        conversation = new Conversation();
        conversation.setId("conversation-1");
        conversation.setTitle("新会话");
        conversation.setLifecycleStatus("DRAFT");
        conversation.setUnread(false);
        when(conversationRepository.findById("conversation-1")).thenReturn(Optional.of(conversation));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void outputLifecycleMovesDraftToRunningThenCompletedAndUnread() {
        service.markOutputStarted("conversation-1", "帮我分析整体情况");

        assertEquals("RUNNING", conversation.getLifecycleStatus());
        assertFalse(conversation.isUnread());
        assertEquals("帮我分析整体情况", conversation.getTitle());
        assertNotNull(conversation.getOutputStartedAt());

        service.markCompleted("conversation-1");

        assertEquals("COMPLETED", conversation.getLifecycleStatus());
        assertTrue(conversation.isUnread());
        verify(conversationRepository, atLeastOnce()).save(conversation);
    }

    @Test
    void failureAndCancellationDoNotExposeEmptyDraftButKeepCompletedOutputUnread() {
        service.markFailed("conversation-1", false);
        assertEquals("DRAFT", conversation.getLifecycleStatus());
        assertFalse(conversation.isUnread());

        service.markCancelled("conversation-1", true);
        assertEquals("CANCELLED", conversation.getLifecycleStatus());
        assertTrue(conversation.isUnread());
    }

    @Test
    void markReadIsIdempotentAndClearsUnread() {
        conversation.setLifecycleStatus("COMPLETED");
        conversation.setUnread(true);

        service.markRead("conversation-1");
        service.markRead("conversation-1");

        assertFalse(conversation.isUnread());
        assertNotNull(conversation.getLastReadAt());
        verify(conversationRepository, atLeastOnce()).save(conversation);
    }
}
