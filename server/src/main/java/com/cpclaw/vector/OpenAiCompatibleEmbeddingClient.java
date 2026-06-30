package com.cpclaw.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
    private static final int MAX_BATCH_SIZE = 10;

    private final VectorSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingClient(VectorSearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    }

    @Override
    public List<EmbeddingVector> embed(List<String> texts) {
        if (!properties.readyForEmbedding() || texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<EmbeddingInput> inputs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i) == null ? "" : texts.get(i).trim();
            if (!text.isBlank()) {
                inputs.add(new EmbeddingInput(i, text));
            }
        }
        if (inputs.isEmpty()) {
            return List.of();
        }

        List<EmbeddingVector> vectors = new ArrayList<>();
        for (int start = 0; start < inputs.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, inputs.size());
            vectors.addAll(embedBatchWithFallback(inputs.subList(start, end)));
        }
        return vectors;
    }

    private List<EmbeddingVector> embedBatchWithFallback(List<EmbeddingInput> batch) {
        EmbeddingBatchResult result = embedBatch(batch);
        if (result.shouldRetryWithSmallerBatch() && batch.size() > 1) {
            int middle = batch.size() / 2;
            List<EmbeddingVector> vectors = new ArrayList<>();
            vectors.addAll(embedBatchWithFallback(batch.subList(0, middle)));
            vectors.addAll(embedBatchWithFallback(batch.subList(middle, batch.size())));
            return vectors;
        }
        return result.vectors();
    }

    private EmbeddingBatchResult embedBatch(List<EmbeddingInput> batch) {
        if (batch.isEmpty()) {
            return new EmbeddingBatchResult(List.of(), false);
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.embeddingModel());
            body.put("input", batch.stream().map(EmbeddingInput::text).toList());

            HttpRequest request = HttpRequest.newBuilder(URI.create(embeddingEndpoint(properties.embeddingBaseUrl())))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.embeddingApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().isBlank()) {
                boolean canRetrySmaller = response.statusCode() == 400 || response.statusCode() == 413;
                log.info("embedding batch skipped: status={}, batchSize={}, retrySmaller={}", response.statusCode(), batch.size(), canRetrySmaller);
                return new EmbeddingBatchResult(List.of(), canRetrySmaller);
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.isArray()) {
                log.info("embedding batch skipped: response data is not an array, batchSize={}", batch.size());
                return new EmbeddingBatchResult(List.of(), true);
            }
            List<EmbeddingVector> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray() || embedding.isEmpty()) {
                    continue;
                }
                List<Double> values = new ArrayList<>();
                for (JsonNode value : embedding) {
                    values.add(value.asDouble());
                }
                int batchIndex = item.path("index").isInt() ? item.path("index").asInt() : vectors.size();
                if (batchIndex < 0 || batchIndex >= batch.size()) {
                    continue;
                }
                vectors.add(new EmbeddingVector(batch.get(batchIndex).originalIndex(), values));
            }
            return new EmbeddingBatchResult(vectors, vectors.isEmpty());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.info("embedding batch skipped: {}, batchSize={}", exception.getMessage(), batch.size());
            return new EmbeddingBatchResult(List.of(), false);
        }
    }

    private String embeddingEndpoint(String apiBaseUrl) {
        String value = apiBaseUrl == null ? "" : apiBaseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/embeddings")) {
            return value;
        }
        if (value.endsWith("/v1")) {
            return value + "/embeddings";
        }
        return value + "/v1/embeddings";
    }

    private record EmbeddingInput(int originalIndex, String text) {
    }

    private record EmbeddingBatchResult(List<EmbeddingVector> vectors, boolean shouldRetryWithSmallerBatch) {
    }
}
