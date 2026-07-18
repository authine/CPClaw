package com.cpclaw.cloudpivot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MvpCloudPivotConnector implements CloudPivotConnector {

    private static final Logger log = LoggerFactory.getLogger(MvpCloudPivotConnector.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean allowFallbackMetadata;
    private final String configuredCorpId;
    private final CloudPivotRuntimeProperties runtimeProperties;

    public MvpCloudPivotConnector(
        ObjectMapper objectMapper,
        @Value("${cpclaw.cloudpivot.allow-metadata-fallback:false}") boolean allowFallbackMetadata,
        @Value("${cpclaw.cloudpivot.corp-id:}") String configuredCorpId,
        CloudPivotRuntimeProperties runtimeProperties
    ) {
        this.objectMapper = objectMapper;
        this.allowFallbackMetadata = allowFallbackMetadata;
        this.configuredCorpId = configuredCorpId;
        this.runtimeProperties = runtimeProperties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(requestTimeout())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    @Override
    public boolean testConnection(String baseUrl, String username, String password) {
        if (!hasText(baseUrl) || !hasText(username) || !hasText(password)) {
            return false;
        }
        if (allowFallbackMetadata && isLocalTestUrl(baseUrl)) {
            return true;
        }
        return authenticate(normalizeBaseUrl(baseUrl), username, password).isPresent();
    }

    @Override
    public CloudPivotMetadataSnapshot fetchMetadata(String baseUrl, String username, String password) {
        if (!hasText(baseUrl) || !hasText(username) || !hasText(password)) {
            throw new IllegalArgumentException("管理员云枢连接配置不完整，无法初始化元数据");
        }
        if (allowFallbackMetadata && isLocalTestUrl(baseUrl)) {
            return fallbackMetadata();
        }

        String host = normalizeBaseUrl(baseUrl);
        AuthSession session = authenticate(host, username, password)
            .orElseThrow(() -> new IllegalStateException("云枢管理员账号验证失败，无法初始化元数据"));
        CloudPivotMetadataSnapshot snapshot = fetchRemoteMetadata(host, session);
        if (snapshot.apps().isEmpty()) {
            throw new IllegalStateException("云枢元数据接口未返回可用应用");
        }
        return snapshot;
    }

    @Override
    public CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize) {
        return queryRecords(baseUrl, username, password, schemaCode, pageSize, false);
    }

    @Override
    public CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize, boolean enrichAllDetails) {
        return queryRecords(baseUrl, username, password, schemaCode, pageSize, enrichAllDetails, connectorProperties().getMaxAnalysisRecords());
    }

    @Override
    public CloudPivotRuntimeQueryResult queryRecords(String baseUrl, String username, String password, String schemaCode, int pageSize, boolean enrichAllDetails, int maxRecords) {
        if (!hasText(baseUrl) || !hasText(username) || !hasText(password)) {
            throw new IllegalArgumentException("请先在设置中绑定云枢访问地址、账号和密码");
        }
        if (!hasText(schemaCode)) {
            throw new IllegalArgumentException("未匹配到可查询的云枢模型编码");
        }
        if (allowFallbackMetadata && isLocalTestUrl(baseUrl)) {
            return fallbackRuntimeQueryResult(schemaCode, Math.min(pageSize, Math.max(1, maxRecords)));
        }
        String host = normalizeBaseUrl(baseUrl);
        AuthSession session = authenticate(host, username, password)
            .orElseThrow(() -> new IllegalStateException("云枢普通用户账号验证失败，无法查询业务数据"));
        return queryRemoteRecords(host, session, schemaCode, Math.max(1, Math.min(pageSize, connectorProperties().getMaxPageSize())), enrichAllDetails, Math.max(1, maxRecords));
    }

    private CloudPivotMetadataSnapshot fetchRemoteMetadata(String host, AuthSession session) {
        List<JsonNode> appNodes = firstSuccessfulList(host, session, List.of(
            new Endpoint("GET", "/api/runtime/app/list_apps", Map.of("isMobile", "false")),
            new Endpoint("GET", "/api/runtime/app/list_apps_group", Map.of("isMobile", "false")),
            new Endpoint("GET", "/api/app/apppackage/list_all", Map.of()),
            new Endpoint("GET", "/api/app/apppackage/list", Map.of()),
            new Endpoint("GET", "/api/app/apppackage/trees", Map.of())
        ));

        Map<String, CloudPivotMetadataSnapshot.AppMetadata> apps = new LinkedHashMap<>();
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities = new LinkedHashMap<>();
        List<CloudPivotMetadataSnapshot.DataItemMetadata> dataItems = new ArrayList<>();
        List<CloudPivotMetadataSnapshot.EntityRelationMetadata> relations = new ArrayList<>();
        for (JsonNode appNode : appNodes) {
            String appCode = firstText(appNode, "code", "appCode", "codePath", "id").orElse(null);
            if (!hasText(appCode)) {
                continue;
            }
            String appName = firstText(appNode, "name", "appName", "displayName", "name_i18n").orElse(appCode);
            apps.putIfAbsent(appCode, new CloudPivotMetadataSnapshot.AppMetadata(appCode, appName, textValue(appNode)));
            fetchEntitiesForApp(host, session, appCode, apps, entities);
        }

        if (entities.isEmpty()) {
            for (JsonNode entityNode : firstSuccessfulList(host, session, List.of(
                new Endpoint("GET", "/api/app/bizmodels/get_all", Map.of()),
                new Endpoint("GET", "/api/app/bizmodels/list", Map.of())
            ))) {
                addEntity(entityNode, null, apps, entities);
            }
        }

        fetchDataItemsForEntities(host, session, entities, dataItems, relations);
        return new CloudPivotMetadataSnapshot(new ArrayList<>(apps.values()), new ArrayList<>(entities.values()), dataItems, relations);
    }

    private void fetchEntitiesForApp(
        String host,
        AuthSession session,
        String appCode,
        Map<String, CloudPivotMetadataSnapshot.AppMetadata> apps,
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities
    ) {
        List<JsonNode> entityNodes = firstSuccessfulList(host, session, List.of(
            new Endpoint("GET", "/api/runtime/app/list_functions_by_appcode", Map.of("appCode", appCode, "isMobile", "false")),
            new Endpoint("GET", "/api/runtime/app/list_functions_by_appcode_find", Map.of("appCode", appCode, "isMobile", "false")),
            new Endpoint("GET", "/api/runtime/app/search_bizModels", Map.of("appCode", appCode, "searchKey", "")),
            new Endpoint("GET", "/api/app/bizmodels/search", Map.of("appCode", appCode)),
            new Endpoint("GET", "/api/app/bizmodels/list", Map.of("appCode", appCode)),
            new Endpoint("GET", "/api/app/bizmodels/get_all", Map.of("appCode", appCode)),
            new Endpoint("GET", "/api/app/bizmodels/get_summary", Map.of("appCode", appCode))
        ));
        for (JsonNode entityNode : entityNodes) {
            addEntity(entityNode, appCode, apps, entities);
        }
    }

    private void addEntity(
        JsonNode entityNode,
        String fallbackAppCode,
        Map<String, CloudPivotMetadataSnapshot.AppMetadata> apps,
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities
    ) {
        String entityCode = firstText(entityNode, "code", "schemaCode", "bizModelCode", "modelCode", "id").orElse(null);
        if (!hasText(entityCode)) {
            return;
        }
        String appCode = firstText(entityNode, "appCode", "appPackageCode", "packageCode", "parentCode", "parentId").orElse(fallbackAppCode);
        if (!hasText(appCode)) {
            appCode = "cloudpivot_default_app";
        }
        if (!apps.containsKey(appCode)) {
            apps.put(appCode, new CloudPivotMetadataSnapshot.AppMetadata(appCode, appCode, "cloudpivot-app"));
        }
        String entityName = firstText(entityNode, "name", "schemaName", "displayName", "name_i18n").orElse(entityCode);
        String entityType = firstText(entityNode, "type", "modelType", "entityType").orElse("data");
        entities.putIfAbsent(appCode + ":" + entityCode, new CloudPivotMetadataSnapshot.EntityMetadata(appCode, entityCode, entityName, entityType, "low"));
    }

    private void fetchDataItemsForEntities(
        String host,
        AuthSession session,
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities,
        List<CloudPivotMetadataSnapshot.DataItemMetadata> dataItems,
        List<CloudPivotMetadataSnapshot.EntityRelationMetadata> relations
    ) {
        Set<String> entityCodes = new LinkedHashSet<>();
        entities.values().forEach(entity -> entityCodes.add(entity.code()));
        Set<String> dataItemKeys = new LinkedHashSet<>();
        Set<String> relationKeys = new LinkedHashSet<>();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(connectorProperties().getDataItemProbeParallelism(), Math.max(1, entities.size())));
        try {
            List<CompletableFuture<EntityDataItemNodes>> futures = entities.values().stream()
                .map(entity -> CompletableFuture.supplyAsync(
                    () -> new EntityDataItemNodes(entity, fetchDataItemNodes(host, session, entity.appCode(), entity.code())),
                    executor
                ))
                .toList();
            for (CompletableFuture<EntityDataItemNodes> future : futures) {
                EntityDataItemNodes result = future.join();
                for (JsonNode itemNode : result.nodes()) {
                    addDataItem(result.entity(), itemNode, entityCodes, dataItems, relations, dataItemKeys, relationKeys);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private record EntityDataItemNodes(CloudPivotMetadataSnapshot.EntityMetadata entity, List<JsonNode> nodes) {
    }

    private List<JsonNode> fetchDataItemNodes(String host, AuthSession session, String appCode, String entityCode) {
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.add(new Endpoint("GET", "/api/app/bizproperty/list", Map.of("schemaCode", entityCode, "isPublish", "true")));
        endpoints.add(new Endpoint("GET", "/api/app/bizproperty/list", Map.of("schemaCode", entityCode)));
        endpoints.add(new Endpoint("GET", "/api/app/bizproperty/list_page", Map.of("schemaCode", entityCode, "page", "0", "size", "500", "isPublish", "true")));
        endpoints.add(new Endpoint("GET", "/api/app/bizproperty/list_find", Map.of("schemaCode", entityCode, "page", "0", "size", "500", "isPublish", "true", "wd", "")));
        endpoints.add(new Endpoint("GET", "/api/runtime/form/get_biz_schema", Map.of("schemaCode", entityCode)));
        for (String path : List.of(
            "/api/app/bizmodels/get",
            "/api/runtime/app/get_bizmodel"
        )) {
            endpoints.add(new Endpoint("GET", path, Map.of("schemaCode", entityCode, "appCode", appCode)));
            endpoints.add(new Endpoint("GET", path, Map.of("code", entityCode, "appCode", appCode)));
            endpoints.add(new Endpoint("GET", path, Map.of("schemaCode", entityCode)));
            endpoints.add(new Endpoint("GET", path, Map.of("code", entityCode)));
        }
        for (Endpoint endpoint : endpoints) {
            try {
                JsonNode body = requestJson(host, endpoint, session);
                List<JsonNode> items = extractDataItemNodes(body);
                if (!items.isEmpty()) {
                    log.info("CloudPivot data item endpoint {} returned {} item(s), schemaCode={}, shape={}", endpoint.path(), items.size(), entityCode, describeShape(body, 0));
                    return items;
                }
            } catch (RuntimeException exception) {
                log.debug("CloudPivot data item endpoint {} failed(schemaCode={}): {}", endpoint.path(), entityCode, exception.getMessage());
            }
        }
        return List.of();
    }

    private List<JsonNode> extractDataItemNodes(JsonNode body) {
        JsonNode source = body != null && body.has("data") ? parseJsonStringNode(body.get("data")).orElse(body.get("data")) : body;
        return extractDataItemNodes(source, 0);
    }

    private List<JsonNode> extractDataItemNodes(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 5) {
            return List.of();
        }
        if (node.isTextual()) {
            return parseJsonStringNode(node)
                .map(parsed -> extractDataItemNodes(parsed, depth + 1))
                .orElse(List.of());
        }
        if (node.isArray()) {
            List<JsonNode> result = new ArrayList<>();
            node.forEach(item -> {
                if (isDataItemNode(item)) {
                    result.add(item);
                } else {
                    result.addAll(extractDataItemNodes(item, depth + 1));
                }
            });
            return result;
        }
        if (!node.isObject()) {
            return List.of();
        }
        if (isDataItemNode(node)) {
            return List.of(node);
        }
        for (String key : List.of("dataItems", "properties", "fields", "bizProperties", "schemaProperties", "items", "columns", "content", "records", "rows", "list", "result")) {
            if (node.has(key)) {
                List<JsonNode> items = extractDataItemNodes(node.get(key), depth + 1);
                if (!items.isEmpty()) {
                    return items;
                }
            }
        }
        return List.of();
    }

    private Optional<JsonNode> parseJsonStringNode(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return Optional.empty();
        }
        String text = node.asText().trim();
        if (!(text.startsWith("{") || text.startsWith("["))) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(text));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private boolean isDataItemNode(JsonNode node) {
        return node != null && node.isObject()
            && firstText(node, "code", "propertyCode", "fieldCode", "dataItemCode", "name").isPresent()
            && (firstText(node, "type", "dataType", "propertyType", "fieldType", "controlType").isPresent()
                || firstText(node, "displayName", "propertyName", "fieldName", "label", "name_i18n").isPresent());
    }

    private void addDataItem(
        CloudPivotMetadataSnapshot.EntityMetadata entity,
        JsonNode itemNode,
        Set<String> entityCodes,
        List<CloudPivotMetadataSnapshot.DataItemMetadata> dataItems,
        List<CloudPivotMetadataSnapshot.EntityRelationMetadata> relations,
        Set<String> dataItemKeys,
        Set<String> relationKeys
    ) {
        String code = firstText(itemNode, "code", "propertyCode", "fieldCode", "dataItemCode", "name").orElse(null);
        if (!hasText(code)) {
            return;
        }
        String key = entity.appCode() + ":" + entity.code() + ":" + code;
        if (!dataItemKeys.add(key)) {
            return;
        }
        String name = firstText(itemNode, "name", "displayName", "propertyName", "fieldName", "label", "name_i18n").orElse(code);
        String dataType = firstText(itemNode, "type", "dataType", "propertyType", "fieldType", "controlType").orElse("");
        boolean required = firstBoolean(itemNode, "required", "isRequired", "mustInput", "requiredFlag").orElse(false);
        String referenceEntityCode = referenceEntityCode(itemNode, entityCodes, entity.code()).orElse("");
        boolean reference = isReferenceDataItem(itemNode, dataType) || hasText(referenceEntityCode);
        dataItems.add(new CloudPivotMetadataSnapshot.DataItemMetadata(
            entity.appCode(),
            entity.code(),
            code,
            name,
            dataType,
            required,
            reference,
            referenceEntityCode,
            firstText(itemNode, "description", "remark", "helpText", "tips").orElse(""),
            textValue(itemNode)
        ));
        if (reference && hasText(referenceEntityCode)) {
            String relationKey = entity.appCode() + ":" + entity.code() + ":" + code + ":" + referenceEntityCode;
            if (relationKeys.add(relationKey)) {
                relations.add(new CloudPivotMetadataSnapshot.EntityRelationMetadata(
                    entity.appCode(),
                    entity.code(),
                    code,
                    referenceEntityCode,
                    "relevance_form",
                    name,
                    textValue(itemNode)
                ));
            }
        }
    }

    private Optional<String> referenceEntityCode(JsonNode node, Set<String> entityCodes, String sourceEntityCode) {
        Optional<String> direct = firstText(node,
            "refSchemaCode", "referenceSchemaCode", "relativeCode", "targetSchemaCode",
            "targetBizModelCode", "targetModelCode", "refCode", "associationSchemaCode", "associationCode",
            "schemaCode"
        );
        Optional<String> normalized = direct.map(this::stripQuotes)
            .filter(entityCodes::contains)
            .filter(value -> !value.equals(sourceEntityCode));
        if (normalized.isPresent()) {
            return normalized;
        }
        for (String key : List.of("options", "option", "setting", "settings", "config", "control", "component", "relevance", "reference", "target", "originalOptions")) {
            Optional<JsonNode> child = firstConfigNode(node, key);
            if (child.isPresent()) {
                Optional<String> childValue = referenceEntityCode(child.get(), entityCodes, sourceEntityCode);
                if (childValue.isPresent()) {
                    return childValue;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> firstConfigNode(JsonNode node, String key) {
        if (node == null || !node.has(key) || node.get(key).isNull()) {
            return Optional.empty();
        }
        JsonNode child = node.get(key);
        if (child.isObject() || child.isArray()) {
            return Optional.of(child);
        }
        return parseJsonStringNode(child);
    }

    private boolean isReferenceDataItem(JsonNode itemNode, String dataType) {
        String value = (dataType + " " + textValue(itemNode)).toLowerCase(Locale.ROOT);
        return value.contains("关联表单")
            || value.contains("relevance_form")
            || value.contains("relevanceform")
            || value.contains("relevance_form_ex")
            || value.contains("relevanceformex")
            || value.contains("relevance")
            || value.contains("related")
            || value.contains("association")
            || value.contains("bizobject")
            || value.contains("biz_object")
            || value.contains("bizproperty")
            || value.contains("reference")
            || value.contains("object");
    }

    private List<JsonNode> firstSuccessfulList(String host, AuthSession session, List<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            try {
                JsonNode body = requestJson(host, endpoint, session);
                List<JsonNode> items = extractItems(body);
                log.info("CloudPivot metadata endpoint {} returned {} item(s), status={}, shape={}", endpoint.path(), items.size(), responseStatus(body), describeShape(body, 0));
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (RuntimeException exception) {
                log.info("CloudPivot metadata endpoint {} failed: {}", endpoint.path(), exception.getMessage());
            }
        }
        return List.of();
    }

    private CloudPivotRuntimeQueryResult queryRemoteRecords(String host, AuthSession session, String schemaCode, int pageSize, boolean enrichAllDetails, int maxRecords) {
        RuntimeException lastFailure = null;
        for (String endpoint : apiPathVariants("/api/runtime/query/listSkipQueryListV2")) {
            try {
                boolean bulkQuery = pageSize >= 50;
                int recordLimit = Math.max(1, Math.min(maxRecords, connectorProperties().getMaxAnalysisRecords()));
                int remainingDetailBudget = enrichAllDetails ? connectorProperties().getMaxDetailEnrichRecords() : Integer.MAX_VALUE;
                int firstDetailLimit = detailEnrichLimit(pageSize, bulkQuery, enrichAllDetails, remainingDetailBudget);
                RuntimePage firstPage = queryRemotePage(host, session, schemaCode, endpoint, pageSize, 0, firstDetailLimit);
                remainingDetailBudget = remainingDetailBudget(remainingDetailBudget, firstPage.records().size(), firstDetailLimit);
                if (!bulkQuery || pageSize <= 1 || firstPage.records().size() >= firstPage.total() || firstPage.records().size() >= recordLimit) {
                    List<Map<String, Object>> limitedRecords = firstPage.records().size() > recordLimit ? firstPage.records().subList(0, recordLimit) : firstPage.records();
                    return new CloudPivotRuntimeQueryResult(schemaCode, firstPage.total(), limitedRecords, endpoint);
                }
                List<Map<String, Object>> records = new ArrayList<>(firstPage.records());
                int page = 1;
                while (records.size() < firstPage.total() && records.size() < recordLimit) {
                    int pageDetailLimit = detailEnrichLimit(pageSize, bulkQuery, enrichAllDetails, remainingDetailBudget);
                    RuntimePage nextPage = queryRemotePage(host, session, schemaCode, endpoint, pageSize, page, pageDetailLimit);
                    if (nextPage.records().isEmpty()) {
                        break;
                    }
                    records.addAll(nextPage.records());
                    remainingDetailBudget = remainingDetailBudget(remainingDetailBudget, nextPage.records().size(), pageDetailLimit);
                    page++;
                }
                if (records.size() > recordLimit) {
                    records = records.subList(0, recordLimit);
                }
                return new CloudPivotRuntimeQueryResult(schemaCode, firstPage.total(), records, endpoint);
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IllegalStateException("CloudPivot runtime query failed") : lastFailure;
    }

    private int remainingDetailBudget(int currentBudget, int returnedRecords, int detailLimit) {
        if (currentBudget == Integer.MAX_VALUE) {
            return currentBudget;
        }
        return Math.max(0, currentBudget - Math.min(returnedRecords, detailLimit));
    }

    private int detailEnrichLimit(int pageSize, boolean bulkQuery, boolean enrichAllDetails, int remainingDetailBudget) {
        if (pageSize <= 1) {
            return 0;
        }
        int requestedLimit = enrichAllDetails
            ? pageSize
            : (bulkQuery ? 0 : Math.min(pageSize, connectorProperties().getMaxListDetailRecords()));
        return Math.max(0, Math.min(requestedLimit, remainingDetailBudget));
    }

    private RuntimePage queryRemotePage(String host, AuthSession session, String schemaCode, String endpoint, int pageSize, int page, int detailEnrichLimit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("queryCode", "");
        body.put("schemaCode", schemaCode);
        body.put("options", Map.of());
        body.put("orderByFields", List.of());
        body.put("orderType", "");
        body.put("page", page);
        body.put("size", pageSize);
        body.put("filtersNewCondition", List.of());
        body.put("queryCondition", List.of());

        JsonNode response = postJson(host, endpoint, session, body);
        ensureBusinessSuccess(response, endpoint);
        JsonNode data = response.has("data") && response.get("data").isObject() ? response.get("data") : response;
        long total = firstLong(data, "totalElements", "total", "totalCount", "count").orElseGet(() -> (long) extractRecordItems(data).size());
        List<JsonNode> items = extractRecordItems(data);
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : items) {
            records.add(toMap(item));
        }
        return new RuntimePage(total, enrichRecordDetails(host, session, schemaCode, records, detailEnrichLimit));
    }

    private List<Map<String, Object>> enrichRecordDetails(String host, AuthSession session, String schemaCode, List<Map<String, Object>> records, int detailEnrichLimit) {
        int enrichLimit = Math.min(Math.max(0, detailEnrichLimit), records.size());
        if (enrichLimit == 0) {
            return records;
        }
        if (enrichLimit == 1) {
            List<Map<String, Object>> enriched = new ArrayList<>(records);
            enriched.set(0, enrichRecordDetail(host, session, schemaCode, records.getFirst()));
            return enriched;
        }

        List<Map<String, Object>> enriched = new ArrayList<>(records);
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(enrichLimit, connectorProperties().getDetailEnrichParallelism()));
        try {
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (int i = 0; i < enrichLimit; i++) {
                Map<String, Object> record = records.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> enrichRecordDetail(host, session, schemaCode, record), executor));
            }
            for (int i = 0; i < futures.size(); i++) {
                enriched.set(i, futures.get(i).join());
            }
            return enriched;
        } finally {
            executor.shutdown();
        }
    }

    private Map<String, Object> enrichRecordDetail(String host, AuthSession session, String schemaCode, Map<String, Object> record) {
        String recordId = recordId(record).orElse(null);
        if (!hasText(recordId)) {
            return record;
        }
        try {
            JsonNode detail = loadRecordDetail(host, session, schemaCode, recordId);
            Map<String, Object> enriched = new LinkedHashMap<>(record);
            Optional<String> displayName = firstText(detail, "instanceName", "name", "title");
            JsonNode data = detail.has("data") && detail.get("data").isObject() ? detail.get("data") : detail;
            displayName = displayName.or(() -> firstText(data, "instanceName", "name", "title"));
            displayName.ifPresent(value -> enriched.putIfAbsent("instanceName", value));

            JsonNode bizObject = firstObject(data, "bizObject", "bizObjectData", "object", "record").orElse(data);
            displayName = displayName.or(() -> firstText(bizObject, "name", "title", "instanceName"));
            displayName.ifPresent(value -> enriched.putIfAbsent("name", value));
            JsonNode bizData = firstObject(bizObject, "data", "properties", "values", "formData").orElse(null);
            if (bizData != null && bizData.isObject()) {
                Map<String, Object> detailData = toMap(bizData);
                Map<String, Object> mergedData = new LinkedHashMap<>();
                displayName.ifPresent(value -> mergedData.put("instanceName", value));
                Object existingData = enriched.get("data");
                if (existingData instanceof Map<?, ?> existingDataMap) {
                    existingDataMap.forEach((key, value) -> mergedData.put(String.valueOf(key), value));
                }
                detailData.forEach(mergedData::putIfAbsent);
                enriched.put("data", mergedData);
            }
            return enriched;
        } catch (RuntimeException exception) {
            log.info("CloudPivot runtime detail endpoint failed(schemaCode={}, objectId={}): {}", schemaCode, recordId, exception.getMessage());
            return record;
        }
    }

    private JsonNode loadRecordDetail(String host, AuthSession session, String schemaCode, String recordId) {
        RuntimeException lastFailure = null;
        Map<String, String> params = Map.of("schemaCode", schemaCode, "objectId", recordId);
        for (String path : apiPathVariants("/api/runtime/form/loadNew")) {
            try {
                JsonNode response = getJson(host, path, session, params);
                ensureBusinessSuccess(response, path);
                return response;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IllegalStateException("CloudPivot runtime detail query failed") : lastFailure;
    }

    private Optional<String> recordId(Map<String, Object> record) {
        for (String key : List.of("id", "objectId", "dataId")) {
            Object value = record.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return Optional.of(String.valueOf(value));
            }
        }
        Object data = record.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            for (String key : List.of("id", "objectId", "dataId")) {
                Object value = dataMap.get(key);
                if (value != null && hasText(String.valueOf(value))) {
                    return Optional.of(String.valueOf(value));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<AuthSession> authenticate(String host, String username, String password) {
        Optional<RsaKey> userKey = fetchRsaKey(host, "/user/public/getKey");
        Optional<RsaKey> apiKey = fetchRsaKey(host, "/api/public/getKey");
        Set<LoginPayload> payloads = new LinkedHashSet<>();
        payloads.add(new LoginPayload(username, password, null, null));
        userKey.flatMap(key -> encryptPassword(password, key)).ifPresent(encrypted -> payloads.add(new LoginPayload(username, encrypted, userKey.get().index(), null)));
        apiKey.flatMap(key -> encryptPassword(password, key)).ifPresent(encrypted -> payloads.add(new LoginPayload(username, encrypted, apiKey.get().index(), null)));

        for (LoginPayload payload : payloads) {
            Optional<AuthSession> session = tryAuthenticationCodeLogin(host, payload);
            if (session.isPresent()) {
                return session;
            }
            session = tryOAuthPasswordLogin(host, payload);
            if (session.isPresent()) {
                return session;
            }
        }
        return Optional.empty();
    }

    private Optional<AuthSession> tryAuthenticationCodeLogin(String host, LoginPayload payload) {
        List<Map<String, Object>> bodies = List.of(
            authenticationCodeLoginMap(host, payload, "username"),
            authenticationCodeLoginMap(host, payload, "account"),
            authenticationCodeLoginMap(host, payload, "userName")
        );
        for (Map<String, Object> body : bodies) {
            try {
                HttpRequest request = baseRequest(host + "/user/login/Authentication/get_code")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
                JsonNode codeResponse = sendJson(request);
                Optional<AuthSession> directToken = tokenFrom(codeResponse);
                if (directToken.isPresent()) {
                    return directToken;
                }
                Optional<String> code = firstText(codeResponse, "code", "auth_code", "data", "ticket");
                if (code.isPresent()) {
                    Optional<AuthSession> token = requestTokenByCode(host, code.get());
                    if (token.isPresent()) {
                        return token;
                    }
                }
            } catch (RuntimeException | JsonProcessingException ignored) {
            }
        }
        return Optional.empty();
    }

    private Optional<AuthSession> requestTokenByCode(String host, String code) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("url", host + "/user");
        params.put("client_secret", "");
        params.put("client_id", "api");
        params.put("redirect_uri", host + "/oauth");
        try {
            HttpRequest request = baseRequest(host + "/user/login/Authentication/get_token?" + formEncode(params))
                .GET()
                .build();
            return tokenFrom(sendJson(request));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private Optional<AuthSession> tryOAuthPasswordLogin(String host, LoginPayload payload) {
        List<Map<String, String>> bodies = List.of(
            oauthMap(payload, "username"),
            oauthMap(payload, "account"),
            oauthMap(payload, "userName")
        );
        for (Map<String, String> body : bodies) {
            try {
                HttpRequest request = baseRequest(host + "/user/oauth/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formEncode(body), StandardCharsets.UTF_8))
                    .build();
                Optional<AuthSession> session = tokenFrom(sendJson(request));
                if (session.isPresent()) {
                    return session;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return Optional.empty();
    }

    private Map<String, Object> authenticationCodeLoginMap(String host, LoginPayload payload, String accountField) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(accountField, payload.username());
        body.put("password", payload.password());
        body.put("url", redirectUrl(host));
        body.put("portal", true);
        if (hasText(payload.keyIndex())) {
            body.put("index", payload.keyIndex());
        }
        return body;
    }

    private Map<String, String> oauthMap(LoginPayload payload, String accountField) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(accountField, payload.username());
        body.put("password", payload.password());
        body.put("client_id", "api");
        body.put("scope", "read");
        if (hasText(payload.keyIndex())) {
            body.put("index", payload.keyIndex());
        }
        body.put("grant_type", "password");
        return body;
    }

    private String redirectUrl(String host) {
        String authorizationUrl = host + "/user/oauth/authorize?client_id=api&response_type=code&scope=read&redirect_uri=" + host + "/oauth";
        return host + "/user/login?redirect_uri=" + encode(authorizationUrl);
    }

    private JsonNode requestJson(String host, Endpoint endpoint, AuthSession session) {
        RuntimeException lastFailure = null;
        for (String path : apiPathVariants(endpoint.path())) {
            try {
                String url = host + path;
                if (!endpoint.params().isEmpty()) {
                    url += "?" + formEncode(endpoint.params());
                }
                HttpRequest.Builder builder = authorizedRequest(url, session);
                HttpRequest request = "POST".equals(endpoint.method())
                    ? builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)).build()
                    : builder.GET().build();
                JsonNode response = sendJson(request);
                ensureBusinessSuccess(response, path);
                return response;
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure == null ? new IllegalStateException("CloudPivot request failed") : lastFailure;
    }

    private JsonNode getJson(String host, String path, AuthSession session, Map<String, String> params) {
        String url = host + path;
        if (!params.isEmpty()) {
            url += "?" + formEncode(params);
        }
        HttpRequest request = authorizedRequest(url, session)
            .GET()
            .build();
        return sendJson(request);
    }

    private JsonNode postJson(String host, String path, AuthSession session, Map<String, Object> body) {
        try {
            HttpRequest request = authorizedRequest(host + path, session)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            return sendJson(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("云枢请求参数序列化失败", exception);
        }
    }

    private List<String> apiPathVariants(String path) {
        if (path.startsWith("/api/") && !path.startsWith("/api/api/")) {
            return List.of("/api" + path, path);
        }
        return List.of(path);
    }

    private void ensureBusinessSuccess(JsonNode response, String endpoint) {
        Optional<Long> errcode = firstLong(response, "errcode", "code");
        if (errcode.isPresent() && errcode.get() != 0L) {
            String message = firstText(response, "errmsg", "message", "msg", "data").orElse("unknown error");
            throw new IllegalStateException("CloudPivot business request failed(endpoint=" + endpoint + ", errcode=" + errcode.get() + ", message=" + message + ")");
        }
    }

    private HttpRequest.Builder authorizedRequest(String url, AuthSession session) {
        HttpRequest.Builder builder = baseRequest(url)
            .header("Authorization", "Bearer " + session.accessToken())
            .header("Time-Zone", "Asia/Shanghai")
            .header("X-Client-Lang", "zh-CN");
        if (hasText(session.corpId())) {
            builder.header("X-LowCode-Corpid", session.corpId());
        }
        if (hasText(session.engineCode())) {
            builder.header("X-LowCode-Enginecode", session.engineCode());
        }
        return builder;
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !hasText(response.body())) {
                throw new IllegalStateException("云枢接口调用失败(status=" + response.statusCode() + ", bodyLength=" + (response.body() == null ? 0 : response.body().length()) + ")");
            }
            return objectMapper.readTree(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("云枢接口调用失败", exception);
        }
    }

    private Optional<RsaKey> fetchRsaKey(String host, String path) {
        try {
            JsonNode body = sendJson(baseRequest(host + path).GET().build());
            Optional<String> key = firstText(body, "key", "publicKey");
            Optional<String> index = firstText(body, "index", "keyIndex");
            if (key.isPresent() && index.isPresent()) {
                return Optional.of(new RsaKey(index.get(), key.get()));
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.empty();
    }

    private Optional<String> encryptPassword(String password, RsaKey key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key.publicKey());
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Optional.of(Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8))));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Optional<AuthSession> tokenFrom(JsonNode response) {
        JsonNode source = response.has("data") && response.get("data").isObject() ? response.get("data") : response;
        Optional<String> token = firstText(source, "access_token", "accessToken", "token", "T");
        if (token.isEmpty()) {
            return Optional.empty();
        }
        String refreshToken = firstText(source, "refresh_token", "refreshToken").orElse("");
        String corpId = firstText(source, "corpId", "corp_id")
            .or(() -> jwtText(token.get(), "corpId", "corp_id"))
            .or(() -> hasText(configuredCorpId) ? Optional.of(configuredCorpId) : Optional.empty())
            .orElse("");
        String engineCode = firstText(source, "engineCode", "engine_code", "tenantId", "tenant_id")
            .or(() -> jwtText(token.get(), "tenantId", "tenant_id", "engineCode", "engine_code"))
            .orElse("");
        return Optional.of(new AuthSession(token.get(), refreshToken, corpId, engineCode));
    }

    private Optional<String> jwtText(String token, String... keys) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return Optional.empty();
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return firstText(objectMapper.readTree(decoded), keys);
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    private List<JsonNode> extractItems(JsonNode body) {
        return extractItems(body, 0);
    }

    private List<JsonNode> extractRecordItems(JsonNode node) {
        for (String key : List.of("content", "records", "rows", "items", "list", "data")) {
            if (node != null && node.has(key) && node.get(key).isArray()) {
                return toList(node.get(key));
            }
        }
        if (node != null && node.isArray()) {
            return toList(node);
        }
        return List.of();
    }

    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<>() {
        });
    }

    private Optional<Long> firstLong(JsonNode node, String... keys) {
        if (node == null) {
            return Optional.empty();
        }
        for (String key : keys) {
            if (node.has(key) && node.get(key).canConvertToLong()) {
                return Optional.of(node.get(key).asLong());
            }
        }
        return Optional.empty();
    }

    private Optional<Boolean> firstBoolean(JsonNode node, String... keys) {
        if (node == null) {
            return Optional.empty();
        }
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                JsonNode value = node.get(key);
                if (value.isBoolean()) {
                    return Optional.of(value.asBoolean());
                }
                if (value.isNumber()) {
                    return Optional.of(value.asInt() != 0);
                }
                if (value.isTextual()) {
                    String text = value.asText().trim();
                    if (hasText(text)) {
                        return Optional.of("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private List<JsonNode> extractItems(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 4) {
            return List.of();
        }
        if (node.isArray()) {
            return flattenArray(node, depth);
        }
        for (String key : List.of("data", "content", "list", "records", "rows", "items", "children", "result", "bizModels", "apps")) {
            if (node.has(key)) {
                List<JsonNode> items = extractItems(node.get(key), depth + 1);
                if (!items.isEmpty()) {
                    return items;
                }
            }
        }
        return List.of();
    }

    private List<JsonNode> flattenArray(JsonNode arrayNode, int depth) {
        List<JsonNode> result = new ArrayList<>();
        arrayNode.forEach(item -> {
            if (isMetadataNode(item)) {
                result.add(item);
            }
            for (String childKey : List.of("children", "childs", "bizModels", "functions", "items", "list")) {
                if (item.has(childKey)) {
                    result.addAll(extractItems(item.get(childKey), depth + 1));
                }
            }
        });
        return result;
    }

    private boolean isMetadataNode(JsonNode node) {
        return node != null && node.isObject() && (
            firstText(node, "code", "appCode", "schemaCode", "bizModelCode", "modelCode", "id").isPresent()
                || firstText(node, "name", "appName", "schemaName", "displayName").isPresent()
        );
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> result = new ArrayList<>();
        arrayNode.forEach(result::add);
        return result;
    }

    private String responseStatus(JsonNode node) {
        Optional<String> code = firstText(node, "errcode", "code", "status");
        Optional<String> message = firstText(node, "errmsg", "message", "msg");
        return code.orElse("ok") + ":" + message.orElse("");
    }

    private String describeShape(JsonNode node, int depth) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (depth > 2) {
            return node.isArray() ? "array(" + node.size() + ")" : node.getNodeType().name();
        }
        if (node.isArray()) {
            return "array(" + node.size() + ")" + (node.isEmpty() ? "" : "[" + describeShape(node.get(0), depth + 1) + "]");
        }
        if (!node.isObject()) {
            return node.getNodeType().name();
        }
        List<String> parts = new ArrayList<>();
        node.fieldNames().forEachRemaining(field -> {
            if (parts.size() < 12) {
                parts.add(field + ":" + describeShape(node.get(field), depth + 1));
            }
        });
        return "object{" + String.join(",", parts) + "}";
    }

    private Optional<String> firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String value = node.get(key).isTextual() ? node.get(key).asText() : node.get(key).toString();
                if (hasText(value)) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<JsonNode> firstObject(JsonNode node, String... keys) {
        if (node == null) {
            return Optional.empty();
        }
        for (String key : keys) {
            if (node.has(key) && node.get(key).isObject()) {
                return Optional.of(node.get(key));
            }
        }
        return Optional.empty();
    }

    private String textValue(JsonNode node) {
        return node == null ? "" : node.toString();
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while ((result.startsWith("\"") && result.endsWith("\"")) || (result.startsWith("'") && result.endsWith("'"))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(requestTimeout())
            .header("Accept", "application/json, text/plain, */*");
    }

    private String formEncode(Map<String, String> values) {
        return values.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl.trim();
        int hashIndex = value.indexOf('#');
        if (hashIndex >= 0) {
            value = value.substring(0, hashIndex);
        }
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        for (String uiPath : List.of("/login", "/admin", "/portal-page", "/app-list", "/oauth")) {
            int pathIndex = value.indexOf(uiPath);
            if (pathIndex > 0) {
                return value.substring(0, pathIndex);
            }
        }
        return value;
    }

    private boolean isLocalTestUrl(String baseUrl) {
        return baseUrl.contains("example.local") || baseUrl.contains("localhost") || baseUrl.contains("127.0.0.1");
    }

    private CloudPivotMetadataSnapshot fallbackMetadata() {
        return new CloudPivotMetadataSnapshot(
            List.of(
                new CloudPivotMetadataSnapshot.AppMetadata("metadata_app", "元数据应用", "cloudpivot-metadata"),
                new CloudPivotMetadataSnapshot.AppMetadata("workflow_metadata_app", "流程元数据应用", "cloudpivot-workflow-metadata")
            ),
            List.of(
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_object", "元数据对象", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_detail", "元数据明细", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_relation", "元数据关联", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("workflow_metadata_app", "metadata_form", "元数据表单", "data", "medium"),
                new CloudPivotMetadataSnapshot.EntityMetadata("workflow_metadata_app", "metadata_attachment", "元数据附件", "attachment", "medium")
            )
        );
    }

    private CloudPivotRuntimeQueryResult fallbackRuntimeQueryResult(String schemaCode, int pageSize) {
        return new CloudPivotRuntimeQueryResult(schemaCode, 0, List.of(), "local-fallback");
    }

    private CloudPivotRuntimeProperties.Connector connectorProperties() {
        return runtimeProperties.getConnector();
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(connectorProperties().getRequestTimeoutSeconds());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RsaKey(String index, String publicKey) {
    }

    private record LoginPayload(String username, String password, String keyIndex, String credentialType) {
    }

    private record AuthSession(String accessToken, String refreshToken, String corpId, String engineCode) {
    }

    private record RuntimePage(long total, List<Map<String, Object>> records) {
    }

    private record Endpoint(String method, String path, Map<String, String> params) {
    }
}
