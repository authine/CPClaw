package com.cpclaw.skill.yunshu.form;

import java.util.List;

/** A schema-governed field value. Model-proposed values remain candidates until validation accepts them. */
public record FormFieldSlot(
        String fieldCode,
        String displayName,
        String fieldType,
        Object value,
        String source,
        Double confidence,
        boolean required,
        boolean confirmed,
        String validationState,
        String errorMessage,
        List<String> allowedValues) {
    public FormFieldSlot {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }

    public FormFieldSlot withValidation(String state, String error) {
        return new FormFieldSlot(fieldCode, displayName, fieldType, value, source, confidence,
                required, confirmed, state, error, allowedValues);
    }
}
