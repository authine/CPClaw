package com.cpclaw.conversation;

import com.cpclaw.conversation.entity.Conversation;
import com.cpclaw.conversation.repository.ConversationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists conversation lifecycle/read state independently of the long model transaction. */
@Service
public class ConversationLifecycleService {
    private final ConversationRepository conversationRepository;

    public ConversationLifecycleService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOutputStarted(String conversationId, String title) {
        update(conversationId, conversation -> {
            conversation.setLifecycleStatus("RUNNING");
            conversation.setUnread(false);
            if ((conversation.getTitle() == null || conversation.getTitle().isBlank() || "新会话".equals(conversation.getTitle())) && title != null && !title.isBlank()) {
                conversation.setTitle(title.length() > 20 ? title.substring(0, 20) : title);
            }
            if (conversation.getOutputStartedAt() == null) {
                conversation.setOutputStartedAt(Instant.now());
            }
            conversation.setUpdatedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String conversationId) {
        update(conversationId, conversation -> {
            conversation.setLifecycleStatus("COMPLETED");
            conversation.setUnread(true);
            conversation.setUpdatedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String conversationId, boolean outputStarted) {
        update(conversationId, conversation -> {
            conversation.setLifecycleStatus(outputStarted ? "FAILED" : "DRAFT");
            conversation.setUnread(outputStarted);
            conversation.setUpdatedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCancelled(String conversationId, boolean outputStarted) {
        update(conversationId, conversation -> {
            conversation.setLifecycleStatus(outputStarted ? "CANCELLED" : "DRAFT");
            conversation.setUnread(outputStarted);
            conversation.setUpdatedAt(Instant.now());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRead(String conversationId) {
        update(conversationId, conversation -> {
            conversation.setUnread(false);
            conversation.setLastReadAt(Instant.now());
        });
    }

    private void update(String conversationId, java.util.function.Consumer<Conversation> updater) {
        if (conversationId == null || conversationId.isBlank()) return;
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            updater.accept(conversation);
            conversationRepository.save(conversation);
        });
    }
}
