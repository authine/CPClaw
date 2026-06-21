package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MetadataSearchService {

    private final MetadataSearchDocumentRepository searchDocumentRepository;

    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository) {
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<MetadataSearchResult> searchLocalMetadata(String query) {
        String safeQuery = query == null ? "" : query.trim();
        List<String> terms = businessTerms(query);

        Map<String, MetadataSearchDocument> candidates = new LinkedHashMap<>();
        searchDocumentRepository.searchByText(safeQuery).forEach(document -> candidates.put(document.getId(), document));
        if (!terms.isEmpty()) {
            searchDocumentRepository.findAll().stream()
                .filter(document -> businessTermScore(terms, document) > 0)
                .forEach(document -> candidates.put(document.getId(), document));
        }

        return candidates.values().stream()
            .map(document -> new ScoredDocument(document, businessTermScore(terms, document) + rankingScore(safeQuery, document)))
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
                matchReason(terms, item)
            ))
            .toList();
    }

    private List<String> businessTerms(String query) {
        String value = query == null ? "" : query.trim();
        value = value.replaceAll("(一共|总共|共有)?有?多少[条个项笔份单]?", " ");
        value = value.replaceAll("(一共|总共|共有)?有?几[条个项笔份单]?", " ");
        for (String noise : List.of("帮我", "请", "一下", "系统中的", "系统中", "系统", "信息", "数据", "情况", "怎么样", "怎么", "如何", "列表", "明细", "进行", "处理", "操作", "做", "一下", "分析", "洞察", "诊断", "趋势", "查询", "查看", "统计", "汇总", "数量", "量", "总计", "了解", "新增", "创建", "写入", "修改", "提交", "删除", "作废", "填写", "给", "第一条", "第二条", "第三条", "上一条", "刚才", "这个", "它", "写一条", "跟进记录", "跟进", "记录", "每年", "按年", "年度", "年份", "年", "一共", "总共", "共有", "中的", "的", "吗", "呢", "嘛")) {
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

    private int rankingScore(String query, MetadataSearchDocument document) {
        List<String> terms = businessTerms(query);
        String name = safe(document.getName());
        String code = safe(document.getCode());
        String graphPath = safe(document.getGraphPath());
        String searchText = safe(document.getSearchText());
        String haystack = String.join(" ", graphPath, code, searchText).toLowerCase();

        int score = "entity".equals(document.getObjectType()) ? 50 : 0;
        for (String term : terms) {
            if (name.equals(term)) {
                score += 120;
            } else if (name.contains(term)) {
                score += 60;
            }
        }

        if (isCrmCoreQuery(query, terms)) {
            if (graphPath.toLowerCase().startsWith("zlcsstcrm /")) {
                score += 120;
            }
            if (haystack.contains("zlcsstcrm") || code.toLowerCase().contains("crm")) {
                score += 80;
            }
        } else if (query.toLowerCase().contains("crm") && haystack.contains("crm")) {
            score += 80;
        }

        if (terms.contains("商机") && "int_bu_oppor".equalsIgnoreCase(code)) {
            score += 140;
        }
        if (terms.contains("客户") && "crm_customer".equalsIgnoreCase(code)) {
            score += 140;
        }

        for (String secondary : List.of("管理", "分配", "变更", "转移", "统计", "报表", "滚动", "持续", "查询", "修正", "基础信息", "test", "测试")) {
            if (name.toLowerCase().contains(secondary.toLowerCase()) || code.toLowerCase().contains(secondary.toLowerCase())) {
                score -= 45;
            }
        }
        return score;
    }

    private boolean isCrmCoreQuery(String query, List<String> terms) {
        String value = query == null ? "" : query.toLowerCase();
        return value.contains("crm") || terms.stream().anyMatch(term -> List.of("商机", "客户", "线索", "联系人", "销售订单", "合同").contains(term));
    }

    private String matchReason(List<String> terms, ScoredDocument item) {
        String termText = terms.isEmpty() ? "原始查询" : String.join("、", terms);
        return "按真实云枢 Metadata Index 匹配；业务关键词=" + termText + "；graphPath="
            + safe(item.document().getGraphPath()) + "；score=" + item.score();
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
