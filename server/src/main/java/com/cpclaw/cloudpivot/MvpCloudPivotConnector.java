package com.cpclaw.cloudpivot;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Cipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MvpCloudPivotConnector implements CloudPivotConnector {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean allowFallbackMetadata;

    public MvpCloudPivotConnector(ObjectMapper objectMapper, @Value("${cpclaw.cloudpivot.allow-metadata-fallback:false}") boolean allowFallbackMetadata) {
        this.objectMapper = objectMapper;
        this.allowFallbackMetadata = allowFallbackMetadata;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
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

    private CloudPivotMetadataSnapshot fetchRemoteMetadata(String host, AuthSession session) {
        List<JsonNode> appNodes = firstSuccessfulList(host, session, List.of(
            new Endpoint("GET", "/api/app/apppackage/list_all", Map.of()),
            new Endpoint("GET", "/api/app/apppackage/list", Map.of()),
            new Endpoint("GET", "/api/app/apppackage/trees", Map.of())
        ));

        Map<String, CloudPivotMetadataSnapshot.AppMetadata> apps = new LinkedHashMap<>();
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities = new LinkedHashMap<>();
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

        return new CloudPivotMetadataSnapshot(new ArrayList<>(apps.values()), new ArrayList<>(entities.values()));
    }

    private void fetchEntitiesForApp(
        String host,
        AuthSession session,
        String appCode,
        Map<String, CloudPivotMetadataSnapshot.AppMetadata> apps,
        Map<String, CloudPivotMetadataSnapshot.EntityMetadata> entities
    ) {
        List<JsonNode> entityNodes = firstSuccessfulList(host, session, List.of(
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
        String appCode = firstText(entityNode, "appCode", "appPackageCode", "packageCode").orElse(fallbackAppCode);
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

    private List<JsonNode> firstSuccessfulList(String host, AuthSession session, List<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints) {
            try {
                JsonNode body = requestJson(host, endpoint, session);
                List<JsonNode> items = extractItems(body);
                if (!items.isEmpty()) {
                    return items;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return List.of();
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
        List<Map<String, String>> bodies = List.of(
            loginMap(payload, "username"),
            loginMap(payload, "account"),
            loginMap(payload, "userName")
        );
        for (Map<String, String> body : bodies) {
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
        params.put("client_id", "api");
        params.put("scope", "read");
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

    private Map<String, String> loginMap(LoginPayload payload, String accountField) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(accountField, payload.username());
        body.put("password", payload.password());
        body.put("client_id", "api");
        body.put("scope", "read");
        if (hasText(payload.keyIndex())) {
            body.put("key", payload.keyIndex());
            body.put("index", payload.keyIndex());
        }
        return body;
    }

    private Map<String, String> oauthMap(LoginPayload payload, String accountField) {
        Map<String, String> body = loginMap(payload, accountField);
        body.put("grant_type", "password");
        return body;
    }

    private JsonNode requestJson(String host, Endpoint endpoint, AuthSession session) {
        String url = host + endpoint.path();
        if (!endpoint.params().isEmpty()) {
            url += "?" + formEncode(endpoint.params());
        }
        HttpRequest.Builder builder = baseRequest(url)
            .header("Authorization", "Bearer " + session.accessToken())
            .header("X-Client-Lang", "zh-CN");
        if (hasText(session.corpId())) {
            builder.header("X-LowCode-Corpid", session.corpId());
        }
        if (hasText(session.engineCode())) {
            builder.header("X-LowCode-Enginecode", session.engineCode());
        }
        HttpRequest request = "POST".equals(endpoint.method())
            ? builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8)).build()
            : builder.GET().build();
        return sendJson(request);
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !hasText(response.body())) {
                throw new IllegalStateException("云枢接口调用失败");
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
        String corpId = firstText(source, "corpId", "corp_id").orElse("");
        String engineCode = firstText(source, "engineCode", "engine_code").orElse("");
        return Optional.of(new AuthSession(token.get(), refreshToken, corpId, engineCode));
    }

    private List<JsonNode> extractItems(JsonNode body) {
        JsonNode source = body;
        if (body.has("data")) {
            source = body.get("data");
        }
        if (source.isArray()) {
            return toList(source);
        }
        for (String key : List.of("content", "list", "records", "rows", "items", "data")) {
            if (source.has(key) && source.get(key).isArray()) {
                return toList(source.get(key));
            }
        }
        return List.of();
    }

    private List<JsonNode> toList(JsonNode arrayNode) {
        List<JsonNode> result = new ArrayList<>();
        arrayNode.forEach(result::add);
        return result;
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

    private String textValue(JsonNode node) {
        return node == null ? "" : node.toString();
    }

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
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
        if (value.endsWith("/login")) {
            value = value.substring(0, value.length() - "/login".length());
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RsaKey(String index, String publicKey) {
    }

    private record LoginPayload(String username, String password, String keyIndex, String credentialType) {
    }

    private record AuthSession(String accessToken, String refreshToken, String corpId, String engineCode) {
    }

    private record Endpoint(String method, String path, Map<String, String> params) {
    }
}
