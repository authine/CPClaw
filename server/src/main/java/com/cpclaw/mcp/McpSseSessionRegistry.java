package com.cpclaw.mcp;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Holds only short-lived MCP SSE connection state. Credentials are never
 * persisted; they live for the lifetime of the client connection only.
 */
@org.springframework.stereotype.Component
public class McpSseSessionRegistry {
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public McpSseSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Session open(String installationId, String username, String password) {
        return open(installationId, username, password, "");
    }

    public Session open(String installationId, String username, String password, String externalPrincipal) {
        String id = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(SESSION_TIMEOUT_MS);
        Session session = new Session(id, emitter, installationId, username, password, externalPrincipal == null ? "" : externalPrincipal.trim());
        sessions.put(id, session);
        Runnable remove = () -> sessions.remove(id);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        return session;
    }

    public Session require(String id) {
        Session session = sessions.get(id);
        if (session == null) throw new IllegalArgumentException("MCP SSE 会话不存在或已过期，请重新连接服务。");
        return session;
    }

    public void send(Session session, Object payload) {
        try {
            session.emitter().send(SseEmitter.event()
                .name("message")
                .data(objectMapper.writeValueAsString(payload)));
        } catch (IOException exception) {
            sessions.remove(session.id());
            throw new IllegalStateException("无法向 MCP SSE 客户端发送响应。", exception);
        }
    }

    public record Session(String id, SseEmitter emitter, String installationId, String username, String password, String externalPrincipal) {}
}
