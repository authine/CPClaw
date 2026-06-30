package com.cpclaw.vector;

import java.util.List;

public interface EmbeddingClient {
    List<EmbeddingVector> embed(List<String> texts);
}
