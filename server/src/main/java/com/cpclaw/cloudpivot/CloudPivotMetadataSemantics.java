package com.cpclaw.cloudpivot;

import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yunshu metadata semantics used by the Yunshu skill. This is field metadata
 * classification, not a business-object rule: fields not in the provider's
 * system-field catalog remain business fields by definition.
 */
public final class CloudPivotMetadataSemantics {
    public static final String BUSINESS_FIELD = "BUSINESS";
    public static final String SYSTEM_FIELD = "SYSTEM";

    private static final Set<String> SYSTEM_FIELD_CODES = Set.of(
        "ownerdeptid", "modifiedtime", "name", "createddeptid", "creater",
        "sequenceno", "createdtime", "modifier", "owner",
        "workflowinstanceid", "processinstanceid", "sequencestatus",
        "workflowstatus", "processstatus", "systemstatus", "datastatus", "recordstatus",
        "createdby", "modifiedby", "deleted", "tenantid", "ownerdeptquerycode", "id"
    );
    private static final Pattern JSON_TEXT = Pattern.compile("\\\"(?:appDescription|description|remark|helpText|tips)\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_ZH = Pattern.compile("\\\"zh\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"", Pattern.CASE_INSENSITIVE);

    private CloudPivotMetadataSemantics() {
    }

    public static String fieldCategory(String code, String name) {
        String normalizedCode = normalize(code);
        String normalizedName = normalize(name);
        return SYSTEM_FIELD_CODES.contains(normalizedCode) || SYSTEM_FIELD_CODES.contains(normalizedName)
            ? SYSTEM_FIELD : BUSINESS_FIELD;
    }

    /**
     * Stable provider-field presentation order: the record identifier first,
     * then the remaining system fields, then business fields.
     */
    public static Comparator<String> fieldCodeComparator() {
        return Comparator.comparingInt(CloudPivotMetadataSemantics::fieldOrder)
            .thenComparing(value -> normalize(value));
    }

    public static int fieldOrder(String code) {
        return "id".equals(normalize(code)) ? 0 : SYSTEM_FIELD_CODES.contains(normalize(code)) ? 1 : 2;
    }

    public static String fieldDescription(String code, String name, String dataType, String existingDescription, String category) {
        if (existingDescription != null && !existingDescription.isBlank()) return existingDescription.trim();
        String label = name == null || name.isBlank() ? code : name;
        String role = SYSTEM_FIELD.equals(category) ? "云枢系统字段" : "云枢业务字段";
        String type = dataType == null || dataType.isBlank() ? "未标注类型" : dataType;
        return role + "：" + label + "，数据类型为" + type + "。";
    }

    /**
     * Human-facing CloudPivot field-type label. The synchronized data type is
     * retained unchanged for runtime execution; this label prevents provider
     * enum values from leaking into metadata views.
     */
    public static String fieldTypeDisplayName(String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return "未标注类型";
        }
        String value = dataType.trim();
        return switch (normalize(value)) {
            case "0" -> "文本（Text）";
            case "3" -> "日期时间（DateTime）";
            case "50" -> "人员（User）";
            case "60" -> "部门（Department）";
            default -> value.matches("\\d+") ? "云枢字段类型" : value;
        };
    }

    public static String entityDescription(String code, String name, String existingDescription) {
        if (existingDescription != null && !existingDescription.isBlank()) return existingDescription.trim();
        String label = name == null || name.isBlank() ? code : name;
        return "云枢业务模型“" + label + "”（编码：" + (code == null ? "" : code) + "），用于承载该模型的业务数据、字段和关联关系。";
    }

    public static String appDescription(String code, String name, String existingDescription) {
        String clean = extractDescription(existingDescription);
        if (clean != null) return clean;
        String label = name == null || name.isBlank() ? code : name;
        return "云枢应用“" + label + "”（编码：" + (code == null ? "" : code) + "），包含该应用下的业务模型、字段、关联关系和可用操作。";
    }

    private static String extractDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.startsWith("\\\"") && text.endsWith("\\\"")) {
            text = unescape(text.substring(1, text.length() - 1));
        }
        if (!looksLikeJson(text)) return text;
        Matcher matcher = JSON_TEXT.matcher(text);
        if (matcher.find()) {
            String candidate = unescape(matcher.group(1));
            if (!candidate.isBlank() && !looksLikeJson(candidate)) return candidate;
        }
        matcher = JSON_ZH.matcher(text);
        if (matcher.find()) {
            String candidate = unescape(matcher.group(1));
            if (!candidate.isBlank() && !looksLikeJson(candidate)) return candidate;
        }
        return null;
    }

    private static String unescape(String value) {
        return value.replace("\\\\\"", "\\\"").replace("\\\\\\", "\\\\").trim();
    }

    private static boolean looksLikeJson(String value) {
        String text = value.trim();
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }
}
