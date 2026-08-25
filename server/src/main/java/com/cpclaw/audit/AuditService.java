package com.cpclaw.audit;

import com.cpclaw.audit.entity.AgentRun;
import com.cpclaw.audit.entity.AgentModelCall;
import com.cpclaw.audit.entity.Confirmation;
import com.cpclaw.audit.entity.ToolCall;
import com.cpclaw.audit.repository.AgentRunRepository;
import com.cpclaw.audit.repository.AgentModelCallRepository;
import com.cpclaw.audit.repository.ConfirmationRepository;
import com.cpclaw.audit.repository.ToolCallRepository;
import com.cpclaw.model.TokenUsage;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final int MAX_AUDIT_TEXT_LENGTH = 20000;
    private static final ZoneId ANALYTICS_ZONE = ZoneId.of("Asia/Shanghai");

    private final AgentRunRepository agentRunRepository;
    private final AgentModelCallRepository agentModelCallRepository;
    private final ToolCallRepository toolCallRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ConfirmationRepository confirmationRepository;
    private final ConfirmedOperationExecutor confirmedOperationExecutor;
    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;

    public AuditService(
        AgentRunRepository agentRunRepository,
        AgentModelCallRepository agentModelCallRepository,
        ToolCallRepository toolCallRepository,
        ModelConfigRepository modelConfigRepository,
        ConfirmationRepository confirmationRepository,
        ConfirmedOperationExecutor confirmedOperationExecutor,
        SensitiveDataMasker masker,
        ObjectMapper objectMapper
    ) {
        this.agentRunRepository = agentRunRepository;
        this.agentModelCallRepository = agentModelCallRepository;
        this.toolCallRepository = toolCallRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.confirmationRepository = confirmationRepository;
        this.confirmedOperationExecutor = confirmedOperationExecutor;
        this.masker = masker;
        this.objectMapper = objectMapper;
    }

    public AgentRun createAgentRun(String conversationId, String userMessageId, String intent, String riskLevel, String planJson) {
        Instant now = Instant.now();
        AgentRun run = new AgentRun();
        run.setId(UUID.randomUUID().toString());
        run.setConversationId(conversationId);
        run.setUserMessageId(userMessageId);
        run.setIntentSummary(intent);
        run.setRiskLevel(riskLevel);
        run.setStatus("running");
        run.setToolCallCount(0);
        run.setPlanJson(maskAndTruncate(planJson));
        run.setReflectionJson("{\"status\":\"mvp-reflection-placeholder\"}");
        run.setCreatedAt(now);
        return agentRunRepository.save(run);
    }

    public AgentRun updateReflection(String agentRunId, String reflectionJson) {
        AgentRun run = agentRunRepository.findById(agentRunId)
            .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        run.setReflectionJson(maskAndTruncate(reflectionJson));
        run.setCompletedAt(Instant.now());
        return agentRunRepository.save(run);
    }

    /** Persists an audit enrichment produced after the Agent has completed task understanding. */
    public AgentRun saveAgentRun(AgentRun run) {
        return agentRunRepository.save(run);
    }

    public void updateExecutionStatus(String agentRunId, String status) {
        if (agentRunId == null || agentRunId.isBlank() || status == null || status.isBlank()) {
            return;
        }
        agentRunRepository.findById(agentRunId).ifPresent(run -> {
            run.setStatus(status);
            agentRunRepository.save(run);
        });
    }

    public ToolCall recordToolCall(String agentRunId, String toolName, String inputJson, String outputJson) {
        Instant now = Instant.now();
        ToolCall toolCall = new ToolCall();
        toolCall.setId(UUID.randomUUID().toString());
        toolCall.setAgentRunId(agentRunId);
        toolCall.setToolName(toolName);
        toolCall.setInputJsonMasked(maskAndTruncate(inputJson));
        toolCall.setOutputJsonMasked(maskAndTruncate(outputJson));
        toolCall.setStatus("completed");
        toolCall.setCreatedAt(now);
        toolCall.setCompletedAt(now);
        ToolCall saved = toolCallRepository.save(toolCall);
        agentRunRepository.findById(agentRunId).ifPresent(run -> {
            run.setToolCallCount(toolCallRepository.countByAgentRunId(agentRunId));
            agentRunRepository.save(run);
        });
        return saved;
    }

    /** Finalizes the single user-visible Agent execution with safe analytics fields. */
    public void finalizeAgentRun(
        String agentRunId,
        String assistantMessageId,
        String modelConfigId,
        String input,
        String output,
        TokenUsage tokenUsage,
        long durationMs,
        String status
    ) {
        if (agentRunId == null || agentRunId.isBlank()) {
            return;
        }
        agentRunRepository.findById(agentRunId).ifPresent(run -> {
            Instant now = Instant.now();
            TokenUsage usage = tokenUsage == null ? TokenUsage.empty() : tokenUsage;
            boolean usageRecorded = !usage.isEmpty();
            run.setAssistantMessageId(assistantMessageId);
            run.setModelConfigId(modelConfigId);
            run.setInputSummaryMasked(summary(input));
            run.setOutputSummaryMasked(summary(output));
            run.setPromptTokens(usageRecorded ? usage.promptTokens() : null);
            run.setCompletionTokens(usageRecorded ? usage.completionTokens() : null);
            run.setCachedTokens(usageRecorded ? usage.cachedTokens() : null);
            run.setTotalTokens(usageRecorded ? usage.totalTokens() : null);
            run.setDurationMs(Math.max(0L, durationMs));
            run.setToolCallCount(toolCallRepository.countByAgentRunId(agentRunId));
            run.setStatus(status == null || status.isBlank() ? nonBlank(run.getStatus(), "completed") : status);
            run.setCompletedAt(now);
            agentRunRepository.save(run);

            AgentModelCall call = new AgentModelCall();
            call.setId(UUID.randomUUID().toString());
            call.setAgentRunId(agentRunId);
            call.setModelConfigId(modelConfigId);
            call.setModelName(modelConfigRepository.findById(modelConfigId == null ? "" : modelConfigId)
                .map(model -> model.getName()).orElse("规则执行"));
            call.setOperation("agent_execution");
            call.setStatus(run.getStatus());
            call.setInputSummaryMasked(run.getInputSummaryMasked());
            call.setOutputSummaryMasked(run.getOutputSummaryMasked());
            call.setPromptTokens(run.getPromptTokens());
            call.setCompletionTokens(run.getCompletionTokens());
            call.setCachedTokens(run.getCachedTokens());
            call.setTotalTokens(run.getTotalTokens());
            call.setDurationMs(run.getDurationMs());
            call.setCreatedAt(run.getCreatedAt() == null ? now : run.getCreatedAt());
            call.setCompletedAt(now);
            agentModelCallRepository.save(call);
        });
    }

    public Confirmation createConfirmation(String conversationId, String agentRunId, String riskLevel, String summary, String changesJson) {
        Instant now = Instant.now();
        Confirmation confirmation = new Confirmation();
        confirmation.setId(UUID.randomUUID().toString());
        confirmation.setConversationId(conversationId);
        confirmation.setAgentRunId(agentRunId);
        confirmation.setPlanId(UUID.randomUUID().toString());
        confirmation.setRiskLevel(riskLevel);
        confirmation.setSummary(summary);
        confirmation.setAffectedObjectsJson("[]");
        confirmation.setChangesJsonMasked(maskAndTruncate(changesJson));
        confirmation.setPlanHash(planHash(confirmation.getChangesJsonMasked()));
        confirmation.setStatus("pending");
        confirmation.setCreatedAt(now);
        confirmation.setExpiresAt(now.plusSeconds(1800));
        return confirmationRepository.save(confirmation);
    }

    @Transactional
    public Map<String, Object> confirm(String confirmationId) {
        Confirmation confirmation = confirmationRepository.findByIdForExecution(confirmationId)
            .orElseThrow(() -> new IllegalArgumentException("Confirmation not found"));
        if (!"pending".equals(confirmation.getStatus())) {
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", confirmationStatusMessage(confirmation.getStatus())
            );
        }
        Instant now = Instant.now();
        if (confirmation.getExpiresAt() != null && !confirmation.getExpiresAt().isAfter(now)) {
            confirmation.setStatus("expired");
            confirmation = confirmationRepository.save(confirmation);
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", "确认单已过期，未执行云枢操作。请重新发起操作。"
            );
        }
        confirmation.setStatus("executing");
        confirmation.setConfirmedAt(now);
        confirmation.setExecutionStartedAt(now);
        confirmation = confirmationRepository.save(confirmation);
        ConfirmedOperationExecutor.ConfirmedOperationResult result;
        try {
            result = confirmedOperationExecutor.execute(confirmation);
        } catch (RuntimeException exception) {
            confirmation.setStatus("failed");
            confirmation = confirmationRepository.save(confirmation);
            return Map.of(
                "id", confirmation.getId(),
                "status", confirmation.getStatus(),
                "agentRunId", confirmation.getAgentRunId(),
                "executed", false,
                "message", exception.getMessage() == null ? "云枢操作执行失败" : exception.getMessage()
            );
        }
        if (result.executed()) {
            recordToolCall(
                confirmation.getAgentRunId(),
                "cloudpivot_runtime_delete",
                confirmation.getChangesJsonMasked(),
                toJson(result.output())
            );
            confirmation.setStatus("executed");
            confirmation = confirmationRepository.save(confirmation);
        } else if ("unsupported".equals(result.operation())) {
            confirmation.setStatus("confirmed");
            confirmation = confirmationRepository.save(confirmation);
        } else if (!"unsupported".equals(result.operation())) {
            confirmation.setStatus("failed");
            confirmation = confirmationRepository.save(confirmation);
        }
        return Map.of(
            "id", confirmation.getId(),
            "status", confirmation.getStatus(),
            "agentRunId", confirmation.getAgentRunId(),
            "executed", result.executed(),
            "message", result.message()
        );
    }

    private String confirmationStatusMessage(String status) {
        return switch (status == null ? "" : status) {
            case "executed" -> "该确认单已经执行过，本次不会重复执行。";
            case "confirmed", "executing" -> "该确认单已经确认或正在执行，本次不会重复执行。";
            case "expired" -> "该确认单已过期，未执行云枢操作。";
            case "failed" -> "该确认单此前执行失败，请重新发起操作。";
            default -> "该确认单当前状态不允许再次确认。";
        };
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String planHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public Map<String, Object> getAgentRunPlaceholder(String id) {
        AgentRun run = agentRunRepository.findById(id).orElse(null);
        if (run == null) {
            return Map.of("id", id, "status", "not-found");
        }
        List<Map<String, Object>> tools = toolCallRepository.findByAgentRunIdOrderByCreatedAtAsc(id).stream()
            .map(tool -> Map.<String, Object>of(
                "id", tool.getId(),
                "toolName", tool.getToolName(),
                "status", tool.getStatus(),
                "inputJsonMasked", tool.getInputJsonMasked(),
                "outputJsonMasked", tool.getOutputJsonMasked()
            ))
            .toList();
        return Map.of(
            "id", run.getId(),
            "conversationId", run.getConversationId() == null ? "" : run.getConversationId(),
            "intent", run.getIntentSummary() == null ? "" : run.getIntentSummary(),
            "businessIntent", effectiveBusinessIntent(run),
            "riskLevel", run.getRiskLevel() == null ? "low" : run.getRiskLevel(),
            "status", run.getStatus(),
            "planJson", run.getPlanJson() == null ? "{}" : run.getPlanJson(),
            "reflectionJson", run.getReflectionJson() == null ? "{}" : run.getReflectionJson(),
            "tools", tools
        );
    }

    private String maskAndTruncate(String value) {
        String masked = masker.mask(value);
        if (masked == null || masked.length() <= MAX_AUDIT_TEXT_LENGTH) {
            return masked;
        }
        return masked.substring(0, MAX_AUDIT_TEXT_LENGTH) + "...[truncated]";
    }

    /** Returns paginated, masked operational analytics for the system settings workbench. */
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics(
        Instant from,
        Instant to,
        String status,
        String intent,
        String modelConfigId,
        int page,
        int size
    ) {
        List<AgentRun> scoped = agentRunRepository.findAll().stream()
            .filter(run -> from == null || (run.getCreatedAt() != null && !run.getCreatedAt().isBefore(from)))
            .filter(run -> to == null || (run.getCreatedAt() != null && !run.getCreatedAt().isAfter(to)))
            .filter(run -> matches(run.getStatus(), status))
            .filter(run -> matches(run.getModelConfigId(), modelConfigId))
            .toList();
        List<AgentRun> filtered = scoped.stream()
            .filter(run -> matchesIntent(run, intent))
            .sorted(Comparator.comparing(AgentRun::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        Map<String, String> modelNames = new LinkedHashMap<>();
        modelConfigRepository.findAll().forEach(model -> modelNames.put(model.getId(), model.getName()));
        Map<String, UsageSnapshot> modelCallUsage = agentModelCallRepository.findAll().stream()
            .filter(call -> call.getAgentRunId() != null && !call.getAgentRunId().isBlank())
            .collect(java.util.stream.Collectors.groupingBy(
                AgentModelCall::getAgentRunId,
                LinkedHashMap::new,
                java.util.stream.Collectors.collectingAndThen(
                    java.util.stream.Collectors.toList(),
                    this::aggregateModelCalls
                )
            ));
        long successes = filtered.stream().filter(run -> !isFailure(run.getStatus())).count();
        long promptTokens = filtered.stream().map(run -> effectiveUsage(run, modelCallUsage)).mapToLong(UsageSnapshot::promptTokens).sum();
        long completionTokens = filtered.stream().map(run -> effectiveUsage(run, modelCallUsage)).mapToLong(UsageSnapshot::completionTokens).sum();
        long cachedTokens = filtered.stream().map(run -> effectiveUsage(run, modelCallUsage)).mapToLong(UsageSnapshot::cachedTokens).sum();
        long totalTokens = filtered.stream().map(run -> effectiveUsage(run, modelCallUsage)).mapToLong(UsageSnapshot::totalTokens).sum();
        List<Long> durations = filtered.stream().map(AgentRun::getDurationMs).filter(java.util.Objects::nonNull).toList();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        int start = Math.min((safePage - 1) * safeSize, filtered.size());
        int end = Math.min(start + safeSize, filtered.size());
        List<Map<String, Object>> items = filtered.subList(start, end).stream()
            .map(run -> analyticsItem(
                run,
                modelNames.getOrDefault(run.getModelConfigId(), modelCallUsage.containsKey(run.getId()) ? modelCallUsage.get(run.getId()).modelName() : "规则执行"),
                effectiveUsage(run, modelCallUsage)
            ))
            .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("invocations", filtered.size());
        summary.put("successes", successes);
        summary.put("failures", filtered.size() - successes);
        summary.put("promptTokens", promptTokens);
        summary.put("completionTokens", completionTokens);
        summary.put("cachedTokens", cachedTokens);
        summary.put("totalTokens", totalTokens);
        summary.put("usageRecorded", filtered.stream().filter(run -> effectiveUsage(run, modelCallUsage).recorded()).count());
        summary.put("averageLatencyMs", durations.isEmpty() ? null : durations.stream().mapToLong(Long::longValue).average().orElse(0D));
        return Map.of("summary", summary, "items", items, "page", safePage, "size", safeSize, "total", filtered.size());
    }

    /**
     * Aggregates recorded model usage for the operating dashboard. Calls that
     * lack a provider usage payload still contribute to invocation counts, but
     * never receive an estimated Token value.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageDashboard(Instant from, Instant to, String status, String modelConfigId) {
        List<AgentRun> runs = agentRunRepository.findAll().stream()
            .filter(run -> from == null || (run.getCreatedAt() != null && !run.getCreatedAt().isBefore(from)))
            .filter(run -> to == null || (run.getCreatedAt() != null && !run.getCreatedAt().isAfter(to)))
            .filter(run -> matches(run.getStatus(), status))
            .filter(run -> matches(run.getModelConfigId(), modelConfigId))
            .toList();
        Map<String, String> modelNames = modelNames();
        Map<String, UsageSnapshot> modelCallUsage = modelCallUsage();
        Map<String, TokenBucket> daily = new LinkedHashMap<>();
        Map<String, TokenBucket> weekly = new LinkedHashMap<>();
        Map<String, TokenBucket> monthly = new LinkedHashMap<>();
        Map<String, TokenBucket> models = new LinkedHashMap<>();
        for (AgentRun run : runs) {
            UsageSnapshot usage = effectiveUsage(run, modelCallUsage);
            String modelName = modelNames.getOrDefault(run.getModelConfigId(), modelCallUsage.containsKey(run.getId()) && !modelCallUsage.get(run.getId()).modelName().isBlank()
                ? modelCallUsage.get(run.getId()).modelName() : "规则执行");
            models.computeIfAbsent(modelName, ignored -> new TokenBucket()).add(usage);
            if (run.getCreatedAt() == null) continue;
            java.time.LocalDate date = run.getCreatedAt().atZone(ANALYTICS_ZONE).toLocalDate();
            WeekFields weekFields = WeekFields.ISO;
            int weekYear = date.get(weekFields.weekBasedYear());
            int week = date.get(weekFields.weekOfWeekBasedYear());
            String dayKey = date.toString();
            String weekKey = "%04d-W%02d".formatted(weekYear, week);
            String monthKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            daily.computeIfAbsent(dayKey, ignored -> new TokenBucket()).add(usage);
            weekly.computeIfAbsent(weekKey, ignored -> new TokenBucket()).add(usage);
            monthly.computeIfAbsent(monthKey, ignored -> new TokenBucket()).add(usage);
        }
        TokenBucket total = new TokenBucket();
        runs.forEach(run -> total.add(effectiveUsage(run, modelCallUsage)));
        return Map.of(
            "summary", total.toMap(),
            "daily", timeSeries(daily, "day"),
            "weekly", timeSeries(weekly, "week"),
            "monthly", timeSeries(monthly, "month"),
            "models", modelSeries(models)
        );
    }

    private Map<String, String> modelNames() {
        Map<String, String> values = new LinkedHashMap<>();
        modelConfigRepository.findAll().forEach(model -> values.put(model.getId(), model.getName()));
        return values;
    }

    private Map<String, UsageSnapshot> modelCallUsage() {
        return agentModelCallRepository.findAll().stream()
            .filter(call -> call.getAgentRunId() != null && !call.getAgentRunId().isBlank())
            .collect(java.util.stream.Collectors.groupingBy(
                AgentModelCall::getAgentRunId,
                LinkedHashMap::new,
                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), this::aggregateModelCalls)
            ));
    }

    private List<Map<String, Object>> timeSeries(Map<String, TokenBucket> buckets, String granularity) {
        return buckets.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            Map<String, Object> value = new LinkedHashMap<>(entry.getValue().toMap());
            value.put("period", entry.getKey());
            value.put("label", switch (granularity) {
                case "week" -> entry.getKey().replace("-W", " 第") + "周";
                case "month" -> entry.getKey() + " 月";
                default -> entry.getKey().substring(5).replace('-', '/');
            });
            return value;
        }).toList();
    }

    private List<Map<String, Object>> modelSeries(Map<String, TokenBucket> buckets) {
        return buckets.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String, TokenBucket>>comparingLong(entry -> entry.getValue().totalTokens).reversed()
                .thenComparing(Map.Entry::getKey))
            .map(entry -> {
                Map<String, Object> value = new LinkedHashMap<>(entry.getValue().toMap());
                value.put("modelName", entry.getKey());
                return value;
            }).toList();
    }

    private static final class TokenBucket {
        private long invocations;
        private long usageRecorded;
        private long promptTokens;
        private long completionTokens;
        private long cachedTokens;
        private long totalTokens;

        private void add(UsageSnapshot usage) {
            invocations++;
            if (usage.recorded()) usageRecorded++;
            promptTokens += usage.promptTokens();
            completionTokens += usage.completionTokens();
            cachedTokens += usage.cachedTokens();
            totalTokens += usage.totalTokens();
        }

        private Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("invocations", invocations);
            value.put("usageRecorded", usageRecorded);
            value.put("promptTokens", promptTokens);
            value.put("completionTokens", completionTokens);
            value.put("cachedTokens", cachedTokens);
            value.put("totalTokens", totalTokens);
            return value;
        }
    }

    private Map<String, Object> analyticsItem(AgentRun run, String modelName, UsageSnapshot usage) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", run.getId());
        item.put("createdAt", run.getCreatedAt() == null ? null : run.getCreatedAt().toString());
        item.put("modelName", modelName);
        item.put("intent", run.getIntentSummary());
        item.put("businessIntent", effectiveBusinessIntent(run));
        item.put("status", run.getStatus());
        item.put("inputSummary", run.getInputSummaryMasked());
        item.put("outputSummary", run.getOutputSummaryMasked());
        item.put("promptTokens", usage.recorded() ? usage.promptTokens() : null);
        item.put("completionTokens", usage.recorded() ? usage.completionTokens() : null);
        item.put("cachedTokens", usage.recorded() ? usage.cachedTokens() : null);
        item.put("totalTokens", usage.recorded() ? usage.totalTokens() : null);
        item.put("durationMs", run.getDurationMs());
        item.put("toolCallCount", run.getToolCallCount() == null ? 0 : run.getToolCallCount());
        return item;
    }

    private UsageSnapshot effectiveUsage(AgentRun run, Map<String, UsageSnapshot> modelCallUsage) {
        if (run.getTotalTokens() != null || run.getPromptTokens() != null || run.getCompletionTokens() != null || run.getCachedTokens() != null) {
            return new UsageSnapshot(
                safeLong(run.getPromptTokens()),
                safeLong(run.getCompletionTokens()),
                safeLong(run.getCachedTokens()),
                safeLong(run.getTotalTokens()),
                true,
                ""
            );
        }
        return modelCallUsage.getOrDefault(run.getId(), UsageSnapshot.empty());
    }

    private UsageSnapshot aggregateModelCalls(List<AgentModelCall> calls) {
        long prompt = 0;
        long completion = 0;
        long cached = 0;
        long total = 0;
        boolean recorded = false;
        String modelName = "";
        for (AgentModelCall call : calls) {
            if (call.getPromptTokens() != null || call.getCompletionTokens() != null || call.getCachedTokens() != null || call.getTotalTokens() != null) {
                recorded = true;
                prompt += safeLong(call.getPromptTokens());
                completion += safeLong(call.getCompletionTokens());
                cached += safeLong(call.getCachedTokens());
                total += safeLong(call.getTotalTokens());
            }
            if (modelName.isBlank() && call.getModelName() != null && !call.getModelName().isBlank()) {
                modelName = call.getModelName();
            }
        }
        return new UsageSnapshot(prompt, Math.max(completion, 0), Math.min(cached, prompt), Math.max(total, prompt + completion), recorded, modelName);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private record UsageSnapshot(long promptTokens, long completionTokens, long cachedTokens, long totalTokens, boolean recorded, String modelName) {
        private static UsageSnapshot empty() {
            return new UsageSnapshot(0, 0, 0, 0, false, "");
        }
    }

    private boolean matches(String value, String expected) {
        return expected == null || expected.isBlank() || expected.equalsIgnoreCase(value == null ? "" : value);
    }

    private boolean matchesIntent(AgentRun run, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String keyword = expected.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(effectiveBusinessIntent(run), keyword)
            || containsIgnoreCase(run.getInputSummaryMasked(), keyword)
            || containsIgnoreCase(run.getIntentSummary(), keyword);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /**
     * V11 records have a planner-provided business intent. Older records are read-only
     * compatible: a short display label is derived from their persisted plan instead of
     * presenting the internal execution enum as a business intent.
     */
    private String effectiveBusinessIntent(AgentRun run) {
        if (run.getBusinessIntent() != null && !run.getBusinessIntent().isBlank()) {
            return run.getBusinessIntent();
        }
        try {
            JsonNode think = objectMapper.readTree(run.getPlanJson() == null ? "{}" : run.getPlanJson()).path("think");
            String action = think.path("action").asText("").trim();
            String object = think.path("metadataObject").asText(think.path("businessObject").asText("")).trim();
            if (!action.isBlank() && !object.isBlank() && !"未明确".equals(action)) {
                String value = action + object;
                return value.length() > 120 ? value.substring(0, 120) : value;
            }
        } catch (JsonProcessingException ignored) {
            // Historic audit data remains readable even if an earlier plan is malformed.
        }
        return "历史任务（未标注）";
    }

    private boolean isFailure(String status) {
        String value = status == null ? "" : status.toLowerCase(java.util.Locale.ROOT);
        return value.contains("failed") || value.contains("blocked") || value.contains("cancelled");
    }

    private String summary(String value) {
        String masked = maskAndTruncate(value == null ? "" : value.replaceAll("\\s+", " ").trim());
        if (masked == null || masked.length() <= 280) {
            return masked;
        }
        return masked.substring(0, 280) + "...[已截断]";
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() || "running".equals(value) || "pending_confirmation".equals(value) ? fallback : value;
    }
}
