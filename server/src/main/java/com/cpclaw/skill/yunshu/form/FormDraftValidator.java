package com.cpclaw.skill.yunshu.form;

import java.util.ArrayList;
import java.util.List;

/** Local, deterministic validation only. CloudPivot business validation belongs to a verified Provider contract. */
public final class FormDraftValidator {
    public List<FormValidationIssue> validate(List<FormFieldSlot> slots) {
        List<FormValidationIssue> issues = new ArrayList<>();
        for (FormFieldSlot slot : slots == null ? List.<FormFieldSlot>of() : slots) {
            if (slot.required() && isBlank(slot.value())) {
                issues.add(new FormValidationIssue(slot.fieldCode(), "REQUIRED", "该字段为必填项"));
            }
            if (!isBlank(slot.value()) && !slot.allowedValues().isEmpty()
                    && !slot.allowedValues().contains(String.valueOf(slot.value()))) {
                issues.add(new FormValidationIssue(slot.fieldCode(), "ENUM", "字段值不在允许范围内"));
            }
        }
        return List.copyOf(issues);
    }

    private boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
