package com.cpclaw.skill.yunshu.form;

import java.util.List;
import java.util.Map;

public record FormTaskSnapshot(String taskId, FormTaskScope scope, String status, Map<String, Object> formRef,
        String metadataVersion, long stateVersion, long draftVersion, List<FormFieldSlot> slots,
        List<FormValidationIssue> validationIssues) {
    public FormTaskSnapshot {
        formRef = formRef == null ? Map.of() : Map.copyOf(formRef);
        slots = slots == null ? List.of() : List.copyOf(slots);
        validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
    }
}
