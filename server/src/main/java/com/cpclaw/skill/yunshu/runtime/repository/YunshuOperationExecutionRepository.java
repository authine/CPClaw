package com.cpclaw.skill.yunshu.runtime.repository;

import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface YunshuOperationExecutionRepository extends JpaRepository<YunshuOperationExecution, String> {
    Optional<YunshuOperationExecution> findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(
            String tenantId, String subjectId, String hostId, String idempotencyKey);
}
