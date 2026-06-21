package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.util.ArrayList;
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
        List<MetadataSearchResult> directResults = searchDocumentRepository.searchByText(safeQuery).stream()
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
        if (!directResults.isEmpty()) {
            return directResults;
        }
        return searchByBusinessTerms(safeQuery);
    }

    private List<MetadataSearchResult> searchByBusinessTerms(String query) {
        List<String> terms = businessTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        return searchDocumentRepository.findAll().stream()
            .map(document -> new ScoredDocument(document, businessTermScore(terms, document)))
            .filter(item -> item.score() > 0)
            .sorted(Comparator.comparingInt(ScoredDocument::score).reversed())
            .limit(10)
            .map(item -> new MetadataSearchResult(
                item.document().getObjectType(),
                item.document().getObjectId(),
                item.document().getName(),
                item.document().getCode(),
                item.document().getGraphPath(),
                item.document().getRiskLevel(),
                "按业务关键词命中本地 Metadata Index：" + String.join("、", terms)
            ))
            .toList();
    }

    private List<String> businessTerms(String query) {
        String value = query == null ? "" : query.trim();
        value = value.replaceAll("(一共|总共|共有)?有?多少[条个项笔份单]?", " ");
        value = value.replaceAll("(一共|总共|共有)?有?几[条个项笔份单]?", " ");
        for (String noise : List.of("帮我", "请", "一下", "系统中的", "系统中", "系统", "信息", "数据", "情况", "列表", "明细", "进行", "处理", "操作", "做", "一下", "分析", "洞察", "诊断", "趋势", "查询", "查看", "统计", "汇总", "数量", "总计", "了解", "新增", "创建", "写入", "修改", "提交", "删除", "作废", "填写", "给", "第一条", "第二条", "第三条", "上一条", "刚才", "这个", "它", "写一条", "跟进记录", "跟进", "记录", "一共", "总共", "共有", "中的", "的", "吗", "呢", "嘛")) {
            value = value.replace(noise, " ");
        }
        String[] parts = value.split("[\\s,，。；;：:、?？!！]+");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            String term = part.trim();
            if (term.length() >= 2 && !terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    private int businessTermScore(List<String> terms, MetadataSearchDocument document) {
        String haystack = String.join(" ", safe(document.getName()), safe(document.getCode()), safe(document.getGraphPath()), safe(document.getSearchText())).toLowerCase();
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term.toLowerCase())) {
                score += 10;
            }
        }
        return score;
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

    private record ScoredDocument(MetadataSearchDocument document, int score) {
    }
}
