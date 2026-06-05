package com.cpclaw.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "model_configs")
public class ModelConfig {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_base_url", nullable = false)
    private String apiBaseUrl;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "api_key_credential_id")
    private String apiKeyCredentialId;

    @Column(name = "supports_thinking", nullable = false)
    private boolean supportsThinking;

    @Column(name = "default_thinking_enabled", nullable = false)
    private boolean defaultThinkingEnabled;

    @Column(name = "default_temperature")
    private BigDecimal defaultTemperature;

    @Column(name = "default_max_tokens")
    private Integer defaultMaxTokens;

    @Column(name = "extra_body_json", columnDefinition = "TEXT")
    private String extraBodyJson;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getApiKeyCredentialId() { return apiKeyCredentialId; }
    public void setApiKeyCredentialId(String apiKeyCredentialId) { this.apiKeyCredentialId = apiKeyCredentialId; }
    public boolean isSupportsThinking() { return supportsThinking; }
    public void setSupportsThinking(boolean supportsThinking) { this.supportsThinking = supportsThinking; }
    public boolean isDefaultThinkingEnabled() { return defaultThinkingEnabled; }
    public void setDefaultThinkingEnabled(boolean defaultThinkingEnabled) { this.defaultThinkingEnabled = defaultThinkingEnabled; }
    public BigDecimal getDefaultTemperature() { return defaultTemperature; }
    public void setDefaultTemperature(BigDecimal defaultTemperature) { this.defaultTemperature = defaultTemperature; }
    public Integer getDefaultMaxTokens() { return defaultMaxTokens; }
    public void setDefaultMaxTokens(Integer defaultMaxTokens) { this.defaultMaxTokens = defaultMaxTokens; }
    public String getExtraBodyJson() { return extraBodyJson; }
    public void setExtraBodyJson(String extraBodyJson) { this.extraBodyJson = extraBodyJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
