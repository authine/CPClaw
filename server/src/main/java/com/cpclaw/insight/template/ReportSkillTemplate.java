package com.cpclaw.insight.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "report_skill_templates")
public class ReportSkillTemplate {
    @Id
    private String id;
    @Column(name = "skill_code", nullable = false, unique = true)
    private String skillCode;
    @Column(nullable = false)
    private String name;
    private String description;
    @Column(name = "object_aliases_json", columnDefinition = "LONGTEXT")
    private String objectAliasesJson;
    @Column(name = "trigger_hints_json", columnDefinition = "LONGTEXT")
    private String triggerHintsJson;
    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(nullable = false)
    private int priority;
    @Column(nullable = false)
    private int version = 1;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getObjectAliasesJson() { return objectAliasesJson; }
    public void setObjectAliasesJson(String objectAliasesJson) { this.objectAliasesJson = objectAliasesJson; }
    public String getTriggerHintsJson() { return triggerHintsJson; }
    public void setTriggerHintsJson(String triggerHintsJson) { this.triggerHintsJson = triggerHintsJson; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
