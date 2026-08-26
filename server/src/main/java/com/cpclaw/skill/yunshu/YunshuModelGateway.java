package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.credential.CredentialUnavailableException;
import com.cpclaw.model.ConversationRouteResult;
import com.cpclaw.model.IntentPlanningResult;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.model.ModelUsageContext;
import com.cpclaw.model.TokenUsage;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class YunshuModelGateway implements ModelGateway {

    private static final String OWNER_MODEL = "model_config";
    private static final String MODEL_API_KEY = "model_api_key";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    // Deep-reasoning providers can legitimately spend longer than a regular
    // response before returning a complete answer. The UI exposes cancellation,
    // so retain a bounded but usable server-side budget instead of fabricating a
    // greeting when a provider has not yet completed its reasoning pass.
    /**
     * A report may spend several minutes in provider-side reasoning. The web
     * stream emits independent progress heartbeats during this window, while
     * this limit still prevents an unbounded provider connection.
     */
    private static final Duration ANALYSIS_HARD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration STREAM_FIRST_OUTPUT_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration PLANNING_HARD_TIMEOUT = Duration.ofSeconds(5);
    private static final ScheduledExecutorService MODEL_TIMEOUT_SCHEDULER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cpclaw-model-stream-timeout");
        thread.setDaemon(true);
        return thread;
    });

    private final ModelConfigRepository modelConfigRepository;
    private final CredentialService credentialService;
    private final SensitiveDataMasker sensitiveDataMasker;
    private final ObjectMapper objectMapper;
    private final ModelUsageContext modelUsageContext;
    private final HttpClient httpClient;

    public YunshuModelGateway(
        ModelConfigRepository modelConfigRepository,
        CredentialService credentialService,
        SensitiveDataMasker sensitiveDataMasker,
        ObjectMapper objectMapper,
        ModelUsageContext modelUsageContext
    ) {
        this.modelConfigRepository = modelConfigRepository;
        this.credentialService = credentialService;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.objectMapper = objectMapper;
        this.modelUsageContext = modelUsageContext;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    @Override
    public Map<String, Object> testModel(String modelConfigId) {
        long startedAt = System.nanoTime();
        Optional<ModelConfig> model = modelConfigRepository.findById(modelConfigId)
            .filter(ModelConfig::isEnabled);
        if (model.isEmpty()) {
            return modelTestResult(false, "模型不存在或已停用", startedAt);
        }
        Optional<String> apiKey;
        try {
            apiKey = credentialService.revealCredential(OWNER_MODEL, model.get().getId(), MODEL_API_KEY);
        } catch (CredentialUnavailableException exception) {
            return modelTestResult(false, exception.getMessage(), startedAt);
        }
        if (apiKey.isEmpty()) {
            return modelTestResult(false, "模型未保存 API Key", startedAt);
        }
        return testUnsavedModel(model.get().getModelName(), model.get().getApiBaseUrl(), apiKey.get());
    }

    @Override
    public Map<String, Object> testUnsavedModel(String modelName, String apiBaseUrl, String apiKey) {
        long startedAt = System.nanoTime();
        if (modelName == null || modelName.isBlank() || apiBaseUrl == null || apiBaseUrl.isBlank() || apiKey == null || apiKey.isBlank()) {
            return modelTestResult(false, "请填写模型名称、API 地址和 API Key", startedAt);
        }
        if (isLocalTestUrl(apiBaseUrl)) {
            return modelTestResult(false, "本地模拟地址不执行真实模型验证，请配置可访问的模型服务地址", startedAt);
        }
        if (!isTestableModelEndpoint(apiBaseUrl)) {
            return modelTestResult(false, "模型地址必须是可公开访问的 http 或 https 服务地址", startedAt);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName.trim());
            body.put("temperature", 0);
            body.put("max_tokens", 1);
            body.put("messages", List.of(Map.of("role", "user", "content", "Reply with OK")));
            HttpRequest request = HttpRequest.newBuilder(URI.create(chatCompletionEndpoint(apiBaseUrl)))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(Duration.ofSeconds(12).toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                return modelTestResult(false, "模型服务拒绝访问，请检查 API Key 或权限", startedAt);
            }
            if (response.statusCode() == 404) {
                return modelTestResult(false, "模型服务地址或接口路径不可用", startedAt);
            }
            if (response.statusCode() == 429) {
                return modelTestResult(false, "模型服务限流，请稍后重试", startedAt);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return modelTestResult(false, "模型服务响应异常（HTTP " + response.statusCode() + "）", startedAt);
            }
            JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            return content.isTextual() && !content.asText().isBlank()
                ? modelTestResult(true, "模型连接验证通过", startedAt)
                : modelTestResult(false, "模型服务返回格式不兼容", startedAt);
        } catch (RuntimeException | IOException exception) {
            return modelTestResult(false, "模型服务连接失败或响应超时", startedAt);
        }
    }

    private Map<String, Object> modelTestResult(boolean success, String message, long startedAt) {
        long latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        return Map.of("success", success, "message", message, "latencyMs", latencyMs);
    }

    @Override
    public Optional<String> analyzeRecords(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled
    ) {
        return analyzeRecords(preferredModelConfigId, userQuestion, entityName, total, records, thinkingEnabled, Map.of());
    }

    @Override
    public Optional<String> analyzeRecords(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled,
        Map<String, Object> reasoningContext
    ) {
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        String safeUserQuestion = sensitiveDataMasker.mask(userQuestion);
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            return Optional.of(localAnalysis(safeUserQuestion, entityName, total, records));
        }
        Optional<String> apiKey = credentialService.revealCredential(OWNER_MODEL, modelConfig.get().getId(), MODEL_API_KEY);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }

        try {
            String endpoint = chatCompletionEndpoint(modelConfig.get().getApiBaseUrl());
            Map<String, Object> body = new LinkedHashMap<>();
            applyModelRequestOptions(body, modelConfig.get(), thinkingEnabled);
            body.put("model", modelConfig.get().getModelName());
            body.put("temperature", 0.2);
            body.put("messages", List.of(
                Map.of(
                    "role", "system",
                    "content", "你是企业经营数据分析助手。请基于用户问题和已查询到的云枢业务数据做推理分析，输出中文结论、关键发现、风险信号和下一步建议。不要编造未提供的数据。"
                ),
                Map.of(
                    "role", "user",
                    "content", analysisPrompt(safeUserQuestion, entityName, total, records, thinkingEnabled, reasoningContext)
                )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(ANALYSIS_HARD_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(ANALYSIS_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            recordUsage(root);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return Optional.of(content.asText().trim());
            }
            return Optional.empty();
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> analyzeRecordsStream(
        String preferredModelConfigId,
        String userQuestion,
        String entityName,
        long total,
        List<Map<String, Object>> records,
        boolean thinkingEnabled,
        Map<String, Object> reasoningContext,
        Consumer<String> chunkConsumer
    ) {
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        String safeUserQuestion = sensitiveDataMasker.mask(userQuestion);
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            String answer = localAnalysis(safeUserQuestion, entityName, total, records);
            emitReadableChunks(answer, chunkConsumer);
            return Optional.of(answer);
        }
        Optional<String> apiKey = credentialService.revealCredential(OWNER_MODEL, modelConfig.get().getId(), MODEL_API_KEY);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }

        try {
            String endpoint = chatCompletionEndpoint(modelConfig.get().getApiBaseUrl());
            Map<String, Object> body = new LinkedHashMap<>();
            applyModelRequestOptions(body, modelConfig.get(), thinkingEnabled);
            body.put("model", modelConfig.get().getModelName());
            body.put("temperature", 0.2);
            body.put("max_tokens", 1800);
            body.put("stream", true);
            body.put("stream_options", Map.of("include_usage", true));
            body.put("messages", List.of(
                Map.of(
                    "role", "system",
                    "content", "你是企业经营数据分析助手。请基于用户问题、云枢元数据和真实查询结果，用中文直接回答。先给结论，再给关键依据；不要重复字段，不要输出原始表结构，不要编造未提供的数据。最终正文不超过约1000个中文字符，保证结尾完整。"
                ),
                Map.of(
                    "role", "user",
                    "content", analysisPrompt(safeUserQuestion, entityName, total, records, thinkingEnabled, reasoningContext)
                )
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(ANALYSIS_HARD_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<InputStream> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .orTimeout(ANALYSIS_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                return Optional.empty();
            }
            return readStreamingAnswer(response.body(), chunkConsumer);
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> readStreamingAnswer(InputStream inputStream, Consumer<String> chunkConsumer) throws IOException {
        AtomicBoolean timedOut = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicBoolean truncated = new AtomicBoolean(false);
        AtomicBoolean doneSeen = new AtomicBoolean(false);
        AtomicBoolean receivedContent = new AtomicBoolean(false);
        ScheduledFuture<?> timeout = MODEL_TIMEOUT_SCHEDULER.schedule(() -> {
            timedOut.set(true);
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // Closing the stream interrupts a stalled provider response.
            }
        }, ANALYSIS_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        StringBuilder answer = new StringBuilder();
        AtomicReference<ScheduledFuture<?>> doneGrace = new AtomicReference<>();
        ScheduledFuture<?> firstOutputTimeout = MODEL_TIMEOUT_SCHEDULER.schedule(() -> {
            if (receivedContent.get()) {
                return;
            }
            timedOut.set(true);
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // The provider stream has already ended.
            }
        }, STREAM_FIRST_OUTPUT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    completed.set(true);
                    doneSeen.set(true);
                    // A few gateways send usage immediately after [DONE] but do
                    // not close the HTTP stream. Allow a short usage grace window,
                    // then close the provider stream instead of leaving the UI in
                    // a permanently running state.
                    doneGrace.set(MODEL_TIMEOUT_SCHEDULER.schedule(() -> {
                        try {
                            inputStream.close();
                        } catch (IOException ignored) {
                            // The reader is already finishing.
                        }
                    }, 750, TimeUnit.MILLISECONDS));
                    continue;
                }
                JsonNode root = objectMapper.readTree(data);
                recordUsage(root);
                JsonNode finishReason = root.path("choices").path(0).path("finish_reason");
                if (!finishReason.isMissingNode() && !finishReason.isNull() && !finishReason.asText().isBlank()) {
                    truncated.set("length".equalsIgnoreCase(finishReason.asText()));
                    completed.set(!truncated.get());
                }
                JsonNode content = root.path("choices").path(0).path("delta").path("content");
                if (!content.isTextual() || content.asText().isEmpty()) {
                    continue;
                }
                String chunk = content.asText();
                answer.append(chunk);
                receivedContent.set(true);
                if (chunkConsumer != null) {
                    chunkConsumer.accept(chunk);
                }
            }
        } catch (IOException exception) {
            if (!timedOut.get() && !doneSeen.get()) {
                throw exception;
            }
        } finally {
            timeout.cancel(false);
            firstOutputTimeout.cancel(false);
            ScheduledFuture<?> grace = doneGrace.get();
            if (grace != null) grace.cancel(false);
        }
        if (timedOut.get() || truncated.get() || !completed.get() || answer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(answer.toString().trim());
    }

    private void emitReadableChunks(String content, Consumer<String> chunkConsumer) {
        AnswerStreamSupport.emitReadableChunks(content, chunkConsumer);
    }

    @Override
    public Optional<IntentPlanningResult> planIntent(String preferredModelConfigId, Map<String, Object> planningContext, boolean thinkingEnabled) {
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            return Optional.of(localIntentPlan(planningContext));
        }
        Optional<String> apiKey = credentialService.revealCredential(OWNER_MODEL, modelConfig.get().getId(), MODEL_API_KEY);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }

        try {
            String endpoint = chatCompletionEndpoint(modelConfig.get().getApiBaseUrl());
            Map<String, Object> body = new LinkedHashMap<>();
            applyModelRequestOptions(body, modelConfig.get(), thinkingEnabled);
            body.put("model", modelConfig.get().getModelName());
            body.put("temperature", 0.1);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                Map.of(
                    "role", "system",
                    "content", "你是 CPClaw 的结构化意图规划器。只能输出 JSON 对象，不要输出 Markdown。必须基于给定上下文、真实云枢元数据字段和关联关系推断用户意图。不要编造 schemaCode 或字段。查询/分析类不需要用户确认；新增、修改、删除需要确认。上下文已有明确实体时优先继承上下文。"
                ),
                Map.of(
                    "role", "user",
                    "content", intentPlanningPrompt(planningContext, thinkingEnabled)
                )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(PLANNING_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            recordUsage(root);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                return Optional.empty();
            }
            String providerReasoning = root.path("choices").path(0).path("message").path("reasoning_content").asText("");
            return parseIntentPlan(content.asText(), providerReasoning);
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ConversationRouteResult> routeConversation(
        String preferredModelConfigId,
        String userGoal,
        List<String> recentMessages,
        boolean thinkingEnabled
    ) {
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        String safeGoal = sensitiveDataMasker.mask(userGoal == null ? "" : userGoal.trim());
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            return Optional.of(localConversationRoute(safeGoal));
        }
        Optional<String> apiKey = credentialService.revealCredential(OWNER_MODEL, modelConfig.get().getId(), MODEL_API_KEY);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }
        try {
            String endpoint = chatCompletionEndpoint(modelConfig.get().getApiBaseUrl());
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("userGoal", safeGoal);
            context.put("recentMessages", recentMessages == null ? List.of() : recentMessages.stream().limit(6).toList());
            Map<String, Object> body = new LinkedHashMap<>();
            applyModelRequestOptions(body, modelConfig.get(), thinkingEnabled);
            body.put("model", modelConfig.get().getModelName());
            body.put("temperature", 0.1);
            body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(
                Map.of("role", "system", "content", "你是 CPClaw 的对话路由器。只输出 JSON。先结合当前输入和上一轮上下文判断用户真实目标；若当前输入是在要求补充、深化、重做或纠正上一轮回答，应承接上一轮主题，不要把它当成孤立的新问题。若不需要业务系统能力，选择 conversation 并直接回答。不要调用工具，不要编造云枢数据。"),
                Map.of("role", "user", "content", routePrompt(context, thinkingEnabled))
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(8, TimeUnit.SECONDS)
                .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            recordUsage(root);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                return Optional.empty();
            }
            JsonNode result = objectMapper.readTree(content.asText());
            String mode = result.path("mode").asText("");
            if (!List.of("conversation", "task", "clarify").contains(mode)) {
                return Optional.empty();
            }
            String reasoning = result.path("reasoning").asText("");
            if (reasoning.isBlank()) {
                reasoning = root.path("choices").path(0).path("message").path("reasoning_content").asText("");
            }
            return Optional.of(new ConversationRouteResult(
                mode,
                result.path("skillId").asText(""),
                result.path("answer").asText(""),
                reasoning,
                result.path("confidence").asDouble(0D)
            ));
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> answerGeneralConversation(
        String preferredModelConfigId,
        String userGoal,
        List<String> recentMessages,
        boolean thinkingEnabled
    ) {
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        String safeGoal = sensitiveDataMasker.mask(userGoal == null ? "" : userGoal.trim());
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            return Optional.of(localGeneralConversationAnswer(safeGoal));
        }
        Optional<String> apiKey = credentialService.revealCredential(OWNER_MODEL, modelConfig.get().getId(), MODEL_API_KEY);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            applyModelRequestOptions(body, modelConfig.get(), thinkingEnabled);
            body.put("model", modelConfig.get().getModelName());
            body.put("temperature", thinkingEnabled ? 0.4 : 0.6);
            body.put("messages", List.of(
                Map.of("role", "system", "content", "你是 CPClaw 的通用对话助手。此请求已由服务端判定为不涉及已注册业务 Skill；请结合上一轮上下文直接、诚实地用中文回答。若当前输入是对上一轮回答的补充、深化、重做或不满意反馈，必须保留原主题并给出更完整、更可落地的新版本，不要只重复摘要，也不要追问用户重新选择动作类型。不得声称访问云枢、系统数据或执行了未发生的操作。"),
                Map.of("role", "user", "content", "当前任务与上下文：\n" + safeGoal + "\n\n最近上下文：\n" + String.join("\n", recentMessages == null ? List.of() : recentMessages.stream().limit(8).toList()))
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(chatCompletionEndpoint(modelConfig.get().getApiBaseUrl())) )
                .timeout(ANALYSIS_HARD_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .orTimeout(ANALYSIS_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .join();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            recordUsage(root);
            JsonNode answer = root.path("choices").path(0).path("message").path("content");
            return answer.isTextual() && !answer.asText().isBlank() ? Optional.of(answer.asText().trim()) : Optional.empty();
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
    }

    private ConversationRouteResult localConversationRoute(String userGoal) {
        String value = userGoal == null ? "" : userGoal.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        boolean task = List.of("查询", "分析", "统计", "多少", "数据", "记录", "表单", "流程", "待办", "已办", "填写", "新增", "修改", "删除", "审批", "发起")
            .stream().anyMatch(value::contains);
        if (task) {
            boolean analysis = List.of("分析", "报告", "洞察", "趋势", "诊断", "经营情况", "概览")
                .stream().anyMatch(value::contains);
            return new ConversationRouteResult(
                "task",
                analysis ? "yunshu-intelligent-inquiry" : "yunshu-business-system",
                "",
                analysis ? "输入需要云枢智能问数 Skill 规划数据、图形和业务总结。" : "输入包含业务数据或系统操作目标，进入云枢业务 Skill。",
                0.98D
            );
        }
        boolean greeting = List.of("hi", "hello", "hey", "你好", "您好", "嗨", "在吗", "早上好", "下午好", "晚上好").stream().anyMatch(value::equals);
        if (greeting) {
            return new ConversationRouteResult("conversation", "none", "你好，我在这里。你可以和我聊天，也可以让我查询和操作系统里的业务数据。", "输入是高置信日常问候，不需要调用业务技能。", 0.99D);
        }
        return new ConversationRouteResult("clarify", "none", "", "输入暂时无法可靠判断为闲聊或业务任务。", 0.45D);
    }

    private String localGeneralConversationAnswer(String userGoal) {
        if (userGoal != null && userGoal.contains("天气")) {
            return "我无法获取实时天气。你可以告诉我所在城市，我可以说明查询天气时应关注的温度、降水和出行建议。";
        }
        return "这是一个通用问题，我会直接回答，不调用云枢业务系统。请继续告诉我你想了解的内容。";
    }

    private String routePrompt(Map<String, Object> context, boolean thinkingEnabled) throws IOException {
        return """
            请输出：
            {"mode":"conversation|task|clarify","skillId":"task 时填写 yunshu-business-system 或 yunshu-intelligent-inquiry，否则 none","answer":"仅 conversation 必填的自然语言回答","reasoning":"一句话说明路由原因","confidence":0.0}
            规则：
            1. 问候、闲聊、解释概念、写作讨论且不要求系统数据/操作，mode=conversation，并直接回答。
            2. 普通查询、填单、流程、审批、创建、修改、删除或涉及云枢业务对象，mode=task、skillId=yunshu-business-system，answer 为空。
            3. 数据分析、经营分析、报告、洞察、趋势、诊断或需要图形化总结，mode=task、skillId=yunshu-intelligent-inquiry，answer 为空；该 Skill 不绑定具体业务对象。
            4. 无法判断且不是高置信问候，mode=clarify，answer 为空。
            5. conversation 不得声称访问了云枢或真实业务数据。
            6. 如果输入包含“再详细设计一下/展开/补充/完善/不够详细/上一版不满意”等表达，且上下文中存在上一轮用户目标和助手回答，优先判定为上一轮主题的 conversation 延续；answer 应直接给出更完整的新回答，不要要求用户重新选择动作类型。
            7. 当前思考模式：%s。
            输入上下文 JSON：%s
            """.formatted(thinkingEnabled ? "深度" : "快速", objectMapper.writeValueAsString(context));
    }

    private Optional<ModelConfig> resolveModel(String preferredModelConfigId) {
        if (preferredModelConfigId != null && !preferredModelConfigId.isBlank()) {
            Optional<ModelConfig> preferred = modelConfigRepository.findById(preferredModelConfigId)
                .filter(ModelConfig::isEnabled);
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        return modelConfigRepository.findByEnabledTrueOrderByUpdatedAtDesc().stream().findFirst();
    }

    /**
     * Builds the provider-neutral part of a chat request. {@code supportsThinking}
     * is an explicit capability declaration made by the administrator, rather than
     * an assumption inferred from a model name. Provider-specific extensions may be
     * stored in {@code extra_body_json}; those values take precedence over defaults.
     */
    private void applyModelRequestOptions(Map<String, Object> body, ModelConfig modelConfig, boolean thinkingEnabled) {
        mergeExtraBody(body, modelConfig == null ? null : modelConfig.getExtraBodyJson());
        if (modelConfig == null || !modelConfig.isSupportsThinking()) {
            return;
        }
        // enable_thinking is the broadly used OpenAI-compatible extension. A
        // provider that uses a different field can override it in extra_body_json.
        body.putIfAbsent("enable_thinking", thinkingEnabled);
        if (thinkingEnabled) {
            // OpenAI reasoning-capable endpoints understand this field; providers
            // that do not use it can override it through extra_body_json.
            body.putIfAbsent("reasoning_effort", "high");
        }
    }

    private void mergeExtraBody(Map<String, Object> body, String extraBodyJson) {
        if (extraBodyJson == null || extraBodyJson.isBlank()) {
            return;
        }
        try {
            JsonNode extraBody = objectMapper.readTree(extraBodyJson);
            if (extraBody == null || !extraBody.isObject()) {
                return;
            }
            extraBody.fields().forEachRemaining(entry -> body.put(
                entry.getKey(),
                objectMapper.convertValue(entry.getValue(), Object.class)
            ));
        } catch (IOException | IllegalArgumentException ignored) {
            // An invalid optional extension must not make otherwise valid model
            // requests fail. The model settings validation remains the authority
            // for reporting configuration errors to users.
        }
    }

    private String chatCompletionEndpoint(String apiBaseUrl) {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        if (value.endsWith("/v1")) {
            return value + "/chat/completions";
        }
        return value + "/v1/chat/completions";
    }

    private String localAnalysis(String userQuestion, String entityName, long total, List<Map<String, Object>> records) {
        StringBuilder answer = new StringBuilder();
        answer.append("### 结论摘要\n");
        answer.append("已围绕“").append(entityName).append("”查询到 ").append(total).append(" 条数据，并根据返回样本生成本地分析结果。\n\n");
        answer.append("### 关键发现\n");
        if (records == null || records.isEmpty()) {
            answer.append("- 当前没有返回可分析记录，建议先确认云枢账号权限、元数据同步状态和业务数据是否存在。\n");
        } else {
            answer.append("- 本次返回样本 ").append(records.size()).append(" 条，可结合金额、阶段、负责人和关联对象字段继续分析。\n");
            answer.append("- 样本中存在多个推进阶段，可用于判断记录是否集中在早期或后期节点。\n");
        }
        answer.append("\n### 风险信号\n");
        answer.append("- 如果高金额记录集中在早期阶段，需要关注转化概率和推进节奏。\n");
        answer.append("- 如果后期阶段记录数量偏少，可能意味着近期目标达成存在压力。\n");
        answer.append("\n### 下一步建议\n");
        answer.append("- 按阶段、负责人和金额分组继续下钻，识别需要优先推进的重点记录。\n");
        answer.append("- 配置真实大模型后，可基于完整记录生成更细的趋势、风险和行动建议。\n");
        if (userQuestion != null && !userQuestion.isBlank()) {
            answer.append("\n原始问题：").append(userQuestion);
        }
        return answer.toString();
    }

    private IntentPlanningResult localIntentPlan(Map<String, Object> planningContext) {
        String userGoal = String.valueOf(planningContext.getOrDefault("userGoal", ""));
        String entityName = String.valueOf(planningContext.getOrDefault("entityName", "业务对象"));
        boolean inherited = Boolean.TRUE.equals(planningContext.get("inheritedRuntimeObject"));
        boolean creation = containsAny(userGoal, "新增", "新建", "创建", "录入", "登记");
        boolean update = !creation && containsAny(userGoal, "写入", "修改", "更新", "调整", "变更", "编辑", "保存", "提交");
        boolean explicitQuery = userGoal.contains("查询")
            || userGoal.contains("多少")
            || userGoal.contains("几")
            || userGoal.contains("返回")
            || userGoal.contains("第一")
            || userGoal.contains("列表")
            || userGoal.contains("明细");
        boolean broadAnalysis = userGoal.contains("分析")
            || userGoal.contains("这些")
            || userGoal.contains("它们")
            || userGoal.contains("整体")
            || userGoal.contains("概览")
            || userGoal.contains("怎么样")
            || userGoal.contains("怎么看");
        boolean fieldAnalysis = userGoal.contains("阶段")
            || userGoal.contains("状态")
            || userGoal.contains("分布")
            || userGoal.contains("汇总")
            || userGoal.contains("金额")
            || userGoal.contains("负责人")
            || userGoal.contains("销售")
            || userGoal.contains("趋势")
            || userGoal.contains("每年")
            || userGoal.contains("按年");
        boolean analysis = broadAnalysis
            || fieldAnalysis
            || (userGoal.contains("情况") && (inherited || !explicitQuery))
            || (inherited && !explicitQuery);
        List<String> dimensions = analysis
            ? List.of("阶段/状态分布", "金额概览", "负责人分布", "时间趋势", "关联对象分析")
            : List.of();
        String reasoning = inherited
            ? "用户问题引用上一轮结果，结合上下文继续分析“" + entityName + "”。"
            : "根据用户问题和元数据候选识别目标对象为“" + entityName + "”。";
        String intent = creation ? "create_data" : (update ? "update_data" : (analysis ? "analyze_data" : "query_data"));
        String actionLabel = creation ? "新增/填单" : (update ? "修改/写入" : (analysis ? "分析" : "查询"));
        String apiOperation = creation ? "create" : (update ? "update" : "query_collection");
        List<Map<String, Object>> executionSteps = creation || update
            ? List.of(
                Map.of("step", "prepare_form", "description", "根据云枢元数据核对目标表单与待填写字段"),
                Map.of("step", apiOperation, "description", "生成写操作确认计划，等待用户确认后再执行")
            )
            : List.of(
                Map.of("step", "query_collection", "description", "调用云枢列表接口获取业务对象数据集合"),
                Map.of("step", "summarize_with_llm", "description", "结合用户问题、云枢元数据和查询结果生成回答")
            );
        return new IntentPlanningResult(
            intent,
            actionLabel,
            entityName,
            analysis ? "业务概览" : "无明确维度",
            "无明确筛选条件",
            dimensions,
            stringList(planningContext.get("fieldHints")),
            stringList(planningContext.get("relationHints")),
            apiOperation,
            executionSteps,
            List.of(),
            inferMetricFieldCodes(userGoal, stringList(planningContext.get("fieldHints"))),
            inferGroupByFieldCodes(userGoal, stringList(planningContext.get("fieldHints"))),
            List.of(),
            0,
            creation || update,
            reasoning,
            false,
            0.9,
            creation ? "新建" + entityName : (update ? "修改" + entityName : (analysis ? "分析" + entityName + "经营情况" : "查询" + entityName + "数据"))
        );
    }

    private boolean containsAny(String value, String... values) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String item : values) {
            if (value.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private Optional<IntentPlanningResult> parseIntentPlan(String content, String providerReasoning) throws IOException {
        JsonNode root = objectMapper.readTree(content);
        String reasoning = root.path("reasoning").asText("");
        if (reasoning.isBlank()) {
            // Provider reasoning_content may contain raw private chain-of-thought.
            // Keep an auditable planning summary, never copy that field into plans or logs.
            reasoning = "基于当前目标、上下文和可用元数据生成受限计划。";
        }
        return Optional.of(new IntentPlanningResult(
            root.path("intent").asText(""),
            root.path("actionLabel").asText(""),
            root.path("businessObject").asText(""),
            root.path("dimension").asText(""),
            root.path("filters").asText(""),
            jsonStringList(root.path("analysisDimensions")),
            jsonStringList(root.path("fieldHints")),
            jsonStringList(root.path("relationHints")),
            root.path("apiOperation").asText(""),
            jsonObjectList(root.path("executionSteps")),
            jsonObjectList(root.path("runtimeFilters")),
            jsonStringList(root.path("metricFieldCodes")),
            jsonStringList(root.path("groupByFieldCodes")),
            jsonObjectList(root.path("sortFields")),
            root.path("resultLimit").asInt(0),
            root.path("requiresConfirmation").asBoolean(false),
            reasoning,
            root.path("clarificationNeeded").asBoolean(false),
            root.path("confidence").asDouble(0.0),
            root.path("businessIntent").asText("")
        ));
    }

    private List<Map<String, Object>> jsonObjectList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<Map<String, Object>> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            if (item.isObject()) {
                values.add(objectMapper.convertValue(item, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
        });
        return values;
    }

    private List<String> jsonStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return values;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
        }
        return List.of();
    }

    private List<String> inferMetricFieldCodes(String userGoal, List<String> fieldHints) {
        String value = userGoal == null ? "" : userGoal.toLowerCase();
        if (!(value.contains("金额") || value.contains("合同额") || value.contains("收入") || value.contains("amount") || value.contains("money") || value.contains("revenue"))) {
            return List.of();
        }
        return fieldHints.stream()
            .filter(item -> {
                String text = item == null ? "" : item.toLowerCase();
                return text.contains("金额") || text.contains("合同额") || text.contains("收入") || text.contains("amount") || text.contains("money") || text.contains("revenue");
            })
            .map(this::fieldCodeFromHint)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private List<String> inferGroupByFieldCodes(String userGoal, List<String> fieldHints) {
        String value = userGoal == null ? "" : userGoal.toLowerCase();
        if (!(value.contains("阶段") || value.contains("状态") || value.contains("分布") || value.contains("分别") || value.contains("按"))) {
            return List.of();
        }
        return fieldHints.stream()
            .filter(item -> {
                String text = item == null ? "" : item.toLowerCase();
                return text.contains("阶段") || text.contains("状态") || text.contains("stage") || text.contains("status") || text.contains("state");
            })
            .map(this::fieldCodeFromHint)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private String fieldCodeFromHint(String hint) {
        if (hint == null) {
            return "";
        }
        int start = hint.indexOf('(');
        int end = hint.indexOf(',', start + 1);
        if (start >= 0 && end > start) {
            return hint.substring(start + 1, end).trim();
        }
        int close = hint.indexOf(')', start + 1);
        if (start >= 0 && close > start) {
            return hint.substring(start + 1, close).trim();
        }
        return hint.trim();
    }

    private boolean isLocalTestUrl(String apiBaseUrl) {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.toLowerCase();
        return value.contains("example.local") || value.contains("localhost") || value.contains("127.0.0.1");
    }

    private boolean isTestableModelEndpoint(String apiBaseUrl) {
        try {
            URI uri = URI.create(apiBaseUrl == null ? "" : apiBaseUrl.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) {
                return false;
            }
            String host = uri.getHost().toLowerCase();
            if (host.equals("localhost") || host.equals("::1") || host.startsWith("127.") || isPrivateIpv4(host)) {
                return false;
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException | IOException exception) {
            return false;
        }
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 0 || first == 10 || first == 127 || (first == 169 && second == 254) || (first == 172 && second >= 16 && second <= 31) || (first == 192 && second == 168);
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String analysisPrompt(String userQuestion, String entityName, long total, List<Map<String, Object>> records, boolean thinkingEnabled, Map<String, Object> reasoningContext) throws IOException {
        List<Map<String, Object>> sample = records == null ? List.of() : records.stream()
            .limit(8)
            .map(this::compactRecordForModel)
            .toList();
        Map<String, Object> context = reasoningContext == null ? Map.of() : reasoningContext;
        String basePrompt = """
            用户问题：%s
            云枢对象：%s
            查询总数：%d
            返回样本数：%d
            是否允许更深入推理：%s

            查询样本 JSON：
            %s

            请输出：
            1. 结论摘要
            2. 关键发现
            3. 可能风险或异常
            4. 建议下一步动作
            """.formatted(
                userQuestion == null ? "" : userQuestion,
                entityName == null ? "" : entityName,
                total,
                sample.size(),
                thinkingEnabled ? "是" : "否",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample)
            );
        return basePrompt + "\n\nMetadata and execution path JSON:\n"
            + objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
    }

    private Map<String, Object> compactRecordForModel(Map<String, Object> record) {
        if (record == null || record.isEmpty()) {
            return Map.of();
        }
        Map<?, ?> data = record.get("data") instanceof Map<?, ?> dataMap ? dataMap : record;
        Map<String, Object> compact = new LinkedHashMap<>();
        putFirst(compact, "id", record, "id", "objectId", "bizObjectId");
        putFirst(compact, "name", data, "instanceName", "name", "title", "recordName", "displayName", "名称", "标题");
        putFirst(compact, "amount", data, "amount", "money", "value", "totalAmount", "金额", "数值");
        putFirst(compact, "stage", data, "stage", "status", "state", "阶段", "状态");
        putFirst(compact, "owner", data, "owner", "ownerName", "sales", "salesName", "createdByName", "负责人", "销售", "业务员");
        putFirst(compact, "relatedObject", data, "relatedObject", "relatedName", "relation", "关联对象", "关联名称");
        putFirst(compact, "updatedAt", data, "updatedAt", "modifiedTime", "modifyTime", "updateTime", "修改时间");
        return compact.isEmpty() ? firstUsefulFields(data) : compact;
    }

    private void putFirst(Map<String, Object> target, String outputKey, Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            String text = compactValue(value);
            if (!text.isBlank()) {
                target.put(outputKey, text);
                return;
            }
        }
    }

    private Map<String, Object> firstUsefulFields(Map<?, ?> source) {
        Map<String, Object> compact = new LinkedHashMap<>();
        source.entrySet().stream()
            .filter(entry -> isUsefulField(String.valueOf(entry.getKey())))
            .limit(8)
            .forEach(entry -> {
                String text = compactValue(entry.getValue());
                if (!text.isBlank()) {
                    compact.put(String.valueOf(entry.getKey()), text);
                }
            });
        return compact;
    }

    private boolean isUsefulField(String key) {
        String value = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        return !(value.contains("schema") || value.contains("propertytype") || value.contains("exceltype") || value.contains("unittype") || value.equals("type") || value.contains("permissions"));
    }

    private String compactValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("name", "displayName", "label", "instanceName", "cust_fullname", "sequenceNo", "org_name", "value", "id")) {
                Object nested = map.get(key);
                if (nested != null && !String.valueOf(nested).isBlank()) {
                    return String.valueOf(nested);
                }
            }
            return firstUsefulFields(map).values().stream().findFirst().map(String::valueOf).orElse("");
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::compactValue).filter(item -> !item.isBlank()).distinct().limit(3).reduce((a, b) -> a + "、" + b).orElse("");
        }
        String text = String.valueOf(value).trim();
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    private String intentPlanningPrompt(Map<String, Object> planningContext, boolean thinkingEnabled) throws IOException {
        return """
            请基于以下 JSON 上下文输出意图规划 JSON。

            输出字段必须为：
            {
              "intent": "query_data|analyze_data|create_data|update_data|delete_data|clarify_intent",
              "businessIntent": "用简短中文描述本轮业务任务；不得输出固定接口分类或技术编码",
              "actionLabel": "查询/分析/新增/修改/删除/澄清",
              "businessObject": "业务对象名称",
              "dimension": "分析维度，没有则写无明确维度",
              "filters": "筛选条件，没有则写无明确筛选条件",
              "analysisDimensions": ["阶段/状态分布", "金额概览"],
              "fieldHints": ["字段线索"],
              "relationHints": ["关联线索"],
              "apiOperation": "query_collection|query_detail|create|update|delete|clarify",
              "executionSteps": [
                {"step": "query_collection", "description": "调用云枢列表接口获取业务对象数据集合"},
                {"step": "summarize_with_llm", "description": "结合用户问题、云枢元数据和查询结果生成回答"}
              ],
              "runtimeFilters": [
                {"fieldCode": "字段编码", "fieldName": "字段名称", "operator": "eq|like|in|gte|lte", "value": "筛选值", "reason": "为什么使用该条件"}
              ],
              "metricFieldCodes": ["需要计算或排序的字段编码"],
              "groupByFieldCodes": ["需要分组的字段编码"],
              "sortFields": [{"fieldCode": "字段编码", "direction": "asc|desc"}],
              "resultLimit": 10,
              "requiresConfirmation": false,
              "reasoning": "一句话说明为什么这样理解",
              "clarificationNeeded": false,
              "confidence": 0.0
            }

            规则：
            1. 用户说“这些/它/上述/继续”时，优先承接上下文实体；用户说“再详细设计一下/展开/补充/完善/不够详细”时，优先承接上一轮用户目标和助手回答，形成深化后的新目标。
            2. 如果用户引用上一轮结果，应继承已验证的运行态对象并继续分析。
            3. 宽泛分析默认包含阶段/状态、金额、负责人、时间趋势和已验证关联对象等维度，前提是字段线索存在。
            4. runtimeFilters、metricFieldCodes、groupByFieldCodes、sortFields 只能使用上下文 fieldHints 中真实存在的字段编码。
            5. apiOperation 必须来自上下文 apiHints 能力；查询集合用 query_collection，单条详情用 query_detail，新增用 create，修改用 update，删除用 delete。
            6. 不要输出不存在于上下文的 schemaCode 或字段编码。
            7. 查询/分析类 requiresConfirmation=false；新增、修改、删除 requiresConfirmation=true。用户明确说新建/创建/录入时，intent 必须是 create_data；明确说修改/更新/编辑时，intent 必须是 update_data。
            8. 只有对象、动作或关键筛选值无法确定时才 clarificationNeeded=true；如果只是对上一轮回答要求更详细，不得因为没有重复动作词而澄清。
            9. 如果上一轮回答存在点踩或明确“不满意/不够详细”反馈，应优先补足结构、边界、字段、流程和落地步骤，而不是重复上一轮摘要。
            10. 是否允许深入推理：%s。

            上下文 JSON：
            %s
            """.formatted(
                thinkingEnabled ? "是" : "否",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(planningContext)
            );
    }

    private void recordUsage(JsonNode usageNode) {
        JsonNode usage = findUsageNode(usageNode);
        if (usage == null || !usage.isObject()) {
            return;
        }
        long promptTokens = firstLong(usage, "prompt_tokens", "input_tokens", "promptTokens", "inputTokens");
        long completionTokens = firstLong(usage, "completion_tokens", "output_tokens", "completionTokens", "outputTokens");
        long cachedTokens = firstLong(usage, "cache_read_input_tokens", "cacheReadInputTokens", "cached_tokens", "cachedTokens");
        if (cachedTokens <= 0) {
            cachedTokens = firstNestedLong(usage, List.of("prompt_tokens_details", "input_token_details"), "cached_tokens", "cache_read_input_tokens", "cachedTokens", "cacheReadInputTokens");
        }
        long totalTokens = firstLong(usage, "total_tokens", "totalTokens");
        modelUsageContext.record(new TokenUsage(promptTokens, completionTokens, cachedTokens, totalTokens));
    }

    private JsonNode findUsageNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isObject() && (node.has("prompt_tokens") || node.has("completion_tokens") || node.has("total_tokens") || node.has("cached_tokens") || node.has("cache_read_input_tokens")
            || node.has("input_tokens") || node.has("output_tokens") || node.has("promptTokens") || node.has("completionTokens") || node.has("totalTokens"))) {
            return node;
        }
        if (node.isObject()) {
            for (String key : List.of("usage", "response", "data", "result")) {
                JsonNode nested = node.get(key);
                JsonNode found = findUsageNode(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private long firstLong(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isNumber()) {
                return Math.max(0, value.asLong());
            }
            if (value.isTextual()) {
                try {
                    return Math.max(0, Long.parseLong(value.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // Try the next compatible field name.
                }
            }
        }
        return 0;
    }

    private long firstNestedLong(JsonNode node, List<String> objectNames, String... fieldNames) {
        for (String objectName : objectNames) {
            JsonNode nested = node.path(objectName);
            if (nested.isObject()) {
                long value = firstLong(nested, fieldNames);
                if (value > 0) {
                    return value;
                }
            }
        }
        return 0;
    }
}
