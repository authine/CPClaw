package com.cpclaw.conversation.repository;

import com.cpclaw.conversation.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, String> {

    long deleteByConversationId(String conversationId);

    @Query("""
        select m from Message m
        where m.conversationId = :conversationId
        order by m.createdAt asc,
            case m.role when 'user' then 0 when 'assistant' then 1 else 2 end asc,
            m.id asc
        """)
    List<Message> findByConversationIdInDisplayOrder(@Param("conversationId") String conversationId);
}
