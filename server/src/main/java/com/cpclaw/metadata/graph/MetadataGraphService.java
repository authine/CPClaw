package com.cpclaw.metadata.graph;

import com.cpclaw.metadata.entity.CloudPivotApiEndpoint;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.graph.dto.GraphifyExportResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphNeighborhoodResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphOverviewResponse;
import com.cpclaw.metadata.repository.CloudPivotApiEndpointRepository;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataGraphService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final MetadataGraphProjector projector;
    private final MetadataGraphProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CloudPivotAppRepository appRepository;
    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotDataItemRepository dataItemRepository;
    private final CloudPivotEntityRelationRepository relationRepository;
    private final CloudPivotApiEndpointRepository apiEndpointRepository;
    private volatile GraphData cachedGraph;

    public MetadataGraphService(
        MetadataGraphProjector projector,
        MetadataGraphProperties properties,
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        CloudPivotAppRepository appRepository,
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotApiEndpointRepository apiEndpointRepository
    ) {
        this.projector = projector;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.appRepository = appRepository;
        this.entityRepository = entityRepository;
        this.dataItemRepository = dataItemRepository;
        this.relationRepository = relationRepository;
        this.apiEndpointRepository = apiEndpointRepository;
    }

    public MetadataGraphProjector.ProjectionResult rebuild(
        String snapshotId,
        String sourceSyncId,
        Instant startedAt,
        List<CloudPivotApp> apps,
        List<CloudPivotEntity> entities,
        List<CloudPivotDataItem> dataItems,
        List<CloudPivotEntityRelation> relations,
        List<CloudPivotApiEndpoint> apiEndpoints
    ) {
        if (!properties.isEnabled()) {
            return new MetadataGraphProjector.ProjectionResult(snapshotId, sourceSyncId, 0, 0, 0, 0, 0, Instant.now());
        }
        MetadataGraphProjector.ProjectionResult result = projector.project(
            snapshotId, sourceSyncId, startedAt, apps, entities, dataItems, relations, apiEndpoints
        );
        invalidate();
        if (properties.isWriteExport()) {
            String exportPath = writeExport(graphifyExport());
            projector.updateExportPath(snapshotId, exportPath);
            invalidate();
        }
        return result;
    }

    @Transactional
    public MetadataGraphProjector.ProjectionResult rebuildCurrentMetadata() {
        List<CloudPivotApp> apps = appRepository.findAllByOrderBySyncedAtDesc();
        if (apps.isEmpty()) {
            throw new IllegalStateException("云枢元数据尚未初始化，无法构建知识图谱");
        }
        String sourceSyncId = apps.stream()
            .map(CloudPivotApp::getSyncBatchId)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("metadata-current");
        String snapshotId = UUID.randomUUID().toString();
        return rebuild(
            snapshotId,
            sourceSyncId,
            Instant.now(),
            apps,
            entityRepository.findAll(),
            dataItemRepository.findAll(),
            relationRepository.findAll(),
            apiEndpointRepository.findAll()
        );
    }

    public MetadataGraphOverviewResponse overview() {
        GraphData graph = activeGraph();
        if (graph == null) {
            return new MetadataGraphOverviewResponse(
                MetadataGraphProjector.PROVIDER, "UNINITIALIZED", null, null,
                0, 0, 0, 0, 0, 0, Map.of(), Map.of(), List.of(), null
            );
        }
        Map<String, Integer> nodesByType = new LinkedHashMap<>();
        Map<String, Integer> edgesByType = new LinkedHashMap<>();
        for (GraphNode node : graph.nodes()) {
            nodesByType.merge(node.nodeType(), 1, Integer::sum);
        }
        for (GraphEdge edge : graph.edges()) {
            edgesByType.merge(edge.edgeType(), 1, Integer::sum);
        }

        Map<String, List<GraphNode>> nodesByApp = new LinkedHashMap<>();
        Map<String, Set<String>> edgeIdsByApp = new LinkedHashMap<>();
        Map<String, GraphNode> nodesByKey = graph.nodesByKey();
        for (GraphNode node : graph.nodes()) {
            if (hasText(node.appId())) {
                nodesByApp.computeIfAbsent(node.appId(), ignored -> new ArrayList<>()).add(node);
            }
        }
        for (GraphEdge edge : graph.edges()) {
            GraphNode source = nodesByKey.get(edge.source());
            GraphNode target = nodesByKey.get(edge.target());
            if (source != null && hasText(source.appId())) {
                edgeIdsByApp.computeIfAbsent(source.appId(), ignored -> new LinkedHashSet<>()).add(edge.id());
            }
            if (target != null && hasText(target.appId())) {
                edgeIdsByApp.computeIfAbsent(target.appId(), ignored -> new LinkedHashSet<>()).add(edge.id());
            }
        }
        List<MetadataGraphOverviewResponse.ApplicationCoverage> applications = graph.nodes().stream()
            .filter(node -> "application".equals(node.nodeType()))
            .sorted(Comparator.comparing(GraphNode::name, Comparator.nullsLast(String::compareTo)))
            .map(app -> {
                List<GraphNode> appNodes = nodesByApp.getOrDefault(app.objectId(), List.of());
                int entityCount = (int) appNodes.stream().filter(node -> "entity".equals(node.nodeType())).count();
                int dataItemCount = (int) appNodes.stream().filter(node -> "data_item".equals(node.nodeType())).count();
                return new MetadataGraphOverviewResponse.ApplicationCoverage(
                    app.objectId(), app.appCode(), app.name(), entityCount, dataItemCount,
                    edgeIdsByApp.getOrDefault(app.objectId(), Set.of()).size(), 1.0, "READY"
                );
            })
            .toList();
        return new MetadataGraphOverviewResponse(
            graph.snapshot().provider(), graph.snapshot().status(), graph.snapshot().sourceSyncId(),
            instantText(graph.snapshot().completedAt()), graph.snapshot().applicationCount(), applications.size(),
            graph.snapshot().coverageRate(), graph.nodes().size(), graph.edges().size(),
            graph.snapshot().unresolvedEdgeCount(), nodesByType, edgesByType, applications,
            graph.snapshot().exportPath()
        );
    }

    public String exportPath(String snapshotId) {
        List<String> paths = jdbcTemplate.query(
            "SELECT export_path FROM metadata_graph_snapshots WHERE id = ?",
            (rs, rowNum) -> rs.getString("export_path"),
            snapshotId
        );
        return paths.isEmpty() ? null : paths.getFirst();
    }

    public MetadataGraphNeighborhoodResponse neighborhood(
        String nodeId,
        String objectType,
        String objectId,
        int requestedDepth,
        int requestedLimit
    ) {
        GraphData graph = requireActiveGraph();
        GraphNode center = hasText(nodeId)
            ? graph.nodesByKey().get(nodeId)
            : graph.nodes().stream()
                .filter(node -> safe(objectType).equalsIgnoreCase(node.objectType()) && safe(objectId).equals(node.objectId()))
                .findFirst()
                .orElse(null);
        if (center == null) {
            throw new IllegalArgumentException("未找到对应的元数据图谱节点");
        }
        int depth = Math.max(1, Math.min(requestedDepth, properties.getMaxDepth()));
        int limit = Math.max(10, Math.min(requestedLimit, properties.getMaxNodes()));
        Set<String> visited = new LinkedHashSet<>();
        visited.add(center.id());
        Set<String> discovered = new HashSet<>(visited);
        Set<String> frontier = Set.of(center.id());
        boolean truncated = false;
        int totalNeighborCount = 0;
        for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
            Set<String> next = new LinkedHashSet<>();
            for (String key : frontier) {
                GraphNode current = graph.nodesByKey().get(key);
                if (level > 0 && current != null && isTraversalTerminal(current)) {
                    continue;
                }
                for (GraphEdge edge : graph.adjacency().getOrDefault(key, List.of())) {
                    String neighbor = edge.source().equals(key) ? edge.target() : edge.source();
                    if (!discovered.add(neighbor)) {
                        continue;
                    }
                    totalNeighborCount++;
                    if (visited.size() >= limit) {
                        truncated = true;
                        continue;
                    }
                    visited.add(neighbor);
                    next.add(neighbor);
                }
            }
            frontier = next;
        }
        List<GraphNode> selectedNodes = visited.stream().map(graph.nodesByKey()::get).filter(java.util.Objects::nonNull).toList();
        List<GraphEdge> selectedEdges = graph.edges().stream()
            .filter(edge -> visited.contains(edge.source()) && visited.contains(edge.target()))
            .toList();
        return new MetadataGraphNeighborhoodResponse(
            toNode(center), selectedNodes.stream().map(this::toNode).toList(), selectedEdges.stream().map(this::toEdge).toList(),
            depth, truncated, totalNeighborCount
        );
    }

    private boolean isTraversalTerminal(GraphNode node) {
        return "application".equals(node.nodeType()) || "api_endpoint".equals(node.nodeType());
    }

    public GraphifyExportResponse graphifyExport() {
        GraphData graph = requireActiveGraph();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", graph.snapshot().provider());
        metadata.put("schema_version", "graphify-v8-node-link");
        metadata.put("snapshot_id", graph.snapshot().id());
        metadata.put("source_sync_id", graph.snapshot().sourceSyncId());
        metadata.put("generated_at", instantText(graph.snapshot().completedAt()));
        metadata.put("application_count", graph.snapshot().applicationCount());
        metadata.put("coverage_rate", graph.snapshot().coverageRate());
        metadata.put("unresolved_edge_count", graph.snapshot().unresolvedEdgeCount());
        List<GraphifyExportResponse.Node> nodes = graph.nodes().stream()
            .map(node -> new GraphifyExportResponse.Node(
                node.id(), node.name(), "cloudpivot_metadata", node.sourceUri(), null, node.community(),
                node.nodeType(), node.objectType(), node.objectId(), node.appCode(), node.code(), node.confidence(), node.attributes()
            ))
            .toList();
        List<GraphifyExportResponse.Link> links = graph.edges().stream()
            .map(edge -> new GraphifyExportResponse.Link(
                edge.edgeType().toLowerCase(), edge.confidence(), edge.sourceUri(), null, edge.weight(),
                edge.source(), edge.target(), edge.source(), edge.target(), edge.label(), edge.attributes()
            ))
            .toList();
        return new GraphifyExportResponse(true, false, metadata, nodes, links);
    }

    public synchronized void invalidate() {
        cachedGraph = null;
    }

    private synchronized GraphData activeGraph() {
        if (cachedGraph != null) {
            return cachedGraph;
        }
        List<SnapshotData> snapshots = jdbcTemplate.query("""
            SELECT id, source_sync_id, provider, status, application_count, unresolved_edge_count,
                   coverage_rate, export_path, completed_at
              FROM metadata_graph_snapshots
             WHERE status = 'ACTIVE'
             ORDER BY completed_at DESC
             LIMIT 1
            """, (rs, rowNum) -> new SnapshotData(
                rs.getString("id"), rs.getString("source_sync_id"), rs.getString("provider"), rs.getString("status"),
                rs.getInt("application_count"), rs.getInt("unresolved_edge_count"), rs.getDouble("coverage_rate"),
                rs.getString("export_path"), rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()
            ));
        if (snapshots.isEmpty()) {
            return null;
        }
        SnapshotData snapshot = snapshots.getFirst();
        List<GraphNode> nodes = jdbcTemplate.query("""
            SELECT stable_key, node_type, object_type, object_id, app_id, app_code, entity_id, code,
                   name, confidence, community, source_uri, attributes_json
              FROM metadata_graph_nodes
             WHERE snapshot_id = ?
             ORDER BY stable_key
            """, (rs, rowNum) -> new GraphNode(
                rs.getString("stable_key"), rs.getString("node_type"), rs.getString("object_type"), rs.getString("object_id"),
                rs.getString("app_id"), rs.getString("app_code"), rs.getString("entity_id"), rs.getString("code"),
                rs.getString("name"), rs.getString("confidence"), rs.getInt("community"), rs.getString("source_uri"),
                parseAttributes(rs.getString("attributes_json"))
            ), snapshot.id());
        List<GraphEdge> edges = jdbcTemplate.query("""
            SELECT stable_key, edge_type, label, source_node_key, target_node_key, confidence, weight,
                   source_data_item_id, source_uri, attributes_json
              FROM metadata_graph_edges
             WHERE snapshot_id = ?
             ORDER BY stable_key
            """, (rs, rowNum) -> new GraphEdge(
                rs.getString("stable_key"), rs.getString("edge_type"), rs.getString("label"),
                rs.getString("source_node_key"), rs.getString("target_node_key"), rs.getString("confidence"),
                rs.getDouble("weight"), rs.getString("source_data_item_id"), rs.getString("source_uri"),
                parseAttributes(rs.getString("attributes_json"))
            ), snapshot.id());
        Map<String, GraphNode> nodesByKey = new LinkedHashMap<>();
        Map<String, List<GraphEdge>> adjacency = new HashMap<>();
        for (GraphNode node : nodes) {
            nodesByKey.put(node.id(), node);
        }
        for (GraphEdge edge : edges) {
            adjacency.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }
        cachedGraph = new GraphData(snapshot, nodes, edges, nodesByKey, adjacency);
        return cachedGraph;
    }

    private GraphData requireActiveGraph() {
        GraphData graph = activeGraph();
        if (graph == null) {
            throw new IllegalStateException("元数据知识图谱尚未构建，请先初始化云枢元数据");
        }
        return graph;
    }

    private String writeExport(GraphifyExportResponse export) {
        Path directory = Path.of(properties.getOutputDirectory()).toAbsolutePath().normalize();
        Path target = directory.resolve("graph.json");
        Path temporary = directory.resolve("graph.json.tmp");
        try {
            Files.createDirectories(directory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), export);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("无法写入 Graphify 图谱文件：" + target, exception);
        }
    }

    private MetadataGraphNeighborhoodResponse.Node toNode(GraphNode node) {
        return new MetadataGraphNeighborhoodResponse.Node(
            node.id(), node.nodeType(), node.objectType(), node.objectId(), node.appId(), node.appCode(),
            node.entityId(), node.name(), node.code(), node.confidence()
        );
    }

    private MetadataGraphNeighborhoodResponse.Edge toEdge(GraphEdge edge) {
        return new MetadataGraphNeighborhoodResponse.Edge(
            edge.id(), edge.edgeType(), edge.label(), edge.source(), edge.target(), edge.confidence(),
            edge.weight(), edge.sourceDataItemId()
        );
    }

    private Map<String, Object> parseAttributes(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (IOException exception) {
            return Map.of("raw", value);
        }
    }

    private String instantText(Instant value) {
        return value == null ? null : value.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SnapshotData(
        String id, String sourceSyncId, String provider, String status, int applicationCount,
        int unresolvedEdgeCount, double coverageRate, String exportPath, Instant completedAt
    ) {
    }

    private record GraphNode(
        String id, String nodeType, String objectType, String objectId, String appId, String appCode,
        String entityId, String code, String name, String confidence, int community, String sourceUri,
        Map<String, Object> attributes
    ) {
    }

    private record GraphEdge(
        String id, String edgeType, String label, String source, String target, String confidence,
        double weight, String sourceDataItemId, String sourceUri, Map<String, Object> attributes
    ) {
    }

    private record GraphData(
        SnapshotData snapshot, List<GraphNode> nodes, List<GraphEdge> edges,
        Map<String, GraphNode> nodesByKey, Map<String, List<GraphEdge>> adjacency
    ) {
    }
}
