package com.cpclaw.feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserFeedbackRepository extends JpaRepository<UserFeedback,String> {
    List<UserFeedback> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
    java.util.Optional<UserFeedback> findByIdAndTenantId(String id, String tenantId);
}
