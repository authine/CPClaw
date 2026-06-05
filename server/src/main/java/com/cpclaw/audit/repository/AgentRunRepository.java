package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
}
