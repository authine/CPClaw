package com.cpclaw.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void batchesEmbeddingRequestsAndPreservesOriginalIndexes() throws Exception {
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        List<List<String>> receivedInputs = new CopyOnWriteArrayList<>();
        startEmbeddingServer(batchSizes, receivedInputs);

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties(), objectMapper);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            if (i == 1) {
                texts.add("   ");
            } else if (i == 33) {
                texts.add(null);
            } else {
                texts.add("doc-" + i);
            }
        }

        List<EmbeddingVector> vectors = client.embed(texts);

        assertEquals(68, vectors.size());
        assertIterableEquals(List.of(10, 10, 10, 10, 10, 10, 8), batchSizes);
        assertTrue(receivedInputs.stream().flatMap(List::stream).noneMatch(String::isBlank));

        List<Integer> indexes = vectors.stream().map(EmbeddingVector::index).toList();
        assertFalse(indexes.contains(1));
        assertFalse(indexes.contains(33));
        assertEquals(0, indexes.getFirst());
        assertEquals(69, indexes.getLast());
    }

    @Test
    void splitsFailedBatchesIntoSmallerRequests() throws Exception {
        List<Integer> batchSizes = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/embeddings", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            JsonNode input = objectMapper.readTree(requestBody).path("input");
            List<String> texts = new ArrayList<>();
            for (JsonNode item : input) {
                texts.add(item.asText());
            }
            batchSizes.add(texts.size());
            if (texts.size() > 5) {
                byte[] response = "{\"error\":\"too many inputs\"}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(400, response.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response);
                }
                return;
            }
            StringBuilder data = new StringBuilder("{\"data\":[");
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) {
                    data.append(',');
                }
                data.append("{\"index\":")
                    .append(i)
                    .append(",\"embedding\":[")
                    .append(i + 1)
                    .append("]}");
            }
            data.append("]}");
            byte[] response = data.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();

        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(properties(), objectMapper);
        List<EmbeddingVector> vectors = client.embed(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"));

        assertEquals(11, vectors.size());
        assertIterableEquals(List.of(10, 5, 5, 1), batchSizes);
        assertEquals(0, vectors.getFirst().index());
        assertEquals(10, vectors.getLast().index());
    }

    private void startEmbeddingServer(List<Integer> batchSizes, List<List<String>> receivedInputs) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/compatible-mode/v1/embeddings", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            JsonNode input = objectMapper.readTree(requestBody).path("input");
            List<String> texts = new ArrayList<>();
            for (JsonNode item : input) {
                texts.add(item.asText());
            }
            batchSizes.add(texts.size());
            receivedInputs.add(texts);

            StringBuilder data = new StringBuilder("{\"data\":[");
            for (int i = 0; i < texts.size(); i++) {
                if (i > 0) {
                    data.append(',');
                }
                data.append("{\"index\":")
                    .append(i)
                    .append(",\"embedding\":[")
                    .append(i + 0.1)
                    .append(',')
                    .append(i + 0.2)
                    .append("]}");
            }
            data.append("]}");
            byte[] response = data.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        });
        server.start();
    }

    private VectorSearchProperties properties() {
        return new VectorSearchProperties(
            true,
            "",
            "",
            "",
            20,
            0.62,
            "http://127.0.0.1:" + server.getAddress().getPort() + "/compatible-mode/v1",
            "test-key",
            "text-embedding-v4"
        );
    }
}
