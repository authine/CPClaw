package com.cpclaw.conversation.repository;

import com.cpclaw.conversation.entity.QueryResultReference;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QueryResultReferenceRepository extends JpaRepository<QueryResultReference, String> {

    long deleteByConversationId(String conversationId);

    List<QueryResultReference> findByConversationIdAndMessageIdOrderByRowIndexAsc(String conversationId, String messageId);

    List<QueryResultReference> findByConversationIdAndExpiresAtAfterOrderByCreatedAtDesc(String conversationId, Instant now);

    QueryResultReference findFirstByConversationIdAndSchemaCodeAndExpiresAtAfterOrderByCreatedAtDesc(
        String conversationId,
        String schemaCode,
        Instant now
    );
}
