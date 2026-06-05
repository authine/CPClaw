package com.cpclaw.audit.repository;

import com.cpclaw.audit.entity.Confirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmationRepository extends JpaRepository<Confirmation, String> {
}
