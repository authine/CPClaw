package com.cpclaw.vector;

import com.cpclaw.metadata.entity.MetadataSearchDocument;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PgVectorMetadataSearch implements MetadataVectorSearch {

    private static final Logger log = LoggerFactory.getLogger(PgVectorMetadataSearch.class);
    private static final String TABLE_NAME = "metadata_vector_documents";

    private final VectorSearchProperties properties;
    private final EmbeddingClient embeddingClient;
    private volatile boolean schemaReady;
    private volatile int vectorDimension;

    public PgVectorMetadataSearch(VectorSearchProperties properties, EmbeddingClient embeddingClient) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
    }

    @Override
    public boolean enabled() {
        return properties.enabled() && properties.readyForEmbedding() && properties.readyForVectorStore();
    }

    @Override
    public void indexDocuments(List<MetadataSearchDocument> documents) {
        if (!enabled() || documents == null || documents.isEmpty()) {
            return;
        }
        List<MetadataSearchDocument> indexable = documents.stream()
            .filter(document -> hasText(document.getId()))
            .toList();
        if (indexable.isEmpty()) {
            return;
        }

        List<String> texts = indexable.stream().map(this::embeddingText).toList();
        List<EmbeddingVector> embeddings = embeddingClient.embed(texts);
        if (embeddings.isEmpty()) {
            log.info("metadata vector index skipped because embedding service returned no vectors");
            return;
        }
        Map<Integer, EmbeddingVector> byIndex = new LinkedHashMap<>();
        for (EmbeddingVector embedding : embeddings) {
            if (!embedding.values().isEmpty()) {
                byIndex.put(embedding.index(), embedding);
            }
        }
        if (byIndex.isEmpty()) {
            return;
        }

        int dimension = byIndex.values().stream()
            .map(EmbeddingVector::values)
            .mapToInt(List::size)
            .findFirst()
            .orElse(0);
        if (dimension <= 0) {
            return;
        }

        try (Connection connection = connection()) {
            ensureSchema(connection, dimension);
            try (PreparedStatement statement = connection.prepareStatement(upsertSql())) {
                Instant now = Instant.now();
                for (int i = 0; i < indexable.size(); i++) {
                    EmbeddingVector vector = byIndex.get(i);
                    if (vector == null || vector.values().size() != dimension) {
                        continue;
                    }
                    MetadataSearchDocument document = indexable.get(i);
                    bindDocument(statement, document, texts.get(i), vector, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        } catch (SQLException exception) {
            log.info("metadata vector index unavailable: {}", exception.getMessage());
        }
    }

    @Override
    public List<VectorSearchCandidate> search(String query) {
        if (!enabled() || !hasText(query)) {
            return List.of();
        }
        List<EmbeddingVector> embeddings = embeddingClient.embed(List.of(query));
        if (embeddings.isEmpty() || embeddings.getFirst().values().isEmpty()) {
            return List.of();
        }
        EmbeddingVector embedding = embeddings.getFirst();
        try (Connection connection = connection()) {
            ensureSchema(connection, embedding.values().size());
            List<VectorSearchCandidate> candidates = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(searchSql())) {
                String vector = toPgVectorLiteral(embedding.values());
                statement.setString(1, vector);
                statement.setString(2, vector);
                statement.setInt(3, properties.topK());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        double similarity = resultSet.getDouble("similarity");
                        if (similarity < properties.minSimilarity()) {
                            continue;
                        }
                        candidates.add(new VectorSearchCandidate(
                            resultSet.getString("document_id"),
                            similarity,
                            resultSet.getString("embedding_model"),
                            "向量语义召回 similarity=" + String.format("%.4f", similarity)
                        ));
                    }
                }
            }
            return candidates.stream()
                .sorted(Comparator.comparingDouble(VectorSearchCandidate::similarity).reversed())
                .toList();
        } catch (SQLException exception) {
            log.info("metadata vector search unavailable: {}", exception.getMessage());
            return List.of();
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(properties.jdbcUrl(), properties.username(), properties.password());
    }

    private synchronized void ensureSchema(Connection connection, int dimension) throws SQLException {
        if (schemaReady && vectorDimension == dimension) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                CREATE TABLE IF NOT EXISTS metadata_vector_schema (
                    id varchar(64) PRIMARY KEY,
                    dimension integer NOT NULL,
                    embedding_model varchar(128) NOT NULL,
                    updated_at timestamp NOT NULL
                )
                """);
        }

        Integer existingDimension = existingDimension(connection);
        if (existingDimension != null && existingDimension != dimension) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS " + TABLE_NAME);
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS metadata_vector_documents (
                    document_id varchar(64) PRIMARY KEY,
                    object_type varchar(64) NOT NULL,
                    object_id varchar(64) NOT NULL,
                    name varchar(255) NOT NULL,
                    code varchar(255),
                    graph_path varchar(1024),
                    risk_level varchar(32),
                    embedding_text text NOT NULL,
                    embedding_model varchar(128) NOT NULL,
                    embedding_dimension integer NOT NULL,
                    sync_batch_id varchar(64),
                    embedding vector(%d) NOT NULL,
                    indexed_at timestamp NOT NULL,
                    updated_at timestamp NOT NULL
                )
                """.formatted(dimension));
            statement.execute("CREATE INDEX IF NOT EXISTS idx_metadata_vector_documents_object ON " + TABLE_NAME + " (object_type, object_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_metadata_vector_documents_code ON " + TABLE_NAME + " (code)");
        }
        upsertSchemaDimension(connection, dimension);
        vectorDimension = dimension;
        schemaReady = true;
    }

    private Integer existingDimension(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("select dimension from metadata_vector_schema where id = 'metadata'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("dimension");
                }
                return null;
            }
        }
    }

    private void upsertSchemaDimension(Connection connection, int dimension) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            insert into metadata_vector_schema (id, dimension, embedding_model, updated_at)
            values ('metadata', ?, ?, ?)
            on conflict (id) do update set
                dimension = excluded.dimension,
                embedding_model = excluded.embedding_model,
                updated_at = excluded.updated_at
            """)) {
            statement.setInt(1, dimension);
            statement.setString(2, properties.embeddingModel());
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private String upsertSql() {
        return """
            insert into metadata_vector_documents (
                document_id, object_type, object_id, name, code, graph_path, risk_level,
                embedding_text, embedding_model, embedding_dimension, sync_batch_id,
                embedding, indexed_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?)
            on conflict (document_id) do update set
                object_type = excluded.object_type,
                object_id = excluded.object_id,
                name = excluded.name,
                code = excluded.code,
                graph_path = excluded.graph_path,
                risk_level = excluded.risk_level,
                embedding_text = excluded.embedding_text,
                embedding_model = excluded.embedding_model,
                embedding_dimension = excluded.embedding_dimension,
                sync_batch_id = excluded.sync_batch_id,
                embedding = excluded.embedding,
                indexed_at = excluded.indexed_at,
                updated_at = excluded.updated_at
            """;
    }

    private String searchSql() {
        return """
            select document_id, embedding_model, 1 - (embedding <=> ?::vector) as similarity
            from metadata_vector_documents
            order by embedding <=> ?::vector
            limit ?
            """;
    }

    private void bindDocument(
        PreparedStatement statement,
        MetadataSearchDocument document,
        String text,
        EmbeddingVector vector,
        Instant now
    ) throws SQLException {
        statement.setString(1, document.getId());
        statement.setString(2, safe(document.getObjectType()));
        statement.setString(3, safe(document.getObjectId()));
        statement.setString(4, safe(document.getName()));
        statement.setString(5, safe(document.getCode()));
        statement.setString(6, safe(document.getGraphPath()));
        statement.setString(7, safe(document.getRiskLevel()));
        statement.setString(8, text);
        statement.setString(9, properties.embeddingModel());
        statement.setInt(10, vector.values().size());
        statement.setString(11, safe(document.getSyncBatchId()));
        statement.setString(12, toPgVectorLiteral(vector.values()));
        statement.setTimestamp(13, Timestamp.from(document.getIndexedAt() == null ? now : document.getIndexedAt()));
        statement.setTimestamp(14, Timestamp.from(now));
    }

    private String embeddingText(MetadataSearchDocument document) {
        return String.join(" ",
            safe(document.getName()),
            safe(document.getCode()),
            safe(document.getGraphPath()),
            safe(document.getObjectType()),
            safe(document.getEmbeddingText()),
            safe(document.getSearchText())
        ).replaceAll("\\s+", " ").trim();
    }

    private String toPgVectorLiteral(List<Double> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(Double.toString(values.get(i)));
        }
        return builder.append(']').toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
