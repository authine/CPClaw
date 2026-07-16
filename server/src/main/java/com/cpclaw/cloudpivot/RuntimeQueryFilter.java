package com.cpclaw.cloudpivot;

public record RuntimeQueryFilter(
    String fieldCode,
    String fieldName,
    String operator,
    String value,
    String source,
    double confidence
) {
    public boolean valid() {
        return hasText(fieldCode) && hasText(value);
    }

    public String summary() {
        String label = hasText(fieldName) ? fieldName : fieldCode;
        return label + "(" + fieldCode + ") " + safe(operator, "like") + " " + value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String safe(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }
}
