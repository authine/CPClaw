package com.cpclaw.task.repository;

import com.cpclaw.task.entity.SemanticTaskRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemanticTaskRunRepository extends JpaRepository<SemanticTaskRun, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SemanticTaskRun r where r.id = :id")
    Optional<SemanticTaskRun> findLockedById(@Param("id") String id);
    Optional<SemanticTaskRun> findFirstByChannelAndInstallationKeyAndExternalPrincipalAndClientRequestId(
        String channel, String installationKey, String externalPrincipal, String clientRequestId
    );

    Optional<SemanticTaskRun> findFirstByChannelAndInstallationKeyAndExternalPrincipalAndTurnId(
        String channel, String installationKey, String externalPrincipal, String turnId
    );

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update SemanticTaskRun r set r.continuationConsumed = true where r.id = :id and r.continuationConsumed = false")
    int consumeContinuation(@org.springframework.data.repository.query.Param("id") String id);

}
