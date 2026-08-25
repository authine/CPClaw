package com.cpclaw.conversation.repository;

import com.cpclaw.conversation.entity.MessageFeedbackEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageFeedbackEventRepository extends JpaRepository<MessageFeedbackEvent, String> {

    List<MessageFeedbackEvent> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<MessageFeedbackEvent> findByConversationIdOrderByCreatedAtAscIdAsc(String conversationId);
}
