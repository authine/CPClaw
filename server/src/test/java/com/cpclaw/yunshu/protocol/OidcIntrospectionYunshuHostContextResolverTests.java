package com.cpclaw.yunshu.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class OidcIntrospectionYunshuHostContextResolverTests {
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/introspect", exchange -> {
            byte[] body = "{\"active\":true,\"sub\":\"user-a\",\"tenant_id\":\"tenant-a\",\"scope\":\"yunshu.read\",\"roles\":[\"analyst\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }

    @AfterEach
    void stopServer() { if (server != null) server.stop(0); }

    @Test
    void resolvesTenantAndSubjectOnlyFromActiveIntrospectionResponse() {
        var resolver = new OidcIntrospectionYunshuHostContextResolver(
                new ObjectMapper(), "workbuddy", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect"),
                "cpclaw", "secret", Duration.ofSeconds(2), HttpClient.newHttpClient());
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer opaque-token");
        request.addHeader("X-Yunshu-Tenant-Id", "attacker-tenant");
        request.addHeader("X-Yunshu-Principal", "attacker-user");

        var context = resolver.resolve(request);

        assertTrue(context.isPresent());
        assertEquals("tenant-a", context.get().tenantId());
        assertEquals("user-a", context.get().subjectId());
        assertEquals("workbuddy", context.get().hostId());
        assertEquals(true, context.get().capabilities().get("oidcIntrospectionVerified"));
    }

    @Test
    void rejectsMissingBearerToken() {
        var resolver = new OidcIntrospectionYunshuHostContextResolver(
                new ObjectMapper(), "workbuddy", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/introspect"),
                "cpclaw", "secret", Duration.ofSeconds(2), HttpClient.newHttpClient());

        assertTrue(resolver.resolve(new MockHttpServletRequest()).isEmpty());
    }
}
