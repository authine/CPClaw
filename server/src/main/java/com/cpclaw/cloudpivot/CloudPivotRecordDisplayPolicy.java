package com.cpclaw.cloudpivot;

import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CloudPivotRecordDisplayPolicy {

    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotDataItemRepository dataItemRepository;
    private final CloudPivotRuntimeProperties properties;

    public CloudPivotRecordDisplayPolicy(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotRuntimeProperties properties
    ) {
        this.entityRepository = entityRepository;
        this.dataItemRepository = dataItemRepository;
        this.properties = properties;
    }

    public DisplayContext context(String schemaCode) {
        Map<String, String> labels = new LinkedHashMap<>();
        entityRepository.findByEntityCode(schemaCode).stream()
            .map(CloudPivotEntity::getId)
            .flatMap(entityId -> dataItemRepository.findByEntityId(entityId).stream())
            .filter(item -> hasText(item.getDataItemCode()) && hasText(item.getName()))
            .forEach(item -> labels.putIfAbsent(normalize(item.getDataItemCode()), item.getName()));
        return new DisplayContext(Map.copyOf(labels));
    }

    public String summarize(DisplayContext context, Map<?, ?> values) {
        CloudPivotRuntimeProperties.Display display = properties.getDisplay();
        List<DisplayField> candidates = values.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .filter(entry -> !isHidden(entry.getKey(), entry.getValue(), display))
            .map(entry -> toDisplayField(context, entry.getKey(), entry.getValue(), display))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted(Comparator.comparingInt(DisplayField::priority))
            .toList();

        List<String> fields = new ArrayList<>();
        Set<String> displayedValues = new LinkedHashSet<>();
        Set<String> displayedLabels = new LinkedHashSet<>();
        for (DisplayField candidate : candidates) {
            if (fields.size() >= display.getMaxFields()) {
                break;
            }
            if (!displayedValues.add(candidate.value()) || !displayedLabels.add(candidate.label())) {
                continue;
            }
            fields.add(candidate.label() + "=" + candidate.value());
        }
        return fields.isEmpty() ? "无可展示字段" : String.join("，", fields);
    }

    private Optional<DisplayField> toDisplayField(
        DisplayContext context,
        Object key,
        Object value,
        CloudPivotRuntimeProperties.Display display
    ) {
        String rawKey = String.valueOf(key);
        String normalizedKey = normalize(rawKey);
        String text = valueText(value, display);
        if (!hasText(text)) {
            return Optional.empty();
        }
        Optional<CloudPivotRuntimeProperties.FieldRule> rule = matchingRule(rawKey, display);
        String metadataLabel = context.labels().get(normalizedKey);
        String label = hasText(metadataLabel)
            ? metadataLabel
            : rule.map(CloudPivotRuntimeProperties.FieldRule::getLabel)
                .filter(this::hasText)
                .orElseGet(() -> display.getFallbackLabels().getOrDefault(rawKey, rawKey));
        int priority = rule.map(CloudPivotRuntimeProperties.FieldRule::getPriority).orElse(display.getDefaultPriority());
        return Optional.of(new DisplayField(label, text, priority));
    }

    private Optional<CloudPivotRuntimeProperties.FieldRule> matchingRule(
        String key,
        CloudPivotRuntimeProperties.Display display
    ) {
        String normalizedKey = normalize(key);
        return display.getFieldRules().stream()
            .filter(rule -> rule.getExact().stream().map(this::normalize).anyMatch(normalizedKey::equals)
                || rule.getContains().stream().map(this::normalize).anyMatch(normalizedKey::contains))
            .findFirst();
    }

    private boolean isHidden(Object key, Object value, CloudPivotRuntimeProperties.Display display) {
        String normalizedKey = normalize(String.valueOf(key));
        if (value instanceof Iterable<?> || value != null && value.getClass().isArray()) {
            return true;
        }
        if (display.getHiddenExact().stream().map(this::normalize).anyMatch(normalizedKey::equals)
            || display.getHiddenContains().stream().map(this::normalize).anyMatch(normalizedKey::contains)) {
            return true;
        }
        boolean hiddenSuffix = display.getHiddenSuffixes().stream().map(this::normalize).anyMatch(normalizedKey::endsWith);
        if (hiddenSuffix && value instanceof Map<?, ?> map) {
            return nestedDisplayValue(map, display).isBlank();
        }
        if (hiddenSuffix) {
            return true;
        }
        String text = value instanceof CharSequence ? String.valueOf(value).trim() : "";
        return hasText(text) && display.getIdentifierPatterns().stream().anyMatch(pattern -> Pattern.matches(pattern, text));
    }

    private String valueText(Object value, CloudPivotRuntimeProperties.Display display) {
        String text = value instanceof Map<?, ?> map
            ? nestedDisplayValue(map, display)
            : String.valueOf(value);
        if (text.length() <= display.getMaxValueLength()) {
            return text;
        }
        return text.substring(0, display.getMaxValueLength()) + "...";
    }

    private String nestedDisplayValue(Map<?, ?> map, CloudPivotRuntimeProperties.Display display) {
        for (String key : display.getNestedValueKeys()) {
            Object nested = map.get(key);
            if (nested != null && hasText(String.valueOf(nested))) {
                return String.valueOf(nested);
            }
        }
        return "";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record DisplayContext(Map<String, String> labels) {
    }

    private record DisplayField(String label, String value, int priority) {
    }
}
