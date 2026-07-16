package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.CloudPivotEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudPivotEntityRepository extends JpaRepository<CloudPivotEntity, String> {
    List<CloudPivotEntity> findByAppId(String appId);

    List<CloudPivotEntity> findByEntityCodeIgnoreCase(String entityCode);
}
