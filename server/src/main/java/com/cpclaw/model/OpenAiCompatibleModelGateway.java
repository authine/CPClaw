package com.cpclaw.model;

import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private static final String OWNER_MODEL = "model_config";
    private static final String MODEL_API_KEY = "model_api_key";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ANALYSIS_HARD_TIMEOUT = Duration.ofSeconds(20);
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

    public OpenAiCompatibleModelGateway(
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
        return Map.of("modelConfigId", modelConfigId, "status", "openai-compatible-placeholder");
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
                .timeout(REQUEST_TIMEOUT)
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
            recordUsage(root.path("usage"));
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
                .timeout(REQUEST_TIMEOUT)
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
        ScheduledFuture<?> timeout = MODEL_TIMEOUT_SCHEDULER.schedule(() -> {
            timedOut.set(true);
            try {
                inputStream.close();
            } catch (IOException ignored) {
                // Closing the stream interrupts a stalled provider response.
            }
        }, ANALYSIS_HARD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        StringBuilder answer = new StringBuilder();
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
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                recordUsage(root.path("usage"));
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
                if (chunkConsumer != null) {
                    chunkConsumer.accept(chunk);
                }
            }
        } catch (IOException exception) {
            if (!timedOut.get()) {
                throw exception;
            }
        } finally {
            timeout.cancel(false);
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
            recordUsage(root.path("usage"));
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) {
                return Optional.empty();
            }
            return parseIntentPlan(content.asText());
        } catch (RuntimeException | IOException exception) {
            return Optional.empty();
        }
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
            answer.append("- 本次返回样本 ").append(records.size()).append(" 条，优先关注金额、阶段、负责人和客户分布。\n");
            answer.append("- 样本中存在多个推进阶段，可用于判断商机管道是否集中在早期沟通或后期成交节点。\n");
        }
        answer.append("\n### 风险信号\n");
        answer.append("- 如果高金额商机集中在早期阶段，需要关注转化概率和跟进节奏。\n");
        answer.append("- 如果后期阶段商机数量偏少，可能意味着近期收入确认存在压力。\n");
        answer.append("\n### 下一步建议\n");
        answer.append("- 按阶段、负责人和金额分组继续下钻，识别需要优先推进的重点商机。\n");
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
            ? List.of("阶段/状态分布", "金额概览", "负责人分布", "时间趋势", "关联客户分析")
            : List.of();
        String reasoning = inherited
            ? "用户问题引用上一轮结果，结合上下文继续分析“" + entityName + "”。"
            : "根据用户问题和元数据候选识别目标对象为“" + entityName + "”。";
        return new IntentPlanningResult(
            analysis ? "analyze_data" : "query_data",
            analysis ? "分析" : "查询",
            entityName,
            analysis ? "业务概览" : "无明确维度",
            "无明确筛选条件",
            dimensions,
            stringList(planningContext.get("fieldHints")),
            stringList(planningContext.get("relationHints")),
            "query_collection",
            List.of(
                Map.of("step", "query_collection", "description", "调用云枢列表接口获取业务对象数据集合"),
                Map.of("step", "summarize_with_llm", "description", "结合用户问题、云枢元数据和查询结果生成回答")
            ),
            List.of(),
            inferMetricFieldCodes(userGoal, stringList(planningContext.get("fieldHints"))),
            inferGroupByFieldCodes(userGoal, stringList(planningContext.get("fieldHints"))),
            List.of(),
            0,
            false,
            reasoning,
            false,
            0.9
        );
    }

    private Optional<IntentPlanningResult> parseIntentPlan(String content) throws IOException {
        JsonNode root = objectMapper.readTree(content);
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
            root.path("reasoning").asText(""),
            root.path("clarificationNeeded").asBoolean(false),
            root.path("confidence").asDouble(0.0)
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
        putFirst(compact, "name", data, "instanceName", "name", "title", "opportunityName", "customerName", "projectName", "商机名称", "客户名称", "项目名称");
        putFirst(compact, "amount", data, "amount", "opportunityAmount", "projectAmount", "contractAmount", "money", "金额", "商机金额", "项目金额", "合同额");
        putFirst(compact, "stage", data, "stage", "status", "projectStatus", "state", "阶段", "状态", "项目状态");
        putFirst(compact, "owner", data, "owner", "ownerName", "sales", "salesName", "createdByName", "负责人", "销售", "业务员");
        putFirst(compact, "customer", data, "customer", "customerName", "cust_id", "opportunityCustomer", "客户", "客户名称");
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
              "intent": "query_data|analyze_data|update_data|delete_data|clarify_intent",
              "actionLabel": "查询/分析/修改/删除/澄清",
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
            1. 用户说“这些/它/上述/继续”时，优先承接上下文实体。
            2. “分析一下这些商机的情况”应理解为对上一轮商机做业务概览分析。
            3. 宽泛分析默认包含阶段/状态、金额、负责人、时间趋势、关联客户等维度，前提是字段线索存在。
            4. runtimeFilters、metricFieldCodes、groupByFieldCodes、sortFields 只能使用上下文 fieldHints 中真实存在的字段编码。
            5. apiOperation 必须来自上下文 apiHints 能力；查询集合用 query_collection，单条详情用 query_detail，删除用 delete。
            6. 不要输出不存在于上下文的 schemaCode 或字段编码。
            7. 查询/分析类 requiresConfirmation=false；新增、修改、删除 requiresConfirmation=true。
            8. 只有对象、动作或关键筛选值无法确定时才 clarificationNeeded=true。
            9. 是否允许深入推理：%s。

            上下文 JSON：
            %s
            """.formatted(
                thinkingEnabled ? "是" : "否",
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(planningContext)
            );
    }

    private void recordUsage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return;
        }
        long promptTokens = firstLong(usageNode, "prompt_tokens", "input_tokens", "promptTokens", "inputTokens");
        long completionTokens = firstLong(usageNode, "completion_tokens", "output_tokens", "completionTokens", "outputTokens");
        long totalTokens = firstLong(usageNode, "total_tokens", "totalTokens");
        modelUsageContext.record(new TokenUsage(promptTokens, completionTokens, totalTokens));
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
}
