package com.cpclaw.skill.yunshu.form.repository;

import com.cpclaw.skill.yunshu.form.entity.FormTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FormTaskRepository extends JpaRepository<FormTask, String> {
    Optional<FormTask> findByIdAndTenantIdAndSubjectIdAndHostId(String id, String tenantId, String subjectId, String hostId);
    Optional<FormTask> findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(
            String tenantId, String subjectId, String hostId, String idempotencyKey);
}
