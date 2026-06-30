package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudPivotEntityRelationRepository extends JpaRepository<CloudPivotEntityRelation, String> {
    List<CloudPivotEntityRelation> findBySourceEntityId(String sourceEntityId);
}
