package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.ToolCall;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCallRepository extends JpaRepository<ToolCall, String> {
    List<ToolCall> findByAgentRunIdOrderByCreatedAtAsc(String agentRunId);
}
