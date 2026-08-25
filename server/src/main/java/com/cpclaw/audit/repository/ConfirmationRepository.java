package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.Confirmation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfirmationRepository extends JpaRepository<Confirmation, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Confirmation c where c.id = :id")
    Optional<Confirmation> findByIdForExecution(@Param("id") String id);
}
