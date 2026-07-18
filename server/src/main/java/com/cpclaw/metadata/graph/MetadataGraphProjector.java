package com.cpclaw.metadata.graph;

import com.cpclaw.metadata.entity.CloudPivotApiEndpoint;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MetadataGraphProjector {
    static final String PROVIDER = "graphify-v8-compatible";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MetadataGraphProperties properties;

    public MetadataGraphProjector(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, MetadataGraphProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ProjectionResult project(
        String snapshotId,
        String sourceSyncId,
        Instant startedAt,
        List<CloudPivotApp> sourceApps,
        List<CloudPivotEntity> sourceEntities,
        List<CloudPivotDataItem> sourceDataItems,
        List<CloudPivotEntityRelation> sourceRelations,
        List<CloudPivotApiEndpoint> sourceApiEndpoints
    ) {
        List<CloudPivotApp> apps = sourceApps.stream().sorted(Comparator.comparing(CloudPivotApp::getAppCode, Comparator.nullsLast(String::compareTo))).toList();
        List<CloudPivotEntity> entities = sourceEntities.stream().sorted(Comparator.comparing(CloudPivotEntity::getEntityCode, Comparator.nullsLast(String::compareTo))).toList();
        List<CloudPivotDataItem> dataItems = sourceDataItems.stream().sorted(Comparator.comparing(CloudPivotDataItem::getDataItemCode, Comparator.nullsLast(String::compareTo))).toList();
        List<CloudPivotEntityRelation> relations = sourceRelations.stream().sorted(Comparator.comparing(CloudPivotEntityRelation::getId)).toList();
        List<CloudPivotApiEndpoint> apiEndpoints = sourceApiEndpoints.stream().sorted(Comparator.comparing(CloudPivotApiEndpoint::getApiCode, Comparator.nullsLast(String::compareTo))).toList();

        Map<String, CloudPivotApp> appsById = new LinkedHashMap<>();
        Map<String, Integer> appCommunities = new LinkedHashMap<>();
        Map<String, String> appNodeKeys = new LinkedHashMap<>();
        int community = 1;
        for (CloudPivotApp app : apps) {
            appsById.put(app.getId(), app);
            appCommunities.put(app.getId(), community++);
            appNodeKeys.put(app.getId(), stableKey("app", app.getAppCode()));
        }

        Map<String, CloudPivotEntity> entitiesById = new LinkedHashMap<>();
        Map<String, String> entityNodeKeys = new LinkedHashMap<>();
        for (CloudPivotEntity entity : entities) {
            CloudPivotApp app = appsById.get(entity.getAppId());
            if (app == null) {
                continue;
            }
            entitiesById.put(entity.getId(), entity);
            entityNodeKeys.put(entity.getId(), stableKey("entity", app.getAppCode(), entity.getEntityCode()));
        }

        Map<String, String> dataItemNodeKeys = new LinkedHashMap<>();
        List<NodeData> nodes = new ArrayList<>(apps.size() + entities.size() + dataItems.size() + apiEndpoints.size());
        List<EdgeData> edges = new ArrayList<>();
        int unresolvedEdges = 0;

        for (CloudPivotApp app : apps) {
            String key = appNodeKeys.get(app.getId());
            nodes.add(node(snapshotId, key, "application", "app", app.getId(), app.getId(), app.getAppCode(), null,
                app.getAppCode(), app.getName(), appCommunities.get(app.getId()), "cloudpivot://" + segment(app.getAppCode()),
                Map.of("description", safe(app.getDescription()), "syncBatchId", safe(app.getSyncBatchId())) , startedAt));
        }

        for (CloudPivotEntity entity : entities) {
            CloudPivotApp app = appsById.get(entity.getAppId());
            String entityKey = entityNodeKeys.get(entity.getId());
            if (app == null || entityKey == null) {
                unresolvedEdges++;
                continue;
            }
            String sourceUri = "cloudpivot://" + segment(app.getAppCode()) + "/entities/" + segment(entity.getEntityCode());
            nodes.add(node(snapshotId, entityKey, "entity", "entity", entity.getId(), app.getId(), app.getAppCode(), entity.getId(),
                entity.getEntityCode(), entity.getName(), appCommunities.get(app.getId()), sourceUri,
                Map.of("entityType", safe(entity.getEntityType())), startedAt));
            edges.add(edge(snapshotId, "APP_CONTAINS_ENTITY", "包含实体", appNodeKeys.get(app.getId()), entityKey,
                "EXTRACTED", 1.0, null, sourceUri, Map.of(), startedAt));
        }

        for (CloudPivotDataItem dataItem : dataItems) {
            CloudPivotEntity entity = entitiesById.get(dataItem.getEntityId());
            CloudPivotApp app = entity == null ? null : appsById.get(entity.getAppId());
            String entityKey = entity == null ? null : entityNodeKeys.get(entity.getId());
            if (app == null || entity == null || entityKey == null) {
                unresolvedEdges++;
                continue;
            }
            String fieldKey = stableKey("data_item", app.getAppCode(), entity.getEntityCode(), dataItem.getDataItemCode());
            dataItemNodeKeys.put(dataItem.getId(), fieldKey);
            String sourceUri = "cloudpivot://" + segment(app.getAppCode()) + "/entities/" + segment(entity.getEntityCode()) + "/fields/" + segment(dataItem.getDataItemCode());
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("dataType", safe(dataItem.getDataType()));
            attributes.put("required", dataItem.isRequired());
            attributes.put("reference", dataItem.isReference());
            attributes.put("description", safe(dataItem.getDescription()));
            nodes.add(node(snapshotId, fieldKey, "data_item", "data_item", dataItem.getId(), app.getId(), app.getAppCode(), entity.getId(),
                dataItem.getDataItemCode(), dataItem.getName(), appCommunities.get(app.getId()), sourceUri, attributes, startedAt));
            edges.add(edge(snapshotId, "ENTITY_HAS_DATA_ITEM", "包含字段", entityKey, fieldKey,
                "EXTRACTED", 1.0, dataItem.getId(), sourceUri, Map.of(), startedAt));
            if (dataItem.isReference()) {
                String targetKey = entityNodeKeys.get(dataItem.getReferenceEntityId());
                if (targetKey == null) {
                    unresolvedEdges++;
                } else {
                    edges.add(edge(snapshotId, "DATA_ITEM_REFERENCES_ENTITY", dataItem.getName(), fieldKey, targetKey,
                        "EXTRACTED", 1.0, dataItem.getId(), sourceUri,
                        Map.of("evidence", "cloudpivot-reference-data-item"), startedAt));
                }
            }
        }

        for (CloudPivotEntityRelation relation : relations) {
            String sourceKey = entityNodeKeys.get(relation.getSourceEntityId());
            String targetKey = entityNodeKeys.get(relation.getTargetEntityId());
            if (sourceKey == null || targetKey == null) {
                unresolvedEdges++;
                continue;
            }
            String fieldKey = dataItemNodeKeys.get(relation.getSourceDataItemId());
            String sourceUri = fieldKey == null ? "cloudpivot://relations" : "cloudpivot://relations/" + segment(fieldKey);
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("relationType", safe(relation.getRelationType()));
            attributes.put("sourceFieldNode", safe(fieldKey));
            attributes.put("evidence", "cloudpivot-explicit-relation");
            edges.add(edge(snapshotId, "ENTITY_RELATES_TO_ENTITY", safe(relation.getRelationName()), sourceKey, targetKey,
                "EXTRACTED", 1.0, relation.getSourceDataItemId(), sourceUri, attributes, startedAt));
        }

        if (properties.isIncludeApiEndpoints()) {
            for (CloudPivotApiEndpoint endpoint : apiEndpoints) {
                String apiKey = stableKey("api_endpoint", endpoint.getApiCode());
                String sourceUri = "cloudpivot://api/" + segment(endpoint.getApiCode());
                nodes.add(node(snapshotId, apiKey, "api_endpoint", "api_endpoint", endpoint.getId(), null, null, null,
                    endpoint.getApiCode(), endpoint.getName(), 0, sourceUri,
                    Map.of(
                        "method", safe(endpoint.getMethod()),
                        "path", safe(endpoint.getPath()),
                        "operationType", safe(endpoint.getOperationType()),
                        "riskLevel", safe(endpoint.getRiskLevel()),
                        "requiresConfirmation", endpoint.isRequiresConfirmation(),
                        "applicableObjectType", safe(endpoint.getApplicableObjectType())
                    ), startedAt));
                for (CloudPivotEntity entity : entities) {
                    String targetKey = entityNodeKeys.get(entity.getId());
                    if (targetKey == null || !appliesToEntity(endpoint)) {
                        continue;
                    }
                    edges.add(edge(snapshotId, "API_OPERATES_ON_ENTITY", endpoint.getName(), apiKey, targetKey,
                        "INFERRED", 0.7, null, sourceUri,
                        Map.of("evidence", "api-applicable-object-type"), startedAt));
                }
            }
        }

        prepareSnapshot(snapshotId, sourceSyncId, startedAt);
        batchInsertNodes(nodes);
        batchInsertEdges(edges);
        double coverageRate = apps.isEmpty() ? 0.0 : 1.0;
        Instant completedAt = Instant.now();
        jdbcTemplate.update("""
            UPDATE metadata_graph_snapshots
               SET status = 'ACTIVE', application_count = ?, node_count = ?, edge_count = ?,
                   unresolved_edge_count = ?, coverage_rate = ?, completed_at = ?
             WHERE id = ?
            """, apps.size(), nodes.size(), edges.size(), unresolvedEdges, coverageRate, completedAt, snapshotId);
        jdbcTemplate.update("UPDATE metadata_graph_snapshots SET status = 'STALE' WHERE status = 'ACTIVE' AND id <> ?", snapshotId);
        pruneSnapshots();
        return new ProjectionResult(snapshotId, sourceSyncId, apps.size(), nodes.size(), edges.size(), unresolvedEdges, coverageRate, completedAt);
    }

    public void updateExportPath(String snapshotId, String exportPath) {
        jdbcTemplate.update("UPDATE metadata_graph_snapshots SET export_path = ? WHERE id = ?", exportPath, snapshotId);
    }

    private void prepareSnapshot(String snapshotId, String sourceSyncId, Instant startedAt) {
        jdbcTemplate.update("DELETE FROM metadata_graph_edges WHERE snapshot_id = ?", snapshotId);
        jdbcTemplate.update("DELETE FROM metadata_graph_nodes WHERE snapshot_id = ?", snapshotId);
        jdbcTemplate.update("DELETE FROM metadata_graph_snapshots WHERE id = ?", snapshotId);
        jdbcTemplate.update("""
            INSERT INTO metadata_graph_snapshots
                (id, source_sync_id, provider, status, application_count, node_count, edge_count,
                 unresolved_edge_count, coverage_rate, started_at)
            VALUES (?, ?, ?, 'BUILDING', 0, 0, 0, 0, 0, ?)
            """, snapshotId, sourceSyncId, PROVIDER, startedAt);
    }

    private void batchInsertNodes(List<NodeData> nodes) {
        String sql = """
            INSERT INTO metadata_graph_nodes
                (id, snapshot_id, stable_key, node_type, object_type, object_id, app_id, app_code,
                 entity_id, code, name, confidence, community, source_uri, attributes_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        for (int offset = 0; offset < nodes.size(); offset += properties.getBatchSize()) {
            List<Object[]> batch = nodes.subList(offset, Math.min(nodes.size(), offset + properties.getBatchSize())).stream()
                .map(NodeData::parameters)
                .toList();
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    private void batchInsertEdges(List<EdgeData> edges) {
        String sql = """
            INSERT INTO metadata_graph_edges
                (id, snapshot_id, stable_key, edge_type, label, source_node_key, target_node_key,
                 confidence, weight, source_data_item_id, source_uri, attributes_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        for (int offset = 0; offset < edges.size(); offset += properties.getBatchSize()) {
            List<Object[]> batch = edges.subList(offset, Math.min(edges.size(), offset + properties.getBatchSize())).stream()
                .map(EdgeData::parameters)
                .toList();
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    private void pruneSnapshots() {
        List<String> ids = jdbcTemplate.queryForList(
            "SELECT id FROM metadata_graph_snapshots ORDER BY completed_at DESC, started_at DESC",
            String.class
        );
        if (ids.size() <= properties.getSnapshotRetention()) {
            return;
        }
        for (String id : ids.subList(properties.getSnapshotRetention(), ids.size())) {
            jdbcTemplate.update("DELETE FROM metadata_graph_edges WHERE snapshot_id = ?", id);
            jdbcTemplate.update("DELETE FROM metadata_graph_nodes WHERE snapshot_id = ?", id);
            jdbcTemplate.update("DELETE FROM metadata_graph_snapshots WHERE id = ?", id);
        }
    }

    private NodeData node(
        String snapshotId, String stableKey, String nodeType, String objectType, String objectId,
        String appId, String appCode, String entityId, String code, String name, int community,
        String sourceUri, Map<String, Object> attributes, Instant createdAt
    ) {
        return new NodeData(
            deterministicId(snapshotId + "|node|" + stableKey), snapshotId, stableKey, nodeType, objectType,
            objectId, appId, appCode, entityId, code, hasText(name) ? name : code, "EXTRACTED", community,
            sourceUri, json(attributes), createdAt
        );
    }

    private EdgeData edge(
        String snapshotId, String edgeType, String label, String source, String target, String confidence,
        double weight, String sourceDataItemId, String sourceUri, Map<String, Object> attributes, Instant createdAt
    ) {
        String stableKey = stableKey("edge", edgeType, source, target, safe(sourceDataItemId));
        return new EdgeData(
            deterministicId(snapshotId + "|edge|" + stableKey), snapshotId, stableKey, edgeType,
            hasText(label) ? label : edgeType, source, target, confidence, weight, sourceDataItemId,
            sourceUri, json(attributes), createdAt
        );
    }

    private boolean appliesToEntity(CloudPivotApiEndpoint endpoint) {
        String type = safe(endpoint.getApplicableObjectType()).toLowerCase();
        return type.isBlank() || type.contains("entity") || type.contains("business") || type.contains("object") || type.contains("data");
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化元数据图谱属性", exception);
        }
    }

    static String stableKey(String type, String... parts) {
        StringBuilder result = new StringBuilder(type);
        for (String part : parts) {
            result.append(':').append(segment(part));
        }
        return result.toString();
    }

    private static String segment(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ProjectionResult(
        String snapshotId,
        String sourceSyncId,
        int applicationCount,
        int nodeCount,
        int edgeCount,
        int unresolvedEdgeCount,
        double coverageRate,
        Instant completedAt
    ) {
    }

    private record NodeData(
        String id, String snapshotId, String stableKey, String nodeType, String objectType, String objectId,
        String appId, String appCode, String entityId, String code, String name, String confidence,
        int community, String sourceUri, String attributesJson, Instant createdAt
    ) {
        Object[] parameters() {
            return new Object[]{id, snapshotId, stableKey, nodeType, objectType, objectId, appId, appCode,
                entityId, code, name, confidence, community, sourceUri, attributesJson, createdAt};
        }
    }

    private record EdgeData(
        String id, String snapshotId, String stableKey, String edgeType, String label, String sourceNodeKey,
        String targetNodeKey, String confidence, double weight, String sourceDataItemId, String sourceUri,
        String attributesJson, Instant createdAt
    ) {
        Object[] parameters() {
            return new Object[]{id, snapshotId, stableKey, edgeType, label, sourceNodeKey, targetNodeKey,
                confidence, weight, sourceDataItemId, sourceUri, attributesJson, createdAt};
        }
    }
}
