package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MetadataSearchService {

    private final MetadataSearchDocumentRepository searchDocumentRepository;

    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository) {
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<MetadataSearchResult> searchLocalMetadata(String query) {
        String safeQuery = query == null ? "" : query.trim();
        return searchDocumentRepository.searchByText(safeQuery).stream()
            .sorted(Comparator.comparingInt(document -> relevanceBoost(safeQuery, document.getGraphPath(), document.getCode(), document.getSearchText())))
            .map(document -> new MetadataSearchResult(
                document.getObjectType(),
                document.getObjectId(),
                document.getName(),
                document.getCode(),
                document.getGraphPath(),
                document.getRiskLevel(),
                "命中本地 Metadata Index"
            ))
            .toList();
    }

    private int relevanceBoost(String query, String graphPath, String code, String searchText) {
        if (!query.toLowerCase().contains("crm")) {
            return 0;
        }
        String haystack = String.join(" ", safe(graphPath), safe(code), safe(searchText)).toLowerCase();
        return haystack.contains("crm") ? 0 : 1;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public MetadataSearchResult bestMatch(String query) {
        List<MetadataSearchResult> results = searchLocalMetadata(query);
        if (!results.isEmpty()) {
            return results.getFirst();
        }
        return new MetadataSearchResult("unknown", "", "未匹配到本地元数据", "", "", "low", "本地 Metadata Index 暂无匹配，请先初始化云枢元数据");
    }
}
