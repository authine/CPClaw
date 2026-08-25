package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.AgentModelCall;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentModelCallRepository extends JpaRepository<AgentModelCall, String> {
    List<AgentModelCall> findByAgentRunIdOrderByCreatedAtAsc(String agentRunId);
}
