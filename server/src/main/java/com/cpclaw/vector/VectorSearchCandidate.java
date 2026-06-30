package com.cpclaw.vector;

public record VectorSearchCandidate(
    String documentId,
    double similarity,
    String model,
    String reason
) {
}
