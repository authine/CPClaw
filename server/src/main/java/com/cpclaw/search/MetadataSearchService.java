package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class MetadataSearchService {

    private final MetadataSearchDocumentRepository searchDocumentRepository;

    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository) {
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<MetadataSearchResult> searchLocalMetadata(String query) {
        SemanticQuery semanticQuery = analyze(query);
        Map<String, MetadataSearchDocument> candidates = new LinkedHashMap<>();
        for (String searchQuery : semanticQuery.searchQueries()) {
            searchDocumentRepository.searchByText(searchQuery).forEach(document -> candidates.putIfAbsent(document.getId(), document));
        }
        if (candidates.isEmpty() && !semanticQuery.terms().isEmpty()) {
            searchDocumentRepository.findAll().stream()
                .filter(document -> relevanceScore(semanticQuery, document) > 0)
                .forEach(document -> candidates.putIfAbsent(document.getId(), document));
        }

        return candidates.values().stream()
            .filter(document -> semanticQuery.terms().isEmpty() || matchesSemanticTerms(semanticQuery, document))
            .sorted(Comparator.comparingInt((MetadataSearchDocument document) -> relevanceScore(semanticQuery, document)).reversed())
            .limit(10)
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

    private SemanticQuery analyze(String query) {
        String original = query == null ? "" : query.trim();
        String normalized = original.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        Set<String> terms = new LinkedHashSet<>();
        Set<String> appHints = new LinkedHashSet<>();

        if (containsAny(normalized, "crm", "客户关系", "销售管理")) {
            appHints.add("crm");
        }
        if (containsAny(normalized, "商机", "机会", "销售机会", "客户机会", "项目机会")) {
            terms.add("商机");
            appHints.add("crm");
        }
        if (containsAny(normalized, "线索", "销售线索", "客户线索")) {
            terms.add("线索");
            appHints.add("crm");
        }
        addTermIfPresent(normalized, terms, "客户");
        addTermIfPresent(normalized, terms, "联系人");
        addTermIfPresent(normalized, terms, "合同");
        addTermIfPresent(normalized, terms, "订单");
        addTermIfPresent(normalized, terms, "项目");
        addTermIfPresent(normalized, terms, "报价");
        if (containsAny(normalized, "回款", "收款")) {
            terms.add("回款");
        }
        addTermIfPresent(normalized, terms, "发票");
        addTermIfPresent(normalized, terms, "待办");
        addTermIfPresent(normalized, terms, "任务");
        addTermIfPresent(normalized, terms, "流程");
        addTermIfPresent(normalized, terms, "审批");

        List<String> searchQueries = new ArrayList<>();
        addSearchQuery(searchQueries, original);
        for (String term : terms) {
            addSearchQuery(searchQueries, term);
            for (String appHint : appHints) {
                addSearchQuery(searchQueries, appHint + " " + term);
            }
        }
        return new SemanticQuery(original, normalized, terms, appHints, searchQueries);
    }

    private int relevanceScore(SemanticQuery query, MetadataSearchDocument document) {
        String name = safe(document.getName()).toLowerCase(Locale.ROOT);
        String code = safe(document.getCode()).toLowerCase(Locale.ROOT);
        String graphPath = safe(document.getGraphPath()).toLowerCase(Locale.ROOT);
        String searchText = safe(document.getSearchText()).toLowerCase(Locale.ROOT);
        String haystack = String.join(" ", name, code, graphPath, searchText);
        int score = "entity".equals(document.getObjectType()) ? 10 : 0;

        for (String term : query.terms()) {
            String normalizedTerm = term.toLowerCase(Locale.ROOT);
            if (name.equals(normalizedTerm)) {
                score += 140;
            } else if (name.contains(normalizedTerm)) {
                score += 90;
            }
            if (query.normalized().contains(normalizedTerm)) {
                score += 60;
            }
            if (code.contains(normalizedTerm)) {
                score += 40;
            }
            if (graphPath.contains(normalizedTerm)) {
                score += 35;
            }
            if (searchText.contains(normalizedTerm)) {
                score += 25;
            }
        }

        for (String appHint : query.appHints()) {
            if ("crm".equals(appHint)) {
                if (graphPath.contains("zlcsstcrm")) {
                    score += 90;
                }
                if (haystack.contains("crm")) {
                    score += 60;
                }
            } else if (haystack.contains(appHint)) {
                score += 35;
            }
        }

        if (!query.original().isBlank()) {
            String lowerOriginal = query.original().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && lowerOriginal.contains(name)) {
                score += 90;
            }
            if (!code.isBlank() && lowerOriginal.contains(code)) {
                score += 70;
            }
        }
        return score;
    }

    private boolean matchesSemanticTerms(SemanticQuery query, MetadataSearchDocument document) {
        String name = safe(document.getName()).toLowerCase(Locale.ROOT);
        String code = safe(document.getCode()).toLowerCase(Locale.ROOT);
        String graphPath = safe(document.getGraphPath()).toLowerCase(Locale.ROOT);
        String searchText = safe(document.getSearchText()).toLowerCase(Locale.ROOT);
        String haystack = String.join(" ", name, code, graphPath, searchText);
        for (String term : query.terms()) {
            if (haystack.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void addTermIfPresent(String value, Set<String> terms, String term) {
        if (value.contains(term)) {
            terms.add(term);
        }
    }

    private void addSearchQuery(List<String> searchQueries, String query) {
        String value = query == null ? "" : query.trim();
        if (!value.isBlank() && !searchQueries.contains(value)) {
            searchQueries.add(value);
        }
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
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

    public List<MetadataSearchResult> suggestAvailableMetadata(int limit) {
        return searchDocumentRepository.findAll().stream()
            .sorted(Comparator
                .comparingInt((MetadataSearchDocument document) -> "entity".equals(document.getObjectType()) ? 0 : 1)
                .thenComparing(document -> safe(document.getName())))
            .limit(Math.max(0, limit))
            .map(document -> new MetadataSearchResult(
                document.getObjectType(),
                document.getObjectId(),
                document.getName(),
                document.getCode(),
                document.getGraphPath(),
                document.getRiskLevel(),
                "可选本地 Metadata Index 对象"
            ))
            .toList();
    }

    private record SemanticQuery(String original, String normalized, Set<String> terms, Set<String> appHints, List<String> searchQueries) {
    }
}
