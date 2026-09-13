package com.cpclaw.skill.yunshu.form;

import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.skill.yunshu.runtime.FormReference;
import com.cpclaw.skill.yunshu.runtime.ProviderFormSchema;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Builds a business form from the synchronized Yunshu metadata index. */
@Primary
@Component
public class LocalMetadataFormSchemaProvider implements YunshuFormSchemaProvider {
    private final MetadataSearchService search;
    private final CloudPivotEntityRepository entities;
    private final CloudPivotAppRepository apps;
    private final CloudPivotDataItemRepository dataItems;

    public LocalMetadataFormSchemaProvider(MetadataSearchService search,
            CloudPivotEntityRepository entities, CloudPivotAppRepository apps,
            CloudPivotDataItemRepository dataItems) {
        this.search = search;
        this.entities = entities;
        this.apps = apps;
        this.dataItems = dataItems;
    }

    @Override
    public ProviderFormSchema resolve(String goal, FormTaskScope scope) {
        String target = extractTarget(goal);
        List<MetadataSearchResult> candidates = search.searchLocalMetadata(target).stream()
                .filter(item -> "entity".equals(item.objectType()) && !blank(item.code())).toList();
        if (candidates.isEmpty()) {
            candidates = search.searchLocalMetadata(goal).stream()
                    .filter(item -> "entity".equals(item.objectType()) && !blank(item.code())).toList();
        }
        Map<String, CloudPivotEntity> usable = new LinkedHashMap<>();
        for (MetadataSearchResult candidate : candidates) {
            for (CloudPivotEntity entity : entities.findByEntityCodeIgnoreCase(candidate.code())) {
                // Search IDs bind a result to its application; identical schema codes are not interchangeable.
                if (!blank(candidate.objectId()) && !candidate.objectId().equals(entity.getId())) continue;
                if (dataItems.findByEntityId(entity.getId()).stream().anyMatch(this::isBusinessField)) {
                    usable.putIfAbsent(entity.getId(), entity);
                }
            }
        }
        List<CloudPivotEntity> matches = usable.values().stream()
                .filter(entity -> target.equalsIgnoreCase(safe(entity.getName())) || target.equalsIgnoreCase(safe(entity.getEntityCode())))
                .toList();
        if (matches.isEmpty()) matches = List.copyOf(usable.values());
        if (matches.size() > 1) {
            throw new AmbiguousFormTargetException(matches.stream().map(entity -> {
                CloudPivotApp app = apps.findById(entity.getAppId()).orElse(null);
                return Map.<String, Object>of("entityId", entity.getId(), "schemaCode", safe(entity.getEntityCode()),
                        "name", safe(entity.getName()), "applicationCode", app == null ? "" : safe(app.getAppCode()),
                        "evidence", "已同步元数据中的可填写对象");
            }).toList());
        }
        if (matches.isEmpty()) {
            return new ProviderFormSchema(new FormReference("", "", ""), "metadata-unresolved", List.of());
        }
        CloudPivotEntity entity = matches.getFirst();
        CloudPivotApp app = apps.findById(entity.getAppId()).orElse(null);
        String appCode = app == null ? "" : safe(app.getAppCode());
        List<FormFieldSlot> fields = dataItems.findByEntityId(entity.getId()).stream()
                .filter(this::isBusinessField)
                .sorted(Comparator.comparing(CloudPivotDataItem::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::toSlot)
                .toList();
        String version = entity.getSyncedAt() == null ? "metadata-index" : entity.getSyncedAt().toString();
        return new ProviderFormSchema(new FormReference(appCode, entity.getEntityCode(), entity.getEntityCode()), version, fields);
    }

    private FormFieldSlot toSlot(CloudPivotDataItem item) {
        return new FormFieldSlot(safe(item.getDataItemCode()), safe(item.getName()), normalizeType(item.getDataType()),
                null, "metadata", null, item.isRequired(), false, "pending", null, List.of());
    }

    private String normalizeType(String value) {
        String type = safe(value).toLowerCase();
        if (type.contains("date") || type.contains("time") || type.contains("日期") || type.contains("时间")) return "date";
        if (type.contains("number") || type.contains("decimal") || type.contains("金额") || type.contains("数字")) return "number";
        if (type.contains("bool") || type.contains("checkbox") || type.contains("开关")) return "boolean";
        if (type.contains("textarea") || type.contains("longtext") || type.contains("多行")) return "textarea";
        return "text";
    }

    private String extractTarget(String goal) {
        String value = safe(goal);
        var matcher = java.util.regex.Pattern.compile("(?:创建|新建|新增|填(?:写)?|录入|登记)(?:一个|一条|一份)?([^，,。；;：:\\s]{1,80})").matcher(value);
        if (matcher.find()) {
            String candidate = matcher.group(1).replaceAll("^(?:业务|数据|记录)", "").trim();
            if (!candidate.isBlank()) {
                return candidate;
            }
        }
        return value;
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return safe(value).isBlank(); }

    private boolean isBusinessField(CloudPivotDataItem item) {
        return !blank(item.getDataItemCode()) && !"SYSTEM".equalsIgnoreCase(safe(item.getFieldCategory()));
    }
}
