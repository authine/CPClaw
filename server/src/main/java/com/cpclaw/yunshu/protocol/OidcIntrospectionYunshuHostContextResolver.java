package com.cpclaw.yunshu.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production identity adapter for hosts that expose an OAuth2/OIDC token
 * introspection endpoint. The gateway never trusts subject or tenant headers;
 * both values are read from the active introspection response.
 */
@Component
@ConditionalOnProperty(name = "cpclaw.yunshu-skill.identity-mode", havingValue = "oidc")
public class OidcIntrospectionYunshuHostContextResolver implements YunshuHostContextResolver {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String hostId;
    private final URI introspectionUri;
    private final String clientId;
    private final String clientSecret;
    private final Duration timeout;

    public OidcIntrospectionYunshuHostContextResolver(
            ObjectMapper objectMapper,
            @Value("${cpclaw.yunshu-skill.host-id:workbuddy}") String hostId,
            @Value("${cpclaw.yunshu-skill.oidc.introspection-url:}") String introspectionUrl,
            @Value("${cpclaw.yunshu-skill.oidc.client-id:}") String clientId,
            @Value("${cpclaw.yunshu-skill.oidc.client-secret:}") String clientSecret,
            @Value("${cpclaw.yunshu-skill.oidc.timeout-ms:3000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.hostId = required(hostId, "Yunshu OIDC hostId");
        this.introspectionUri = URI.create(required(introspectionUrl, "Yunshu OIDC introspection URL"));
        this.clientId = required(clientId, "Yunshu OIDC client id");
        this.clientSecret = required(clientSecret, "Yunshu OIDC client secret");
        this.timeout = Duration.ofMillis(Math.max(250, timeoutMs));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    OidcIntrospectionYunshuHostContextResolver(ObjectMapper objectMapper, String hostId,
            URI introspectionUri, String clientId, String clientSecret, Duration timeout,
            HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.hostId = required(hostId, "Yunshu OIDC hostId");
        this.introspectionUri = introspectionUri;
        this.clientId = required(clientId, "Yunshu OIDC client id");
        this.clientSecret = required(clientSecret, "Yunshu OIDC client secret");
        this.timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
        this.httpClient = httpClient == null ? HttpClient.newBuilder().connectTimeout(this.timeout).build() : httpClient;
    }

    @Override
    public String hostId() {
        return hostId;
    }

    @Override
    public Optional<YunshuHostContext> resolve(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader(AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) return Optional.empty();
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank() || token.length() > 16_384) return Optional.empty();
        try {
            String basic = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest httpRequest = HttpRequest.newBuilder(introspectionUri)
                    .timeout(timeout)
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("token=" + urlEncode(token)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return Optional.empty();
            JsonNode payload = objectMapper.readTree(response.body());
            if (!payload.path("active").asBoolean(false)) return Optional.empty();
            String subject = firstText(payload, "sub", "username", "uid");
            String tenant = firstText(payload, "tenant_id", "tenantId", "org_id", "organization");
            if (subject.isBlank() || tenant.isBlank()) return Optional.empty();
            return Optional.of(new YunshuHostContext(tenant, subject, hostId, Map.of(
                    "oidcIntrospectionVerified", true,
                    "activeToken", true)));
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private String firstText(JsonNode payload, String... names) {
        for (String name : names) {
            JsonNode value = payload.get(name);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText().trim();
        }
        return "";
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " 未配置");
        return value.trim();
    }
}
