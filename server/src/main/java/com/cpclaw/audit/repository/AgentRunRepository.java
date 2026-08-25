package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AgentRunRepository extends JpaRepository<AgentRun, String>, JpaSpecificationExecutor<AgentRun> {
}
