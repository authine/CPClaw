package com.cpclaw.yunshu.confirmation.repository;

import com.cpclaw.yunshu.confirmation.entity.YunshuConfirmationTicket;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface YunshuConfirmationTicketRepository extends JpaRepository<YunshuConfirmationTicket, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from YunshuConfirmationTicket t where t.id = :id")
    Optional<YunshuConfirmationTicket> findByIdForConsumption(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<YunshuConfirmationTicket> findFirstByTaskIdAndTenantIdAndSubjectIdAndHostIdAndStatus(
            String taskId, String tenantId, String subjectId, String hostId, String status);
}
