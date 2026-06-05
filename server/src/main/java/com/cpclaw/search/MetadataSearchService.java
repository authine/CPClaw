package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MetadataSearchService {

    private final MetadataSearchDocumentRepository searchDocumentRepository;

    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository) {
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<MetadataSearchResult> searchLocalMetadata(String query) {
        String safeQuery = query == null || query.isBlank() ? "销售订单" : query.trim();
        return searchDocumentRepository.findTop10BySearchTextContainingIgnoreCaseOrderByCreatedAtDesc(safeQuery).stream()
            .map(document -> new MetadataSearchResult(
                document.getObjectType(),
                document.getObjectId(),
                document.getName(),
                document.getCode(),
                document.getGraphPath(),
                document.getRiskLevel(),
                "命中本地 Metadata Index：" + safeQuery
            ))
            .toList();
    }

    public MetadataSearchResult bestMatch(String query) {
        List<MetadataSearchResult> results = searchLocalMetadata(query);
        if (!results.isEmpty()) {
            return results.getFirst();
        }
        return new MetadataSearchResult("unknown", "", "未匹配到本地元数据", "", "", "low", "本地 Metadata Index 暂无匹配，请先同步模拟元数据");
    }
}
