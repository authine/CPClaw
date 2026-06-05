package com.cpclaw.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "system_settings")
public class SystemSettings {

    @Id
    private String id;

    @Column(name = "cloudpivot_base_url")
    private String cloudPivotBaseUrl;

    @Column(name = "cloudpivot_username")
    private String cloudPivotUsername;

    @Column(name = "admin_cloudpivot_base_url")
    private String adminCloudPivotBaseUrl;

    @Column(name = "admin_cloudpivot_username")
    private String adminCloudPivotUsername;

    @Column(name = "search_engine_type")
    private String searchEngineType;

    @Column(name = "search_engine_url")
    private String searchEngineUrl;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCloudPivotBaseUrl() { return cloudPivotBaseUrl; }
    public void setCloudPivotBaseUrl(String cloudPivotBaseUrl) { this.cloudPivotBaseUrl = cloudPivotBaseUrl; }
    public String getCloudPivotUsername() { return cloudPivotUsername; }
    public void setCloudPivotUsername(String cloudPivotUsername) { this.cloudPivotUsername = cloudPivotUsername; }
    public String getAdminCloudPivotBaseUrl() { return adminCloudPivotBaseUrl; }
    public void setAdminCloudPivotBaseUrl(String adminCloudPivotBaseUrl) { this.adminCloudPivotBaseUrl = adminCloudPivotBaseUrl; }
    public String getAdminCloudPivotUsername() { return adminCloudPivotUsername; }
    public void setAdminCloudPivotUsername(String adminCloudPivotUsername) { this.adminCloudPivotUsername = adminCloudPivotUsername; }
    public String getSearchEngineType() { return searchEngineType; }
    public void setSearchEngineType(String searchEngineType) { this.searchEngineType = searchEngineType; }
    public String getSearchEngineUrl() { return searchEngineUrl; }
    public void setSearchEngineUrl(String searchEngineUrl) { this.searchEngineUrl = searchEngineUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
