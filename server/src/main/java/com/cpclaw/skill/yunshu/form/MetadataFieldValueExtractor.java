package com.cpclaw.skill.yunshu.form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Conservative extraction of explicit assignments, bounded by the actual schema. */
public final class MetadataFieldValueExtractor {
    public Map<String, Object> extract(String text, List<FormFieldSlot> slots) {
        if (text == null || text.isBlank()) return Map.of();
        Map<String, List<String>> codesByLabel = new LinkedHashMap<>();
        for (FormFieldSlot slot : slots) {
            for (String label : List.of(slot.fieldCode(), slot.displayName())) {
                if (label != null && !label.isBlank()) {
                    List<String> codes = codesByLabel.computeIfAbsent(label, ignored -> new ArrayList<>());
                    if (!codes.contains(slot.fieldCode())) codes.add(slot.fieldCode());
                }
            }
        }
        List<String> labels = codesByLabel.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
        if (labels.isEmpty()) return Map.of();
        String alternatives = labels.stream().map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        Pattern assignment = Pattern.compile("(" + alternatives + ")\\s*(?:[:：=]|(?:修改为|改为|设为|设置为|为|是|叫))\\s*");
        var matcher = assignment.matcher(text);
        record Assignment(String label, int begin, int valueStart) { }
        List<Assignment> assignments = new ArrayList<>();
        while (matcher.find()) {
            // A short display name must not match inside a longer unknown field name.
            if (matcher.start() > 0 && Character.isLetterOrDigit(text.charAt(matcher.start() - 1))) continue;
            assignments.add(new Assignment(matcher.group(1), matcher.start(), matcher.end()));
        }
        Map<String, Object> patch = new LinkedHashMap<>();
        for (int i = 0; i < assignments.size(); i++) {
            Assignment item = assignments.get(i);
            if (codesByLabel.get(item.label()).size() != 1) continue;
            int end = i + 1 < assignments.size() ? assignments.get(i + 1).begin() : text.length();
            String value = text.substring(item.valueStart(), end).split("[，,；;。\\r\\n]", 2)[0].trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("“") && value.endsWith("”")))) value = value.substring(1, value.length() - 1);
            if (!value.isBlank()) patch.put(codesByLabel.get(item.label()).getFirst(), value);
        }
        if (patch.isEmpty()) {
            // Natural language often supplies the primary name without
            // repeating the field label. Only apply this
            // inference when exactly one empty name-like text field exists.
            List<FormFieldSlot> nameSlots = slots.stream()
                    .filter(slot -> slot.value() == null || String.valueOf(slot.value()).isBlank())
                    .filter(slot -> "text".equalsIgnoreCase(slot.fieldType()) || "textarea".equalsIgnoreCase(slot.fieldType()))
                    .filter(slot -> containsNameMarker(slot.displayName()) || containsNameMarker(slot.fieldCode()))
                    .toList();
            if (!nameSlots.isEmpty()) {
                var named = Pattern.compile("(?:叫做|叫|名为|名称是|姓名是)\\s*[“\\\"]?([^，,。；;\\r\\n“”\\\"]{2,80})").matcher(text);
                if (named.find()) {
                    FormFieldSlot target = chooseNameSlot(text, nameSlots);
                    if (target != null) patch.put(target.fieldCode(), named.group(1).trim());
                }
            }
        }
        return patch;
    }

    private boolean containsNameMarker(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase();
        return normalized.contains("名称") || normalized.contains("姓名") || normalized.contains("标题")
                || normalized.endsWith("_name") || normalized.endsWith("name");
    }

    private FormFieldSlot chooseNameSlot(String text, List<FormFieldSlot> slots) {
        String value = text == null ? "" : text;
        List<FormFieldSlot> preferred = slots.stream()
                .filter(slot -> slot.source() != null && slot.source().contains("primary-name"))
                .toList();
        return preferred.size() == 1 ? preferred.getFirst() : slots.size() == 1 ? slots.getFirst() : null;
    }
}
