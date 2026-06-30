package com.cpclaw.vector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VectorSearchProperties {

    private final boolean enabled;
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int topK;
    private final double minSimilarity;
    private final String embeddingBaseUrl;
    private final String embeddingApiKey;
    private final String embeddingModel;

    public VectorSearchProperties(
        @Value("${cpclaw.vector.enabled:false}") boolean enabled,
        @Value("${cpclaw.vector.jdbc-url:}") String jdbcUrl,
        @Value("${cpclaw.vector.username:}") String username,
        @Value("${cpclaw.vector.password:}") String password,
        @Value("${cpclaw.vector.top-k:20}") int topK,
        @Value("${cpclaw.vector.min-similarity:0.62}") double minSimilarity,
        @Value("${cpclaw.embedding.base-url:}") String embeddingBaseUrl,
        @Value("${cpclaw.embedding.api-key:}") String embeddingApiKey,
        @Value("${cpclaw.embedding.model:text-embedding-v4}") String embeddingModel
    ) {
        this.enabled = enabled;
        this.jdbcUrl = value(jdbcUrl);
        this.username = value(username);
        this.password = value(password);
        this.topK = Math.max(1, topK);
        this.minSimilarity = minSimilarity;
        this.embeddingBaseUrl = value(embeddingBaseUrl);
        this.embeddingApiKey = value(embeddingApiKey);
        this.embeddingModel = value(embeddingModel);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean readyForEmbedding() {
        return enabled && hasText(embeddingBaseUrl) && hasText(embeddingApiKey) && hasText(embeddingModel);
    }

    public boolean readyForVectorStore() {
        return enabled && hasText(jdbcUrl) && hasText(username);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public int topK() {
        return topK;
    }

    public double minSimilarity() {
        return minSimilarity;
    }

    public String embeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public String embeddingApiKey() {
        return embeddingApiKey;
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }

    private boolean hasText(String input) {
        return input != null && !input.isBlank();
    }
}
