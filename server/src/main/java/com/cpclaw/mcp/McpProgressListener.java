package com.cpclaw.mcp;

import com.cpclaw.task.dto.TaskProgressEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Emits MCP-standard progress notifications when the caller opted in with a progress token. */
public final class McpProgressListener {
    private final McpSseSessionRegistry registry;
    private final McpSseSessionRegistry.Session session;
    private final Object progressToken;

    public McpProgressListener(McpSseSessionRegistry registry, McpSseSessionRegistry.Session session, Object progressToken) {
        this.registry = registry;
        this.session = session;
        this.progressToken = progressToken;
    }

    public void publish(TaskProgressEvent event) {
        if (session == null || progressToken == null || String.valueOf(progressToken).isBlank()) return;
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("progressToken", progressToken);
        parameters.put("progress", Math.max(0, Math.min(100, event.progress())));
        parameters.put("total", 100);
        parameters.put("message", event.title() + "：" + event.message());
        try {
            registry.send(session, Map.of("jsonrpc", "2.0", "method", "notifications/progress", "params", parameters));
        } catch (RuntimeException ignored) {
            // Streaming progress is best-effort; the final task envelope remains authoritative.
        }
    }
}
