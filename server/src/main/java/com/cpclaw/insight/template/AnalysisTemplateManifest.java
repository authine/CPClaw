package com.cpclaw.insight.template;

import java.util.List;
import java.util.Map;

/**
 * Declarative, versioned scenario-template contract. The platform only
 * interprets the whitelisted operator names; vocabulary and field selectors
 * belong to the template plugin that owns this manifest.
 */
public record AnalysisTemplateManifest(
    String id,
    String version,
    String skillId,
    Activation activation,
    List<FieldSelector> fieldSelectors,
    List<Operation> plan,
    Output output,
    Map<String, Object> riskRules
) {
    public record Activation(List<String> triggerHints, List<String> objectSelectors) { }
    public record FieldSelector(String role, List<String> semanticHints, List<String> fieldCodes) { }
    public record Operation(String op, String input, String as, Map<String, Object> options) { }
    public record Output(List<String> sections, List<String> charts, int maxChars) { }
}
