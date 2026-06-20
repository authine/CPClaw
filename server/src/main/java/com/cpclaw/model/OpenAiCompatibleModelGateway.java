package com.cpclaw.model;

import com.cpclaw.credential.CredentialService;
import com.cpclaw.model.entity.ModelConfig;
import com.cpclaw.model.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleModelGateway implements ModelGateway {

    private static final String OWNER_MODEL = "model_config";
    private static final String MODEL_API_KEY = "model_api_key";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ModelConfigRepository modelConfigRepository;
    private final CredentialService credentialService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleModelGateway(
        ModelConfigRepository modelConfigRepository,
        CredentialService credentialService,
        ObjectMapper objectMapper
    ) {
        this.modelConfigRepository = modelConfigRepository;
        this.credentialService = credentialService;
        this.objectMapper = objectMapper;
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
        Optional<ModelConfig> modelConfig = resolveModel(preferredModelConfigId);
        if (modelConfig.isEmpty()) {
            return Optional.empty();
        }
        if (isLocalTestUrl(modelConfig.get().getApiBaseUrl())) {
            return Optional.of(localAnalysis(userQuestion, entityName, total, records));
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
                    "content", analysisPrompt(userQuestion, entityName, total, records, thinkingEnabled)
                )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey.get())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return Optional.of(content.asText().trim());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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

    private boolean isLocalTestUrl(String apiBaseUrl) {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.toLowerCase();
        return value.contains("example.local") || value.contains("localhost") || value.contains("127.0.0.1");
    }

    private String analysisPrompt(String userQuestion, String entityName, long total, List<Map<String, Object>> records, boolean thinkingEnabled) throws IOException {
        List<Map<String, Object>> sample = records == null ? List.of() : records.stream().limit(20).toList();
        return """
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
    }
}
