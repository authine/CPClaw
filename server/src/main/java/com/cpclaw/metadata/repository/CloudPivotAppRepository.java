package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.CloudPivotApp;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudPivotAppRepository extends JpaRepository<CloudPivotApp, String> {
    List<CloudPivotApp> findAllByOrderBySyncedAtDesc();
}
