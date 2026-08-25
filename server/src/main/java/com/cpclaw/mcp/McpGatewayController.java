package com.cpclaw.mcp;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskSpec;
import com.cpclaw.task.TaskContinuationTokenService;
import com.cpclaw.identity.PrincipalContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** MCP JSON-RPC gateway exposed through an SSE-compatible endpoint. */
@RestController
@RequestMapping("/api/mcp/cloudpivot")
public class McpGatewayController {
    private static final Logger log = LoggerFactory.getLogger(McpGatewayController.class);
    private final McpInstallationService installationService;
    private final McpAuditService auditService;
    private final McpSseSessionRegistry sseSessionRegistry;
    private final McpSemanticTaskService semanticTaskService;
    private final McpTaskExperienceRenderer experienceRenderer;
    private final TaskContinuationTokenService continuationTokenService;
    private final ObjectMapper objectMapper;
    private final PrincipalContextService principalContextService;

    public McpGatewayController(
        McpInstallationService installationService,
        McpAuditService auditService,
        McpSseSessionRegistry sseSessionRegistry,
        McpSemanticTaskService semanticTaskService,
        McpTaskExperienceRenderer experienceRenderer,
        TaskContinuationTokenService continuationTokenService,
        ObjectMapper objectMapper,
        PrincipalContextService principalContextService
    ) {
        this.installationService = installationService;
        this.auditService = auditService;
        this.sseSessionRegistry = sseSessionRegistry;
        this.semanticTaskService = semanticTaskService;
        this.experienceRenderer = experienceRenderer;
        this.continuationTokenService = continuationTokenService;
        this.objectMapper = objectMapper;
        this.principalContextService = principalContextService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(
        @RequestBody JsonNode request,
        @RequestHeader(value = "X-CPClaw-Installation-Id", required = false) String installationId,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Username", required = false) String cloudPivotUsername,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Password", required = false) String cloudPivotPassword,
        @RequestHeader(value = "X-CPClaw-Principal", required = false) String externalPrincipal
    ) {
        return handle(request, installationId, cloudPivotUsername, cloudPivotPassword, externalPrincipal, null);
    }

    private Map<String, Object> handle(
        JsonNode request,
        String installationId,
        String cloudPivotUsername,
        String cloudPivotPassword,
        String externalPrincipal,
        McpSseSessionRegistry.Session session
    ) {
        Object id = jsonRpcId(request);
        String method = text(request, "method");
        try {
            return switch (method) {
                case "initialize" -> success(id, Map.of(
                    "protocolVersion", "2025-06-18",
                    "capabilities", Map.of("tools", Map.of()),
                    "serverInfo", Map.of("name", "cpclaw-yunshu", "version", "0.3.0")
                ));
                case "notifications/initialized" -> Map.of();
                case "tools/list" -> success(id, Map.of("tools", tools()));
                case "tools/call" -> call(id, request, installationId, cloudPivotUsername, cloudPivotPassword, externalPrincipal, session);
                default -> error(id, -32601, "未实现的 MCP 方法：" + method);
            };
        } catch (RuntimeException exception) {
            log.warn("MCP call failed: method={}, installation={}, message={}", method, safeId(installationId), exception.getMessage());
            return error(id, -32000, exception.getMessage() == null ? "MCP 调用失败" : exception.getMessage());
        }
    }

    /**
     * MCP clients using an SSE server entry discover this endpoint first. The
     * server advertises the message endpoint instead of requiring a local Node
     * adapter or an installation-directory path in client configuration.
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(
        @RequestHeader(value = "X-CPClaw-Installation-Id", required = false) String installationId,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Username", required = false) String cloudPivotUsername,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Password", required = false) String cloudPivotPassword,
        @RequestHeader(value = "X-CPClaw-Principal", required = false) String externalPrincipal
    ) {
        if (!hasText(installationId)) {
            throw new IllegalArgumentException("缺少 MCP 安装实例标识。");
        }
        installationService.getOrCreate(installationId);
        McpSseSessionRegistry.Session session = sseSessionRegistry.open(installationId, cloudPivotUsername, cloudPivotPassword, externalPrincipal);
        try {
            session.emitter().send(SseEmitter.event()
                .name("endpoint")
                .data("/api/mcp/cloudpivot/message?sessionId=" + session.id()));
        } catch (IOException exception) {
            session.emitter().completeWithError(exception);
            throw new IllegalStateException("无法建立 MCP SSE 会话。", exception);
        }
        return session.emitter();
    }

    @PostMapping(value = "/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> message(@RequestParam String sessionId, @RequestBody JsonNode request) {
        McpSseSessionRegistry.Session session = sseSessionRegistry.require(sessionId);
        Map<String, Object> response = handle(request, session.installationId(), session.username(), session.password(), session.externalPrincipal(), session);
        if (request.has("id") && !request.path("id").isNull()) {
            try {
                sseSessionRegistry.send(session, response);
            } catch (RuntimeException exception) {
                // A client may close/reconnect immediately after POSTing the
                // message. The request has been accepted and the SSE registry
                // removes the dead session; do not turn that transport race
                // into WorkBuddy's generic HTTP 500 / -32603 error.
                log.debug("MCP SSE response channel closed: session={}, message={}", safeId(session.id()), exception.getMessage());
            }
        }
        return ResponseEntity.accepted().build();
    }

    @PostMapping(value = "/tasks/{taskId}/continue", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> continueTask(
        @PathVariable String taskId,
        @RequestBody JsonNode request,
        @RequestHeader(value = "X-CPClaw-Installation-Id", required = false) String installationId,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Username", required = false) String username,
        @RequestHeader(value = "X-CPClaw-CloudPivot-Password", required = false) String password,
        @RequestHeader(value = "X-CPClaw-Principal", required = false) String principal
    ) {
        String token = text(request, "continuationToken");
        String external = hasText(principal) ? principal.trim() : "huangj";
        String effectivePrincipal = principalContextService.resolveExternal(external).principalId();
        if (!continuationTokenService.verify(token, taskId, effectivePrincipal)) {
            return Map.of("success", false, "status", "blocked", "message", "续接票据无效、已过期或不属于当前用户。", "taskId", taskId);
        }
        BoundCloudPivotConnection connection = installationService.requireEnabledConnection(installationId, username, password);
        String turnId = text(request, "turnId");
        String clientRequestId = text(request, "clientRequestId");
        TaskExperienceEnvelope result = semanticTaskService.handle(
            connection,
            text(request, "request"),
            textList(request.path("context")),
            clientRequestId,
            text(request, "conversationId"),
            turnId,
            withContinuationToken(parseTaskSpec(request.path("taskSpec"), text(request, "request"), text(request, "conversationId"), turnId, clientRequestId), token),
            effectivePrincipal,
            ignored -> { }
        );
        return Map.of("success", true, "taskId", taskId, "result", toolResult(result, experienceRenderer.markdown(result)));
    }

    private Map<String, Object> call(Object id, JsonNode request, String headerInstallationId, String cloudPivotUsername, String cloudPivotPassword, String externalPrincipal, McpSseSessionRegistry.Session session) {
        String toolName = text(request.path("params"), "name");
        JsonNode arguments = request.path("params").path("arguments");
        String installationId = headerInstallationId;
        if (!hasText(installationId)) installationId = text(arguments, "installationId");
        if (!hasText(installationId)) throw new IllegalArgumentException("缺少 MCP 安装实例标识。请在 MCP 客户端适配器中设置 CPCLAW_MCP_INSTALLATION_ID。");
        try {
            BoundCloudPivotConnection connection = installationService.requireEnabledConnection(installationId, cloudPivotUsername, cloudPivotPassword);
            Map<String, Object> response = switch (toolName) {
                case "cpclaw_cloudpivot_agent", "yunshu_handle_request" -> semanticResponse(id, request, connection, arguments, externalPrincipal, session);
                default -> error(id, -32602, "当前云枢 MCP 只公开 yunshu_handle_request 或 cpclaw_cloudpivot_agent。请直接提交用户的自然语言业务目标，不要调用或请求 schemaCode、apiCode 等内部编码。");
            };
            String auditStatus = response.containsKey("error") ? "REJECTED" : toolStatus(response, toolName);
            auditService.record(installationId, toolName, auditStatus, "MCP 工具调用已完成");
            return response;
        } catch (RuntimeException exception) {
            auditService.record(installationId, toolName, "FAILED", exception.getMessage());
            throw exception;
        }
    }

    private Map<String, Object> semanticResponse(Object id, JsonNode request, BoundCloudPivotConnection connection, JsonNode arguments, String externalPrincipal, McpSseSessionRegistry.Session session) {
        String progressToken = text(request.path("params").path("_meta"), "progressToken");
        McpProgressListener progress = new McpProgressListener(sseSessionRegistry, session, progressToken);
        TaskExperienceEnvelope result = semanticTaskService.handle(
            connection,
            text(arguments, "request"),
            textList(arguments.path("context")),
            text(arguments, "clientRequestId"),
            text(arguments, "conversationId"),
            text(arguments, "turnId"),
            parseTaskSpec(arguments.path("taskSpec"), text(arguments, "request"), text(arguments, "conversationId"), text(arguments, "turnId"), text(arguments, "clientRequestId")),
            hasText(externalPrincipal) ? externalPrincipal : text(arguments, "externalPrincipal"),
            progress::publish
        );
        return success(id, toolResult(result, experienceRenderer.markdown(result)));
    }

    private List<Map<String, Object>> tools() {
        Map<String, Object> schema = taskExperienceInputSchema();
        Map<String, Object> output = taskExperienceOutputSchema();
        return List.of(
            tool(
                "yunshu_handle_request",
                "兼容旧客户端的云枢领域委派入口，也是 CPClaw 云枢唯一业务工具。推荐新客户端使用 cpclaw_cloudpivot_agent；两者共享同一轮幂等、证据契约和执行边界。不能在本轮自行补查字段；不要在请求中填写 schemaCode、apiCode 或内部字段编码。",
                schema, output),
            tool(
                "cpclaw_cloudpivot_agent",
                "CPClaw 云枢领域委派入口，也是唯一业务工具。OpenClaw 传入用户目标以及可选的 taskSpec 交付要求；CPClaw 在服务端完成云枢元数据核验、数据准备、关联、证据覆盖和风险信号整理。OpenClaw 仍负责最终跨领域思考与自然语言表达，但不得修改 structuredContent.evidence 中的事实、数字、口径、范围或警告。仅在 completion 明确 needs_input、confirmation_required 或用户新增约束时继续调用；不得因为表达不够漂亮而在同一 turn 重复访问云枢。不得要求、猜测或调用 schemaCode、apiCode、字段编码等内部技术标识。",
                schema, output)
        );
    }

    private Map<String, Object> taskExperienceInputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("request"),
            "properties", Map.of(
                "request", Map.of("type", "string", "description", "用户的自然语言业务目标。不要填写 schemaCode、apiCode 或内部字段编码。"),
                "context", Map.of("type", "array", "items", Map.of("type", "string"), "description", "最多 6 条与当前任务直接相关、且已获用户同意的自然语言上下文摘要。"),
                "clientRequestId", Map.of("type", "string", "description", "同一用户轮次的稳定请求标识。重试时必须复用，用于避免重复任务。"),
                "turnId", Map.of("type", "string", "description", "OpenClaw 当前轮次的稳定标识。同一轮重复调用不会再次执行云枢。"),
                "externalPrincipal", Map.of("type", "string", "description", "可选的可信宿主用户标识；缺省使用 CPClaw 默认用户 huangj。"),
                "conversationId", Map.of("type", "string", "description", "宿主提供的稳定会话标识。仅用于隔离多轮任务上下文和幂等，不作为云枢字段或业务参数。"),
                "taskSpec", Map.of("type", "object", "description", "可选的领域委派规格，声明目标交付物和验收要求；不填写云枢内部编码。"),
                "presentation", Map.of("type", "object", "description", "结果呈现偏好，例如 summary_table_chart。CPClaw 始终同时返回可直接展示的 Markdown。")
            )
        );
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema, Map<String, Object> outputSchema) {
        return Map.of("name", name, "description", description, "inputSchema", inputSchema, "outputSchema", outputSchema);
    }

    private Map<String, Object> taskExperienceOutputSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("experienceVersion", "task", "completion", "evidence", "hostAction", "output"),
            "properties", Map.of(
                "experienceVersion", Map.of("type", "string"),
                "task", Map.of("type", "object"),
                "completion", Map.of("type", "object"),
                "evidence", Map.of("type", "object"),
                "hostAction", Map.of(
                    "type", "object",
                    "required", List.of("type", "allowAnotherMcpCallThisTurn"),
                    "properties", Map.of(
                        "type", Map.of("enum", List.of("compose_answer", "respond_directly", "ask_user", "open_cpclaw_confirmation", "report_failure")),
                        "allowAnotherMcpCallThisTurn", Map.of("type", "boolean")
                    )
                ),
                "output", Map.of(
                    "type", "object",
                    "required", List.of("message"),
                    "properties", Map.of("message", Map.of("type", "string"), "artifact", Map.of("type", "object"))
                )
            )
        );
    }

    private Map<String, Object> toolResult(Object value, String text) {
        Object structured = value;
        if (value instanceof TaskExperienceEnvelope envelope) {
            Map<String, Object> compatibility = new LinkedHashMap<>();
            compatibility.put("experienceVersion", envelope.experienceVersion());
            compatibility.put("task", envelope.task());
            compatibility.put("status", envelope.task().status());
            compatibility.put("summary", envelope.summary());
            compatibility.put("visibleTrace", envelope.visibleTrace());
            compatibility.put("output", envelope.output());
            compatibility.put("interaction", envelope.interaction());
            compatibility.put("hostAction", envelope.hostAction());
            compatibility.put("completion", envelope.completion());
            compatibility.put("evidence", envelope.evidence());
            compatibility.put("continuation", envelope.continuation());
            compatibility.put("result", envelope.output().get("result"));
            compatibility.put("understandingSummary", envelope.output().get("message"));
            structured = compatibility;
        }
        return Map.of("content", List.of(Map.of("type", "text", "text", text)), "structuredContent", structured);
    }

    private TaskSpec parseTaskSpec(JsonNode node, String request, String conversationId, String turnId, String clientRequestId) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return TaskSpec.empty(request, conversationId, turnId, clientRequestId);
        }
        try {
            TaskSpec parsed = objectMapper.treeToValue(node, TaskSpec.class);
            return new TaskSpec(parsed.protocolVersion(), parsed.goal().isBlank() ? request : parsed.goal(), parsed.deliverables(), parsed.constraints(), parsed.contextRefs(), parsed.presentationMode(), conversationId, turnId, clientRequestId, parsed.continuationToken());
        } catch (Exception ignored) {
            return TaskSpec.empty(request, conversationId, turnId, clientRequestId);
        }
    }

    private TaskSpec withContinuationToken(TaskSpec spec, String token) {
        if (spec == null) return TaskSpec.empty("继续上一任务", "", "", "");
        return new TaskSpec(spec.protocolVersion(), spec.goal(), spec.deliverables(), spec.constraints(), spec.contextRefs(),
            spec.presentationMode(), spec.conversationId(), spec.turnId(), spec.clientRequestId(), token);
    }

    private Map<String, Object> success(Object id, Object result) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", result); }
    private Map<String, Object> error(Object id, int code, String message) { return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", Map.of("code", code, "message", message)); }
    private String text(JsonNode node, String field) { return node == null || node.isMissingNode() ? "" : node.path(field).asText("").trim(); }
    private List<String> textList(JsonNode node) { if (node == null || !node.isArray()) return List.of(); List<String> result = new ArrayList<>(); node.forEach(value -> { if (value.isTextual() && !value.asText().isBlank() && result.size() < 6) result.add(value.asText().trim()); }); return List.copyOf(result); }
    private Object jsonRpcId(JsonNode request) {
        JsonNode id = request == null ? null : request.get("id");
        if (id == null || id.isNull()) return "";
        if (id.isIntegralNumber()) return id.longValue();
        if (id.isFloatingPointNumber()) return id.decimalValue();
        return id.asText("");
    }
    @SuppressWarnings("unchecked")
    private String toolStatus(Map<String, Object> response, String toolName) {
        Object result = response.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            Object content = resultMap.get("structuredContent");
            if (content instanceof Map<?, ?> structured) {
                Object statusValue = structured.get("status");
                String status = statusValue == null ? "" : String.valueOf(statusValue);
                if ("confirmation_required".equals(status)) return "CONFIRMATION_REQUIRED";
                if ("clarification_required".equals(status) || "needs_input".equals(status)) return "NEEDS_INPUT";
                if ("partial".equals(status) || "completed_with_gaps".equals(status)) return "COMPLETED_WITH_GAPS";
                if ("failed".equals(status)) return "FAILED";
            }
            if (content instanceof TaskExperienceEnvelope envelope) {
                return switch (envelope.task().status()) {
                    case "confirmation_required" -> "CONFIRMATION_REQUIRED";
                    case "needs_input" -> "NEEDS_INPUT";
                    case "failed" -> "FAILED";
                    default -> "COMPLETED";
                };
            }
        }
        if (result instanceof TaskExperienceEnvelope envelope) {
            return switch (envelope.task().status()) {
                case "confirmation_required" -> "CONFIRMATION_REQUIRED";
                case "needs_input" -> "NEEDS_INPUT";
                case "failed" -> "FAILED";
                default -> "COMPLETED";
            };
        }
        return "SUCCEEDED";
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String safeId(String value) { return hasText(value) ? value : "<missing>"; }
}
