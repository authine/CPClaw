package com.cpclaw.metadata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cloudpivot_api_endpoints")
public class CloudPivotApiEndpoint {
    @Id private String id;
    @Column(name = "api_code", nullable = false) private String apiCode;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String method;
    @Column(nullable = false) private String path;
    @Column(nullable = false) private String category;
    @Column(name = "operation_type", nullable = false) private String operationType;
    @Column(name = "risk_level", nullable = false) private String riskLevel;
    @Column(name = "requires_confirmation", nullable = false) private boolean requiresConfirmation;
    @Column(name = "input_schema_json", columnDefinition = "LONGTEXT") private String inputSchemaJson;
    @Column(name = "output_schema_json", columnDefinition = "LONGTEXT") private String outputSchemaJson;
    @Column(name = "data_scope", columnDefinition = "TEXT") private String dataScope;
    @Column(name = "applicable_object_type") private String applicableObjectType;
    @Column(name = "raw_json", columnDefinition = "LONGTEXT") private String rawJson;
    @Column(name = "synced_at") private Instant syncedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApiCode() { return apiCode; }
    public void setApiCode(String apiCode) { this.apiCode = apiCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    public String getInputSchemaJson() { return inputSchemaJson; }
    public void setInputSchemaJson(String inputSchemaJson) { this.inputSchemaJson = inputSchemaJson; }
    public String getOutputSchemaJson() { return outputSchemaJson; }
    public void setOutputSchemaJson(String outputSchemaJson) { this.outputSchemaJson = outputSchemaJson; }
    public String getDataScope() { return dataScope; }
    public void setDataScope(String dataScope) { this.dataScope = dataScope; }
    public String getApplicableObjectType() { return applicableObjectType; }
    public void setApplicableObjectType(String applicableObjectType) { this.applicableObjectType = applicableObjectType; }
    public String getRawJson() { return rawJson; }
    public void setRawJson(String rawJson) { this.rawJson = rawJson; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
