package com.cpclaw.metadata.repository;

import com.cpclaw.metadata.entity.CloudPivotApiEndpoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CloudPivotApiEndpointRepository extends JpaRepository<CloudPivotApiEndpoint, String> {
    List<CloudPivotApiEndpoint> findByOperationTypeIn(List<String> operationTypes);
}
