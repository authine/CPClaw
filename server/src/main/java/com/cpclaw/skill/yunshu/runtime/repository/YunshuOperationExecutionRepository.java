package com.cpclaw.skill.yunshu.runtime.repository;

import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface YunshuOperationExecutionRepository extends JpaRepository<YunshuOperationExecution, String> {
    Optional<YunshuOperationExecution> findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(
            String tenantId, String subjectId, String hostId, String idempotencyKey);

    List<YunshuOperationExecution> findByTaskIdAndTenantIdAndSubjectIdAndHostIdOrderByUpdatedAtDesc(
            String taskId, String tenantId, String subjectId, String hostId);

    List<YunshuOperationExecution> findTop20ByStatusOrderByUpdatedAtAsc(String status);

    /**
     * Atomically claims an UNKNOWN execution for one reconciliation worker.
     * A stale lease is reclaimable after an instance crash; a live lease is
     * owned by exactly one worker across all application instances.
     */
    @Transactional
    @Modifying
    @Query("update YunshuOperationExecution execution set execution.reconciliationOwner = :owner, "
            + "execution.reconciliationClaimedAt = :claimedAt "
            + "where execution.id = :id and execution.status = :status "
            + "and (execution.reconciliationClaimedAt is null or execution.reconciliationClaimedAt < :staleBefore)")
    int claimForReconciliation(@Param("id") String id, @Param("status") String status,
            @Param("owner") String owner, @Param("claimedAt") java.time.Instant claimedAt,
            @Param("staleBefore") java.time.Instant staleBefore);

    /**
     * Row lock used by terminal state transitions. Reading the row through this
     * query makes the STARTED/UNKNOWN/FAILED/COMPLETED check a real
     * compare-and-set instead of a last-writer-wins overwrite.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from YunshuOperationExecution execution where execution.id = :id")
    Optional<YunshuOperationExecution> findByIdForUpdate(@Param("id") String id);
}
