package com.cpclaw.memory.repository;

import com.cpclaw.memory.entity.AgentMemory;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, String> {

    @Query("select m from AgentMemory m where m.conversationId = :conversationId and m.memoryScope = 'SESSION' and m.ownerPrincipal = :owner and m.tenantId = :tenant and (m.expiresAt is null or m.expiresAt > :now) order by m.updatedAt desc")
    List<AgentMemory> findActiveSession(@Param("conversationId") String conversationId, @Param("owner") String ownerPrincipal, @Param("tenant") String tenantId, @Param("now") Instant now);

    @Query("select m from AgentMemory m where m.memoryScope = :scope and m.ownerPrincipal = :owner and m.tenantId = :tenant and (m.expiresAt is null or m.expiresAt > :now) order by m.priority desc, m.updatedAt desc")
    List<AgentMemory> findActiveScoped(@Param("scope") String memoryScope, @Param("owner") String ownerPrincipal, @Param("tenant") String tenantId, @Param("now") Instant now);

    List<AgentMemory> findByMemoryScopeAndOwnerPrincipalAndTenantIdOrderByPriorityDescUpdatedAtDesc(String memoryScope, String ownerPrincipal, String tenantId);

    long deleteByConversationId(String conversationId);
}
