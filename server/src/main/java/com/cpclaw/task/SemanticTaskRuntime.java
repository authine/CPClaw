package com.cpclaw.task;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskSpec;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.task.entity.SemanticTaskEvent;
import com.cpclaw.task.entity.SemanticTaskRun;
import com.cpclaw.task.repository.SemanticTaskEventRepository;
import com.cpclaw.task.repository.SemanticTaskRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Channel-neutral lifecycle owner for semantic tasks. Transport adapters provide
 * an executor and receive only safe task events and a versioned envelope.
 */
@Service
public class SemanticTaskRuntime {
    private final SemanticTaskRunRepository runRepository;
    private final SemanticTaskEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final TaskContinuationTokenService continuationTokenService;
    private final TaskEvidencePlanner evidencePlanner;

    public SemanticTaskRuntime(SemanticTaskRunRepository runRepository, SemanticTaskEventRepository eventRepository, ObjectMapper objectMapper, TaskContinuationTokenService continuationTokenService, TaskEvidencePlanner evidencePlanner) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.continuationTokenService = continuationTokenService;
        this.evidencePlanner = evidencePlanner;
    }

    @Transactional
    public TaskExperienceEnvelope execute(
        SemanticTaskRequest request,
        TaskExecutor executor,
        Consumer<TaskProgressEvent> downstream
    ) {
        ContinuationContext continuation = resolveContinuation(request);
        if (continuation.blocked() != null) return continuation.blocked();
        SemanticTaskRequest effectiveRequest = continuation.request();
        boolean continuationRequest = request.taskSpec() != null && request.taskSpec().continuationToken() != null && !request.taskSpec().continuationToken().isBlank();
        TaskExperienceEnvelope duplicate = continuationRequest ? null : readDuplicate(request);
        if (duplicate != null) return duplicate;
        Instant now = Instant.now();
        SemanticTaskRun run = new SemanticTaskRun();
        run.setId(UUID.randomUUID().toString());
        run.setChannel(effectiveRequest.channel());
        run.setInstallationKey(limit(effectiveRequest.installationId(), 64));
        run.setExternalPrincipal(limit(effectiveRequest.externalPrincipal(), 255));
        run.setClientRequestId(effectiveRequest.clientRequestId().isBlank() ? null : limit(effectiveRequest.clientRequestId(), 128));
        run.setTurnId(effectiveRequest.turnId().isBlank() ? null : limit(effectiveRequest.turnId(), 128));
        run.setParentTaskId(continuation.parentTaskId());
        run.setContinuationConsumed(false);
        run.setStatus("running");
        run.setRequestMasked(mask(limit(effectiveRequest.userGoal(), 2000)));
        run.setTaskSpecJson(write(effectiveRequest.taskSpec()));
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        try {
            runRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException conflict) {
            TaskExperienceEnvelope replay = continuationRequest ? null : readDuplicate(request);
            if (replay != null) return replay;
            throw conflict;
        }

        List<TaskProgressEvent> trace = new ArrayList<>();
        Consumer<TaskProgressEvent> eventSink = event -> {
            TaskProgressEvent safeEvent = sanitize(event);
            trace.add(safeEvent);
            persistEvent(run.getId(), trace.size(), safeEvent);
            try {
                if (downstream != null) downstream.accept(safeEvent);
            } catch (RuntimeException ignored) {
                // Progress is an optional transport enhancement. Never fail a task because a client disconnected.
            }
        };
        Map<String, Object> raw;
        try {
            raw = executor.execute(run.getId(), eventSink);
        } catch (RuntimeException exception) {
            raw = Map.of("status", "failed", "understandingSummary", "任务执行失败，请稍后重试。", "error", "任务执行失败");
            eventSink.accept(new TaskProgressEvent(100, "verify", "停止任务", "执行异常已被安全处理。", "failed"));
        }
        TaskExperienceEnvelope envelope = toEnvelope(run.getId(), raw, trace, effectiveRequest);
        run.setStatus(envelope.task().status());
        run.setUpdatedAt(Instant.now());
        if (isTerminal(envelope.task().status())) run.setCompletedAt(run.getUpdatedAt());
        run.setResultJson(write(envelope));
        run.setCompletionJson(write(envelope.completion()));
        run.setEvidenceJson(write(envelope.evidence()));
        runRepository.save(run);
        return envelope;
    }

    /**
     * A continuation is a child task of the task that issued the ticket. The
     * signed token is checked against the persisted parent result and consumed
     * exactly once; arbitrary second MCP calls cannot create a new branch.
     */
    private ContinuationContext resolveContinuation(SemanticTaskRequest request) {
        String token = request.taskSpec() == null ? "" : request.taskSpec().continuationToken();
        if (token == null || token.isBlank()) return new ContinuationContext(request, "", null);
        TaskContinuationTokenService.Claims claims = continuationTokenService.verifyAndRead(token);
        if (claims == null || !safeEquals(claims.principal(), request.externalPrincipal())) {
            return new ContinuationContext(request, "", blockedEnvelope("续接票据无效、已过期或不属于当前用户。"));
        }
        SemanticTaskRun parent = runRepository.findLockedById(claims.taskId()).orElse(null);
        if (parent == null || parent.isContinuationConsumed()
            || !safeEquals(parent.getChannel(), request.channel())
            || !safeEquals(parent.getInstallationKey(), request.installationId())
            || !safeEquals(parent.getExternalPrincipal(), request.externalPrincipal())) {
            return new ContinuationContext(request, "", blockedEnvelope("续接票据已使用或原任务不存在，不能重复续接。"));
        }
        if (!request.clientRequestId().isBlank() && safeEquals(request.clientRequestId(), parent.getClientRequestId())) {
            return new ContinuationContext(request, "", blockedEnvelope("续接任务必须使用新的 clientRequestId，不能复用原轮次标识。"));
        }
        TaskExperienceEnvelope parentEnvelope = parent.getResultJson() == null ? null : read(parent.getResultJson());
        String persistedToken = parentEnvelope == null ? "" : text(parentEnvelope.continuation().get("token"));
        String parentStatus = parent.getStatus() == null ? "" : parent.getStatus();
        if (!safeEquals(token, persistedToken) || !("needs_input".equals(parentStatus) || "confirmation_required".equals(parentStatus))) {
            return new ContinuationContext(request, "", blockedEnvelope("当前任务不处于可续接状态，或续接票据已失效。"));
        }
        if (runRepository.consumeContinuation(parent.getId()) != 1) {
            return new ContinuationContext(request, "", blockedEnvelope("续接票据已使用或正在被其他请求消费。"));
        }
        return new ContinuationContext(restoreParentContext(request, parent), parent.getId(), null);
    }

    private SemanticTaskRequest restoreParentContext(SemanticTaskRequest request, SemanticTaskRun parent) {
        TaskSpec parentSpec = readTaskSpec(parent.getTaskSpecJson());
        if (parentSpec == null) return request;
        String currentGoal = request.userGoal().isBlank() ? "继续上一任务" : request.userGoal();
        String inheritedGoal = parentSpec.goal().isBlank() ? "" : "上一任务目标：" + parentSpec.goal();
        String goal = inheritedGoal.isBlank() ? currentGoal : inheritedGoal + "；用户补充：" + currentGoal;
        List<String> context = new ArrayList<>();
        context.addAll(parentSpec.contextRefs());
        context.addAll(request.context());
        TaskSpec current = request.taskSpec();
        TaskSpec merged = new TaskSpec(
            current.protocolVersion(), goal, current.deliverables().isEmpty() ? parentSpec.deliverables() : current.deliverables(),
            mergeMaps(parentSpec.constraints(), current.constraints()), context, current.presentationMode(),
            request.taskSpec().conversationId(), request.taskSpec().turnId(), request.taskSpec().clientRequestId(), ""
        );
        return new SemanticTaskRequest(request.channel(), request.installationId(), request.externalPrincipal(), request.clientRequestId(), request.turnId(), goal, context, merged);
    }

    private TaskSpec readTaskSpec(String value) {
        if (value == null || value.isBlank()) return null;
        try { return objectMapper.readValue(value, TaskSpec.class); } catch (JsonProcessingException exception) { return null; }
    }

    private Map<String, Object> mergeMaps(Map<String, Object> parent, Map<String, Object> current) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (parent != null) merged.putAll(parent);
        if (current != null) merged.putAll(current);
        return merged;
    }

    private TaskExperienceEnvelope blockedEnvelope(String message) {
        return new TaskExperienceEnvelope("2.0", new TaskExperienceEnvelope.Task("", "blocked", Instant.now(), false),
            Map.of("replayed", false), List.of(), Map.of("message", message, "error", "invalid_continuation"),
            Map.of("type", "none"), Map.of("type", "report_failure", "allowAnotherMcpCallThisTurn", false),
            Map.of("state", "blocked", "answerReady", false, "continuationAllowed", false), Map.of(), Map.of("allowed", false));
    }

    private boolean safeEquals(String left, String right) { return left == null ? right == null : left.equals(right); }

    private record ContinuationContext(SemanticTaskRequest request, String parentTaskId, TaskExperienceEnvelope blocked) { }

    private TaskExperienceEnvelope readDuplicate(SemanticTaskRequest request) {
        if (!request.clientRequestId().isBlank()) {
            TaskExperienceEnvelope duplicate = runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndClientRequestId(
                    request.channel(), request.installationId(), request.externalPrincipal(), request.clientRequestId())
                .map(this::replayOrInFlight)
                .orElse(null);
            if (duplicate != null) return duplicate;
        }
        if (!request.turnId().isBlank()) {
            return runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndTurnId(
                    request.channel(), request.installationId(), request.externalPrincipal(), request.turnId())
                .map(this::replayOrInFlight)
                .orElse(null);
        }
        return null;
    }

    private TaskExperienceEnvelope replayOrInFlight(SemanticTaskRun run) {
        if (run.getResultJson() != null && !run.getResultJson().isBlank()) return read(run.getResultJson());
        if ("running".equalsIgnoreCase(run.getStatus())) {
            Map<String, Object> completion = Map.of(
                "state", "blocked",
                "answerReady", false,
                "deliverables", Map.of(),
                "missingEvidence", List.of(Map.of("code", "turn_in_flight", "message", "同一轮任务正在执行，不能重复访问云枢。")),
                "continuationAllowed", false,
                "terminal", true
            );
            Map<String, Object> output = Map.of("message", "同一轮任务正在执行，已阻止重复访问云枢；请使用原任务结果或等待任务完成。", "error", "duplicate_turn_in_flight");
            return new TaskExperienceEnvelope("2.0", new TaskExperienceEnvelope.Task(run.getId(), "blocked", run.getUpdatedAt(), false), Map.of("replayed", true), List.of(), output, Map.of("type", "none"), Map.of("type", "report_failure", "allowAnotherMcpCallThisTurn", false), completion, Map.of(), Map.of("allowed", false));
        }
        return null;
    }

    private TaskExperienceEnvelope toEnvelope(String taskId, Map<String, Object> raw, List<TaskProgressEvent> trace, SemanticTaskRequest request) {
        com.cpclaw.task.dto.TaskSpec taskSpec = request.taskSpec();
        Map<String, Object> safe = sanitizeMap(raw == null ? Map.of() : raw);
        safe.remove("taskId");
        String legacyStatus = text(safe.remove("status"));
        String status = switch (legacyStatus) {
            case "clarification_required" -> "needs_input";
            case "confirmation_required", "completed", "completed_with_gaps", "partial", "needs_input", "blocked", "failed", "cancelled" -> legacyStatus;
            default -> "failed";
        };
        String answer = text(safe.remove("understandingSummary"));
        Object rawTrace = safe.remove("executionTrace");
        List<TaskProgressEvent> finalTrace = trace.isEmpty() ? traceFrom(rawTrace) : List.copyOf(trace);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("intent", text(safe.get("intent")));
        summary.put("matchedObject", matchedName(safe.get("matchedMetadata")));
        summary.put("riskLevel", "confirmation_required".equals(status) ? "WRITE" : "READ");
        if (!legacyStatus.equals(status)) summary.put("legacyStatus", legacyStatus);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("message", answer);
        if (safe.containsKey("result")) output.put("result", safe.get("result"));
        if (safe.containsKey("artifact")) output.put("artifact", safe.get("artifact"));
        if (safe.containsKey("dataRange")) output.put("dataRange", safe.get("dataRange"));
        if (safe.containsKey("followUps")) output.put("followUps", safe.get("followUps"));
        if (safe.containsKey("error")) output.put("error", safe.get("error"));
        Map<String, Object> completion = mapValue(safe.get("completion"));
        if (completion.isEmpty()) completion = evidencePlanner.evaluate(taskSpec, status, safe);
        String completionState = text(completion.get("state"));
        if ("completed".equals(status) && "partial".equals(completionState)) status = "partial";
        Map<String, Object> interaction = switch (status) {
            case "needs_input" -> Map.of("type", "clarify", "prompt", text(safe.getOrDefault("question", answer)));
            case "confirmation_required" -> Map.of("type", "confirm", "prompt", answer, "executionBlocked", true, "channel", "cpclaw_authenticated_ui");
            default -> Map.of("type", "none");
        };
        Map<String, Object> hostAction = switch (status) {
            case "completed", "completed_with_gaps", "partial" -> Map.of("type", "compose_answer", "allowAnotherMcpCallThisTurn", false);
            case "needs_input" -> Map.of("type", "ask_user", "allowAnotherMcpCallThisTurn", false);
            case "confirmation_required" -> Map.of("type", "open_cpclaw_confirmation", "allowAnotherMcpCallThisTurn", false);
            default -> Map.of("type", "report_failure", "allowAnotherMcpCallThisTurn", false);
        };
        Map<String, Object> evidence = mapValue(safe.get("evidence"));
        if (evidence.isEmpty()) evidence = deriveEvidence(safe, taskSpec);
        Map<String, Object> continuation = mapValue(safe.get("continuation"));
        if (continuation.isEmpty() && ("needs_input".equals(status) || "confirmation_required".equals(status))) {
            continuation = Map.of("allowed", true, "taskId", taskId, "expiresInSeconds", continuationTokenService.ttlSeconds(), "token", continuationTokenService.issue(taskId, request.externalPrincipal()));
        }
        return new TaskExperienceEnvelope("2.0", new TaskExperienceEnvelope.Task(taskId, status, Instant.now(), "failed".equals(status) && Boolean.parseBoolean(text(safe.get("retryable")))), summary, finalTrace, output, interaction, hostAction, completion, evidence, continuation);
    }

    private Map<String, Object> deriveCompletion(com.cpclaw.task.dto.TaskSpec taskSpec, String status, Map<String, Object> safe) {
        Map<String, String> deliverables = new LinkedHashMap<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        if (taskSpec != null && !taskSpec.deliverables().isEmpty()) {
            for (com.cpclaw.task.dto.TaskDeliverable item : taskSpec.deliverables()) {
                String state = "completed".equals(status) && (safe.containsKey("result") || safe.containsKey("artifact")) ? "unverified" : "unavailable";
                deliverables.put(item.id(), state);
                missing.add(Map.of("deliverableId", item.id(), "reason", "执行器尚未返回逐项证据完成度"));
            }
        }
        String state = taskSpec != null && !taskSpec.deliverables().isEmpty() && "completed".equals(status) ? "partial" : status;
        boolean ready = "completed".equals(state) || "completed_with_gaps".equals(state) || "partial".equals(state);
        return Map.of("state", state, "answerReady", ready, "deliverables", deliverables, "missingEvidence", missing, "continuationAllowed", false, "terminal", true);
    }

    private Map<String, Object> deriveEvidence(Map<String, Object> safe, com.cpclaw.task.dto.TaskSpec taskSpec) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        Object dataRange = safe.get("dataRange");
        evidence.put("scope", dataRange instanceof Map<?, ?> ? dataRange : "当前绑定账号可见范围");
        Object result = safe.get("result");
        if (result instanceof Map<?, ?> resultMap) {
            Object records = resultMap.get("records");
            if (records instanceof List<?> list) evidence.put("records", list);
            Object total = resultMap.get("total");
            if (total != null) evidence.put("metrics", List.of(Map.of("name", "total", "value", total)));
        }
        List<String> caveats = new ArrayList<>();
        if (taskSpec != null && !taskSpec.deliverables().isEmpty()) caveats.add("当前结果未声明逐项交付物证据覆盖，不能据此推断复合任务已完整完成。");
        evidence.put("caveats", caveats);
        evidence.put("facts", List.of());
        evidence.put("riskSignals", List.of());
        evidence.put("relations", List.of());
        evidence.put("coverage", Map.of());
        evidence.put("provenance", List.of("cpclaw-semantic-task-runtime"));
        return evidence;
    }

    private boolean isTerminal(String status) {
        return switch (status) {
            case "completed", "completed_with_gaps", "partial", "needs_input", "blocked", "confirmation_required", "failed", "cancelled" -> true;
            default -> false;
        };
    }

    private void persistEvent(String taskId, int sequence, TaskProgressEvent event) {
        SemanticTaskEvent stored = new SemanticTaskEvent();
        stored.setId(UUID.randomUUID().toString());
        stored.setTaskId(taskId);
        stored.setEventSequence(sequence);
        stored.setEventJson(write(event));
        stored.setCreatedAt(Instant.now());
        eventRepository.save(stored);
    }

    private List<TaskProgressEvent> traceFrom(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<TaskProgressEvent> result = new ArrayList<>();
        for (Object item : values) {
            if (item instanceof Map<?, ?> map) {
                result.add(sanitize(new TaskProgressEvent(number(map.get("percent")), text(map.get("phase")), text(map.get("title")), text(map.get("message")), text(map.get("state")))));
            }
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : value.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            if (key.matches("(?i).*(password|token|secret|authorization|schemacode|apicode|endpoint|baseurl).*$")) continue;
            Object item = entry.getValue();
            if (item instanceof Map<?, ?> map) result.put(key, sanitizeMap((Map<String, Object>) map));
            else if (item instanceof List<?> list) result.put(key, list.stream().map(entryValue -> entryValue instanceof Map<?, ?> map ? sanitizeMap((Map<String, Object>) map) : entryValue).toList());
            else result.put(key, item);
        }
        return result;
    }

    private TaskProgressEvent sanitize(TaskProgressEvent event) {
        return new TaskProgressEvent(event.progress(), event.phase(), event.title(), mask(event.message()), event.state());
    }
    private String matchedName(Object value) { return value instanceof Map<?, ?> map ? text(map.get("objectName")) : ""; }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException exception) { return "{}"; } }
    private TaskExperienceEnvelope read(String value) { try { return objectMapper.readValue(value, TaskExperienceEnvelope.class); } catch (JsonProcessingException exception) { return null; } }
    private int number(Object value) { try { return Integer.parseInt(text(value)); } catch (NumberFormatException exception) { return 0; } }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    private String limit(String value, int length) { return value == null ? "" : value.length() <= length ? value : value.substring(0, length); }
    private String mask(String value) { return value == null ? "" : value.replaceAll("(?i)(password|token|secret|authorization)\\s*[:=]\\s*[^,\\s]+", "$1=***"); }

    @FunctionalInterface
    public interface TaskExecutor { Map<String, Object> execute(String taskId, Consumer<TaskProgressEvent> progress); }
}
