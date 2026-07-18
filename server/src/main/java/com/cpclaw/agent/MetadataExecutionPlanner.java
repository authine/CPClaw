package com.cpclaw.agent;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.CloudPivotApiEndpoint;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.repository.CloudPivotApiEndpointRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MetadataExecutionPlanner {

    private static final String OWNER = "\u8d1f\u8d23\u4eba";
    private static final String SALES = "\u9500\u552e";
    private static final String SALESMAN = "\u4e1a\u52a1\u5458";
    private static final String OWNER_SALES = "\u5f52\u5c5e\u9500\u552e";

    private final CloudPivotApiEndpointRepository apiEndpointRepository;
    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotDataItemRepository dataItemRepository;
    private final CloudPivotEntityRelationRepository relationRepository;

    public MetadataExecutionPlanner(
        CloudPivotApiEndpointRepository apiEndpointRepository,
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository
    ) {
        this.apiEndpointRepository = apiEndpointRepository;
        this.entityRepository = entityRepository;
        this.dataItemRepository = dataItemRepository;
        this.relationRepository = relationRepository;
    }

    public MetadataExecutionPlan plan(String userQuestion, MetadataSearchResult candidate) {
        if (candidate == null || candidate.objectType() == null) {
            return MetadataExecutionPlan.empty(candidate, "no metadata candidate");
        }
        return switch (candidate.objectType()) {
            case "entity" -> entityPlan(userQuestion, candidate.objectId(), candidate, "candidate is executable entity");
            case "data_item" -> dataItemPlan(userQuestion, candidate.objectId(), candidate);
            case "relation" -> relationPlan(userQuestion, candidate.objectId(), candidate);
            default -> MetadataExecutionPlan.empty(candidate, "candidate is not executable business entity");
        };
    }

    public MetadataExecutionPlan inherited(MetadataSearchResult candidate, String userQuestion) {
        if (candidate == null || !hasText(candidate.code())) {
            return MetadataExecutionPlan.empty(candidate, "inherited runtime object missing schemaCode");
        }
        Optional<CloudPivotEntity> entity = entityRepository.findByEntityCodeIgnoreCase(candidate.code()).stream().findFirst();
        if (entity.isPresent()) {
            return entityPlan(userQuestion, entity.get().getId(), candidate, "inherited previous runtime object schemaCode=" + candidate.code());
        }
        List<ApiHint> apiHints = selectApiHints(userQuestion);
        return MetadataExecutionPlan.inherited(candidate, apiHints);
    }

    private MetadataExecutionPlan dataItemPlan(String userQuestion, String dataItemId, MetadataSearchResult candidate) {
        Optional<CloudPivotDataItem> dataItem = dataItemRepository.findById(dataItemId);
        if (dataItem.isEmpty()) {
            List<CloudPivotDataItem> byCode = dataItemRepository.findByDataItemCodeIgnoreCase(candidate.code());
            if (!byCode.isEmpty()) {
                dataItem = Optional.of(selectBestDataItem(userQuestion, byCode));
            }
        }
        if (dataItem.isEmpty()) {
            return MetadataExecutionPlan.empty(candidate, "data item cannot be traced to entity");
        }
        CloudPivotDataItem item = dataItem.get();
        Optional<CloudPivotEntity> entity = entityRepository.findById(item.getEntityId());
        if (entity.isEmpty()) {
            return MetadataExecutionPlan.empty(candidate, "data item entity not found");
        }
        MetadataExecutionPlan base = entityPlan(userQuestion, entity.get().getId(), candidate, "candidate data item traced to entity " + entity.get().getName());
        List<FieldHint> fields = new ArrayList<>(base.fieldHints());
        addField(fields, item);
        List<String> reasoning = new ArrayList<>(base.reasoningSteps());
        reasoning.add("field hint from matched data item " + item.getName() + "/" + item.getDataItemCode());
        return base.withFields(fields, reasoning);
    }

    private MetadataExecutionPlan relationPlan(String userQuestion, String relationId, MetadataSearchResult candidate) {
        Optional<CloudPivotEntityRelation> relation = relationRepository.findById(relationId);
        if (relation.isEmpty()) {
            return MetadataExecutionPlan.empty(candidate, "relation not found");
        }
        CloudPivotEntityRelation value = relation.get();
        Optional<CloudPivotEntity> sourceEntity = entityRepository.findById(value.getSourceEntityId());
        Optional<CloudPivotEntity> targetEntity = entityRepository.findById(value.getTargetEntityId());
        if (sourceEntity.isEmpty()) {
            return MetadataExecutionPlan.empty(candidate, "relation source entity not found");
        }
        MetadataExecutionPlan base = entityPlan(userQuestion, sourceEntity.get().getId(), candidate, "candidate relation traced to source entity " + sourceEntity.get().getName());
        List<RelationHint> relations = new ArrayList<>(base.relationHints());
        relations.add(toRelationHint(value, sourceEntity.orElse(null), targetEntity.orElse(null)));
        List<String> reasoning = new ArrayList<>(base.reasoningSteps());
        reasoning.add("relation hint " + safe(value.getRelationName(), candidate.name()));
        return base.withRelations(relations, reasoning);
    }

    private MetadataExecutionPlan entityPlan(String userQuestion, String entityId, MetadataSearchResult candidate, String reason) {
        Optional<CloudPivotEntity> entity = entityRepository.findById(entityId);
        if (entity.isEmpty()) {
            if (candidate != null && "entity".equals(candidate.objectType()) && hasText(candidate.code())) {
                return MetadataExecutionPlan.of(candidate, candidate.name(), candidate.code(), List.of(), List.of(), selectApiHints(userQuestion), List.of(reason, "entity repository missed candidate id"));
            }
            return MetadataExecutionPlan.empty(candidate, "executable entity not found");
        }
        CloudPivotEntity value = entity.get();
        List<CloudPivotDataItem> dataItems = dataItemRepository.findByEntityId(value.getId());
        List<RelationHint> relationHints = relationHints(value.getId());
        List<FieldHint> fieldHints = selectFieldHints(userQuestion, dataItems, relationHints);
        List<ApiHint> apiHints = selectApiHints(userQuestion);
        List<String> reasoning = new ArrayList<>();
        reasoning.add(reason);
        if (!fieldHints.isEmpty()) {
            reasoning.add("field hints: " + joinFieldHints(fieldHints, 5));
        }
        if (!relationHints.isEmpty()) {
            reasoning.add("relation hints: " + joinRelationHints(relationHints, 3));
        }
        if (!apiHints.isEmpty()) {
            reasoning.add("api hints: " + joinApiHints(apiHints, 3));
        }
        return MetadataExecutionPlan.of(candidate, value.getName(), value.getEntityCode(), fieldHints, relationHints, apiHints, reasoning);
    }

    private List<ApiHint> selectApiHints(String userQuestion) {
        return apiEndpointRepository.findByOperationTypeIn(operationTypesForQuestion(userQuestion)).stream()
            .map(this::toApiHint)
            .toList();
    }

    private List<String> operationTypesForQuestion(String userQuestion) {
        String query = compact(userQuestion);
        if (containsAny(query, List.of("\u5220\u9664", "\u79fb\u9664", "\u4f5c\u5e9f"))) {
            return List.of("delete");
        }
        if (containsAny(query, List.of("\u65b0\u589e", "\u521b\u5efa", "\u65b0\u5efa", "\u4fee\u6539", "\u66f4\u65b0", "\u4fdd\u5b58", "\u5199\u5165"))) {
            return List.of("create_update");
        }
        if (containsAny(query, List.of("\u7b2c\u4e00\u6761", "\u7b2c\u4e00\u4e2a", "\u8be6\u60c5", "\u660e\u7ec6", "\u4fe1\u606f"))) {
            return List.of("query_collection", "query_detail");
        }
        return List.of("query_collection");
    }

    private ApiHint toApiHint(CloudPivotApiEndpoint endpoint) {
        return new ApiHint(endpoint.getApiCode(), endpoint.getName(), endpoint.getMethod(), endpoint.getPath(), endpoint.getOperationType(), endpoint.getRiskLevel(), endpoint.isRequiresConfirmation(), endpoint.getDataScope());
    }

    private List<FieldHint> selectFieldHints(String userQuestion, List<CloudPivotDataItem> dataItems, List<RelationHint> relationHints) {
        String query = compact(userQuestion);
        Set<String> selectedIds = new LinkedHashSet<>();
        List<FieldHint> result = new ArrayList<>();
        if (looksLikeOwnerFilterQuestion(query)) {
            relationHints.stream()
                .filter(this::isLikelyOwnerRelation)
                .map(RelationHint::sourceDataItemId)
                .filter(this::hasText)
                .map(this::findDataItemByIdOrCode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(item -> selectedIds.add(item.getId()))
                .map(this::toFieldHint)
                .forEach(result::add);
        }
        for (CloudPivotDataItem item : dataItems) {
            if (isAnalyticalField(query, item)) {
                if (selectedIds.add(item.getId())) {
                    result.add(toFieldHint(item));
                }
            }
        }
        for (CloudPivotDataItem item : dataItems) {
            if (fieldMatchesQuestion(query, item)) {
                if (selectedIds.add(item.getId())) {
                    result.add(toFieldHint(item));
                }
            }
        }
        dataItems.stream()
            .filter(item -> isLikelyNameField(item) || isLikelyStatusField(item) || isLikelyAmountField(item) || isLikelyOwnerField(item) || item.isReference())
            .sorted(Comparator.comparingInt(item -> fieldPriority(query, item)))
            .filter(item -> selectedIds.add(item.getId()))
            .limit(Math.max(0, 8 - result.size()))
            .map(this::toFieldHint)
            .forEach(result::add);
        return result.stream().limit(8).toList();
    }

    private Optional<CloudPivotDataItem> findDataItemByIdOrCode(String value) {
        Optional<CloudPivotDataItem> byId = dataItemRepository.findById(value);
        if (byId.isPresent()) {
            return byId;
        }
        return dataItemRepository.findByDataItemCodeIgnoreCase(value).stream().findFirst();
    }

    private int fieldPriority(String query, CloudPivotDataItem item) {
        if (looksLikeOwnerFilterQuestion(query) && isLikelyOwnerField(item)) return 0;
        if (isAnalyticalField(query, item)) return 1;
        if (fieldMatchesQuestion(query, item)) return 2;
        if (isLikelyNameField(item)) return 2;
        if (isLikelyAmountField(item) || isLikelyStatusField(item)) return 3;
        return 4;
    }

    private List<RelationHint> relationHints(String entityId) {
        List<RelationHint> result = new ArrayList<>();
        relationRepository.findBySourceEntityId(entityId).stream()
            .map(relation -> toRelationHint(relation, entityRepository.findById(relation.getSourceEntityId()).orElse(null), entityRepository.findById(relation.getTargetEntityId()).orElse(null)))
            .forEach(result::add);
        relationRepository.findByTargetEntityId(entityId).stream()
            .map(relation -> toRelationHint(relation, entityRepository.findById(relation.getSourceEntityId()).orElse(null), entityRepository.findById(relation.getTargetEntityId()).orElse(null)))
            .forEach(result::add);
        return result.stream().limit(6).toList();
    }

    private CloudPivotDataItem selectBestDataItem(String userQuestion, List<CloudPivotDataItem> items) {
        String query = compact(userQuestion);
        return items.stream().max(Comparator.comparingInt(item -> fieldScore(query, item))).orElse(items.getFirst());
    }

    private int fieldScore(String query, CloudPivotDataItem item) {
        int score = 0;
        if (fieldMatchesQuestion(query, item)) score += 100;
        if (isAnalyticalField(query, item)) score += 60;
        if (item.isReference()) score += 20;
        return score;
    }

    private boolean fieldMatchesQuestion(String query, CloudPivotDataItem item) {
        return query.contains(compact(item.getName())) || query.contains(compact(item.getDataItemCode())) || query.contains(compact(item.getDescription()));
    }

    private boolean isAnalyticalField(String query, CloudPivotDataItem item) {
        if ((query.contains("\u91d1\u989d") || query.contains("amount") || query.contains("money") || query.contains("revenue")) && isLikelyAmountField(item)) return true;
        if ((query.contains("\u9636\u6bb5") || query.contains("\u72b6\u6001") || query.contains("status") || query.contains("stage")) && isLikelyStatusField(item)) return true;
        if ((query.contains("\u5ba2\u6237") || query.contains("\u5173\u8054")) && item.isReference()) return true;
        return looksLikeOwnerFilterQuestion(query) && isLikelyOwnerField(item);
    }

    private boolean looksLikeOwnerFilterQuestion(String query) {
        return query.contains(OWNER) || query.contains(SALES) || query.contains(SALESMAN) || query.contains(OWNER_SALES)
            || query.contains("\u540d\u4e0b")
            || query.matches(".*[\\p{IsHan}]{2,5}\u7684(\u5546\u673a|\u9879\u76ee|\u5ba2\u6237|\u7ebf\u7d22|\u8ba2\u5355).*(\u91d1\u989d|\u591a\u5c11|\u51e0\u4e2a|\u51e0\u6761).*")
            || query.matches(".*[a-z][a-z0-9._-]{1,30}\u7684(\u5546\u673a|\u9879\u76ee|\u5ba2\u6237|\u7ebf\u7d22|\u8ba2\u5355).*(\u91d1\u989d|\u591a\u5c11|\u51e0\u4e2a|\u51e0\u6761).*")
            || query.matches(".*[\\p{IsHan}]{2,5}(\\u6709\\u591a\\u5c11|\\u6709\\u51e0\\u4e2a|\\u6709\\u51e0\\u6761).*")
            || query.matches(".*[a-z][a-z0-9._-]{1,30}(\\u6709\\u591a\\u5c11|\\u6709\\u51e0\\u4e2a|\\u6709\\u51e0\\u6761).*");
    }

    private boolean isLikelyNameField(CloudPivotDataItem item) {
        String value = compact(item.getName() + item.getDataItemCode());
        return value.contains("\u540d\u79f0") || value.contains("name") || value.contains("title") || value.contains("instancename");
    }

    private boolean isLikelyStatusField(CloudPivotDataItem item) {
        String value = compact(item.getName() + item.getDataItemCode());
        return value.contains("\u72b6\u6001") || value.contains("\u9636\u6bb5") || value.contains("status") || value.contains("stage") || value.contains("state");
    }

    private boolean isLikelyAmountField(CloudPivotDataItem item) {
        String value = compact(item.getName() + item.getDataItemCode());
        return value.contains("\u91d1\u989d") || value.contains("\u5408\u540c\u989d") || value.contains("amount") || value.contains("money") || value.contains("revenue");
    }

    private boolean isLikelyOwnerField(CloudPivotDataItem item) {
        String value = compact(item.getName() + item.getDataItemCode() + item.getDescription());
        return isOwnerText(value);
    }

    private boolean isLikelyOwnerRelation(RelationHint relation) {
        if (relation == null) return false;
        return isOwnerText(compact(relation.relationName() + relation.targetEntityName() + relation.sourceDataItemId()));
    }

    private boolean isOwnerText(String value) {
        return value.contains(OWNER) || value.contains(SALES) || value.contains(SALESMAN) || value.contains(OWNER_SALES)
            || value.contains("owner") || value.contains("sales") || value.contains("seller");
    }

    private void addField(List<FieldHint> fields, CloudPivotDataItem item) {
        FieldHint hint = toFieldHint(item);
        boolean exists = fields.stream().anyMatch(field -> field.code().equalsIgnoreCase(hint.code()));
        if (!exists) fields.add(0, hint);
    }

    private FieldHint toFieldHint(CloudPivotDataItem item) {
        return new FieldHint(item.getName(), item.getDataItemCode(), item.getDataType(), item.isReference(), item.getDescription());
    }

    private RelationHint toRelationHint(CloudPivotEntityRelation relation, CloudPivotEntity source, CloudPivotEntity target) {
        String sourceName = source == null ? relation.getSourceEntityId() : source.getName();
        String targetName = target == null ? relation.getTargetEntityId() : target.getName();
        String sourceCode = source == null ? "" : source.getEntityCode();
        String targetCode = target == null ? "" : target.getEntityCode();
        String relationName = safe(relation.getRelationName(), sourceName + "->" + targetName);
        return new RelationHint(sourceName, sourceCode, targetName, targetCode, relationName, relation.getRelationType(), relation.getSourceDataItemId());
    }

    private boolean containsAny(String value, List<String> keywords) {
        for (String keyword : keywords) {
            if (value.contains(compact(keyword))) return true;
        }
        return false;
    }

    private String compact(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safe(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private String joinFieldHints(List<FieldHint> hints, int limit) {
        return hints.stream().map(FieldHint::displayName).limit(limit).reduce((left, right) -> left + ", " + right).orElse("no field hints");
    }

    private String joinRelationHints(List<RelationHint> hints, int limit) {
        return hints.stream().map(RelationHint::summary).limit(limit).reduce((left, right) -> left + "; " + right).orElse("no relation hints");
    }

    private String joinApiHints(List<ApiHint> hints, int limit) {
        return hints.stream().map(ApiHint::summary).limit(limit).reduce((left, right) -> left + "; " + right).orElse("no api hints");
    }

    public record MetadataExecutionPlan(
        MetadataSearchResult originalCandidate,
        MetadataSearchResult executableMatch,
        List<FieldHint> fieldHints,
        List<RelationHint> relationHints,
        List<ApiHint> apiHints,
        List<String> reasoningSteps,
        boolean executable
    ) {
        static MetadataExecutionPlan of(MetadataSearchResult original, String entityName, String schemaCode, List<FieldHint> fields, List<RelationHint> relations, List<ApiHint> apiHints, List<String> reasoning) {
            MetadataSearchResult executable = new MetadataSearchResult("entity", original.objectId(), entityName, schemaCode, original.graphPath(), original.riskLevel(), original.reason());
            return new MetadataExecutionPlan(original, executable, fields, relations, apiHints, reasoning, hasSchema(schemaCode));
        }

        static MetadataExecutionPlan inherited(MetadataSearchResult candidate, List<ApiHint> apiHints) {
            return new MetadataExecutionPlan(candidate, candidate, List.of(), List.of(), apiHints, List.of("inherited previous runtime object schemaCode=" + candidate.code()), hasSchema(candidate.code()));
        }

        static MetadataExecutionPlan empty(MetadataSearchResult candidate, String reason) {
            return new MetadataExecutionPlan(candidate, candidate, List.of(), List.of(), List.of(), List.of(reason), false);
        }

        MetadataExecutionPlan withFields(List<FieldHint> fields, List<String> reasoning) {
            return new MetadataExecutionPlan(originalCandidate, executableMatch, fields.stream().limit(8).toList(), relationHints, apiHints, reasoning, executable);
        }

        MetadataExecutionPlan withRelations(List<RelationHint> relations, List<String> reasoning) {
            return new MetadataExecutionPlan(originalCandidate, executableMatch, fieldHints, relations.stream().limit(6).toList(), apiHints, reasoning, executable);
        }

        String summary() {
            String fields = fieldHints.isEmpty() ? "no field hints" : fieldHints.stream().map(FieldHint::displayName).limit(5).reduce((left, right) -> left + ", " + right).orElse("no field hints");
            String relations = relationHints.isEmpty() ? "no relation hints" : relationHints.stream().map(RelationHint::summary).limit(3).reduce((left, right) -> left + "; " + right).orElse("no relation hints");
            String apis = apiHints.isEmpty() ? "no api hints" : apiHints.stream().map(ApiHint::summary).limit(3).reduce((left, right) -> left + "; " + right).orElse("no api hints");
            return "executionObject=" + executableMatch.name() + ", schemaCode=" + executableMatch.code() + "; fieldHints=" + fields + "; relationHints=" + relations + "; apiHints=" + apis;
        }

        private static boolean hasSchema(String schemaCode) {
            return schemaCode != null && !schemaCode.isBlank();
        }
    }

    public record FieldHint(String name, String code, String dataType, boolean reference, String description) {
        String displayName() { return name + "(" + code + ")"; }
    }

    public record ApiHint(String apiCode, String name, String method, String path, String operationType, String riskLevel, boolean requiresConfirmation, String dataScope) {
        String summary() { return name + "(" + operationType + ", " + method + " " + path + ")"; }
    }

    public record RelationHint(String sourceEntityName, String sourceSchemaCode, String targetEntityName, String targetSchemaCode, String relationName, String relationType, String sourceDataItemId) {
        String summary() { return sourceEntityName + " -> " + targetEntityName + "(" + relationName + ")"; }
    }
}
