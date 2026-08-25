package com.cpclaw.mcp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mcp_installations")
public class McpInstallation {
    @Id private String id;
    @Column(name = "installation_key", nullable = false, unique = true) private String installationKey;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(nullable = false) private String status;
    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;
    @Column(name = "enabled_at") private Instant enabledAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getInstallationKey() { return installationKey; }
    public void setInstallationKey(String installationKey) { this.installationKey = installationKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getEnabledAt() { return enabledAt; }
    public void setEnabledAt(Instant enabledAt) { this.enabledAt = enabledAt; }
}
