package com.cpclaw.skill.yunshu.form;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Applies host field patches without allowing a host or model to alter schema-owned slot attributes. */
public final class FormDraftService {
    private final FormDraftValidator validator = new FormDraftValidator();

    public DraftValidationResult patchAndValidate(List<FormFieldSlot> current, Map<String, Object> patch) {
        Map<String, Object> values = patch == null ? Map.of() : patch;
        Map<String, FormFieldSlot> merged = new LinkedHashMap<>();
        for (FormFieldSlot slot : current == null ? List.<FormFieldSlot>of() : current) {
            Object value = values.containsKey(slot.fieldCode()) ? values.get(slot.fieldCode()) : slot.value();
            String source = values.containsKey(slot.fieldCode()) ? "user" : slot.source();
            merged.put(slot.fieldCode(), new FormFieldSlot(slot.fieldCode(), slot.displayName(), slot.fieldType(),
                    value, source, slot.confidence(), slot.required(), slot.confirmed(), "pending", null,
                    slot.allowedValues()));
        }
        List<FormValidationIssue> issues = validator.validate(List.copyOf(merged.values()));
        Map<String, FormValidationIssue> byField = new LinkedHashMap<>();
        for (FormValidationIssue issue : issues) byField.putIfAbsent(issue.fieldCode(), issue);
        List<FormFieldSlot> slots = merged.values().stream().map(slot -> {
            FormValidationIssue issue = byField.get(slot.fieldCode());
            return issue == null ? slot.withValidation("valid", null) : slot.withValidation("invalid", issue.message());
        }).toList();
        return new DraftValidationResult(slots, issues);
    }

    public record DraftValidationResult(List<FormFieldSlot> slots, List<FormValidationIssue> issues) { }
}
