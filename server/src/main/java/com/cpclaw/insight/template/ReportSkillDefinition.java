package com.cpclaw.insight.template;

public record ReportSkillDefinition(
    String id,
    String name,
    String description,
    int version,
    String configJson,
    String selectionReason
) {
}
