package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.CloudPivotDataItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudPivotDataItemRepository extends JpaRepository<CloudPivotDataItem, String> {
    List<CloudPivotDataItem> findByEntityId(String entityId);
}
