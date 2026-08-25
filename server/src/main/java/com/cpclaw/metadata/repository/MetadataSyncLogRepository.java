package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.MetadataSyncLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetadataSyncLogRepository extends JpaRepository<MetadataSyncLog, String> {
    List<MetadataSyncLog> findTop20ByOrderByStartedAtDesc();
    List<MetadataSyncLog> findTop1ByStatusOrderByCompletedAtDesc(String status);
}
