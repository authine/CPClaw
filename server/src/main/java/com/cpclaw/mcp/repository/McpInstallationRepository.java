package com.cpclaw.mcp.repository;

import com.cpclaw.mcp.entity.McpInstallation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpInstallationRepository extends JpaRepository<McpInstallation, String> {
    Optional<McpInstallation> findByInstallationKey(String installationKey);
}
