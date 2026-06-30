package com.cpclaw.vector;

import com.cpclaw.metadata.entity.MetadataSearchDocument;
import java.util.List;

public interface MetadataVectorSearch {
    boolean enabled();

    void indexDocuments(List<MetadataSearchDocument> documents);

    List<VectorSearchCandidate> search(String query);
}
