package com.cpclaw.model.repository;

import com.cpclaw.model.entity.ModelConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, String> {

    List<ModelConfig> findByEnabledTrueOrderByUpdatedAtDesc();
}
