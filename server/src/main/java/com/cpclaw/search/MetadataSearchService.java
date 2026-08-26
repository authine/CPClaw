package com.cpclaw.search;

import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import com.cpclaw.vector.MetadataVectorSearch;
import com.cpclaw.vector.VectorSearchCandidate;
import com.cpclaw.skill.SkillQuestionSemantics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class MetadataSearchService {

    private static final Logger log = LoggerFactory.getLogger(MetadataSearchService.class);

    private static final List<String> NOISE_WORDS = List.of(
        "帮我", "请", "一下", "系统中的", "系统中", "系统内", "系统", "信息", "数据", "情况", "怎么样", "怎么", "如何", "列表", "明细",
        "进行", "处理", "操作", "做", "全部", "所有", "相关", "下面", "下的", "对应", "关联", "分析", "洞察", "诊断", "趋势", "查询", "查看",
        "统计", "汇总", "数量", "量", "总计", "了解", "新增", "创建", "写入", "修改", "提交", "删除", "作废", "填写", "给", "第一条",
        "第二条", "第三条", "上一条", "刚才", "这个", "它", "写一条", "每年", "按年", "年度", "年份", "年", "一共", "总共", "共有",
        "中的", "的", "里", "有哪些", "有哪", "多少", "几条", "几个", "吗", "呢", "嘛", "一个", "一条", "一项", "整体", "经营", "概况", "报告", "全面", "综合"
    );
    private static final List<String> SECONDARY_OBJECT_MARKERS = List.of(
        "管理", "分配", "变更", "转移", "统计", "报表", "滚动", "持续", "查询", "修正", "基础信息", "池", "来源", "规则", "原因", "分类", "层级", "test", "测试"
    );

    private final MetadataSearchDocumentRepository searchDocumentRepository;
    private final MetadataVectorSearch metadataVectorSearch;
    private final SkillQuestionSemantics questionSemantics;

    @Autowired
    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository, MetadataVectorSearch metadataVectorSearch, SkillQuestionSemantics questionSemantics) {
        this.searchDocumentRepository = searchDocumentRepository;
        this.metadataVectorSearch = metadataVectorSearch;
        this.questionSemantics = questionSemantics;
    }

    public MetadataSearchService(MetadataSearchDocumentRepository searchDocumentRepository, MetadataVectorSearch metadataVectorSearch) {
        // Keep the framework-level search service independent from any
        // installed domain template. A template provider may be injected by
        // the application context; this fallback only performs generic text
        // expansion and never adds business vocabulary.
        this(searchDocumentRepository, metadataVectorSearch, new SkillQuestionSemantics() { });
    }

    public List<MetadataSearchResult> searchLocalMetadata(String query) {
        SearchQuery searchQuery = analyzeQuery(query);
        List<String> directIds = searchQuery.searchQueries().stream()
            .flatMap(searchText -> searchDocumentRepository.searchByText(searchText).stream())
            .map(MetadataSearchDocument::getId)
            .distinct()
            .toList();
        Map<String, VectorSearchCandidate> vectorCandidates = vectorCandidates(searchQuery);

        return searchDocumentRepository.findAll().stream()
            .map(document -> scoreDocument(searchQuery, document, directIds.contains(document.getId()), vectorCandidates.get(document.getId())))
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
                matchReason(searchQuery, item)
            ))
            .toList();
    }

    private Map<String, VectorSearchCandidate> vectorCandidates(SearchQuery query) {
        if (!metadataVectorSearch.enabled()) {
            return Map.of();
        }
        Map<String, VectorSearchCandidate> candidates = new HashMap<>();
        List<VectorSearchCandidate> results;
        try {
            results = metadataVectorSearch.search(vectorQueryText(query));
        } catch (RuntimeException exception) {
            log.info("metadata vector search skipped: {}", exception.getMessage());
            return Map.of();
        }
        for (VectorSearchCandidate candidate : results) {
            candidates.putIfAbsent(candidate.documentId(), candidate);
        }
        return candidates;
    }

    private String vectorQueryText(SearchQuery query) {
        List<String> values = new ArrayList<>();
        addDistinct(values, query.raw());
        query.terms().forEach(term -> addDistinct(values, term));
        query.expandedTerms().forEach(term -> addDistinct(values, term));
        query.appHints().forEach(hint -> addDistinct(values, hint));
        addDistinct(values, query.targetTerm());
        return String.join(" ", values);
    }

    private SearchQuery analyzeQuery(String query) {
        String safeQuery = query == null ? "" : query.trim();
        String normalized = safeQuery.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        List<String> terms = businessTerms(safeQuery, normalized);
        List<String> expandedTerms = expandedTerms(terms);
        List<String> appHints = List.of();
        String targetTerm = targetBusinessTerm(safeQuery, normalized, terms);
        List<String> searchQueries = searchQueries(safeQuery, terms, appHints);
        return new SearchQuery(safeQuery, safeQuery.toLowerCase(Locale.ROOT), normalized, terms, expandedTerms, appHints, targetTerm, searchQueries);
    }

    private List<String> businessTerms(String query, String normalized) {
        String value = query == null ? "" : query.trim();
        value = value.replaceAll("(一共|总共|共有)?有?多少[条个项笔份单]?", " ");
        value = value.replaceAll("(一共|总共|共有)?有?几[条个项笔份单]?", " ");
        for (String noise : NOISE_WORDS) {
            value = value.replace(noise, " ");
        }
        String[] parts = value.split("[\\s,，。；;：:、?？!！]+");
        List<String> terms = new ArrayList<>();
        for (String part : parts) {
            String term = part.trim();
            if (term.length() >= 2) addDistinct(terms, canonicalTerm(term));
        }
        return terms;
    }

    private List<String> expandedTerms(List<String> terms) {
        List<String> expanded = new ArrayList<>();
        for (String term : terms) {
            addDistinct(expanded, term);
            addDistinct(expanded, canonicalTerm(term));
        }
        questionSemantics.expandSearchTerms(terms).forEach(term -> addDistinct(expanded, term));
        return expanded;
    }

    private List<String> appHints(String normalized) {
        return List.of();
    }

    private List<String> searchQueries(String raw, List<String> terms, List<String> appHints) {
        List<String> queries = new ArrayList<>();
        addDistinct(queries, raw);
        for (String term : terms) {
            addDistinct(queries, term);
            for (String hint : appHints) {
                addDistinct(queries, hint + " " + term);
            }
        }
        return queries;
    }

    private String canonicalTerm(String term) {
        return term;
    }

    private String targetBusinessTerm(String query, String normalized, List<String> terms) {
        if (terms.isEmpty()) return "";
        String last = canonicalTerm(terms.getLast());
        List<String> expanded = questionSemantics.expandSearchTerms(List.of(last));
        return expanded.isEmpty() ? last : expanded.getLast();
    }

    private ScoredDocument scoreDocument(SearchQuery query, MetadataSearchDocument document, boolean directHit, VectorSearchCandidate vectorCandidate) {
        List<String> reasons = new ArrayList<>();
        int recallScore = recallScore(query, document, directHit, reasons);
        int vectorScore = vectorScore(vectorCandidate, reasons);
        if (recallScore <= 0 && vectorScore <= 0) {
            return new ScoredDocument(document, 0, reasons);
        }
        int score = recallScore + vectorScore + rankingScore(query, document, reasons);
        return new ScoredDocument(document, score, reasons);
    }

    private int vectorScore(VectorSearchCandidate candidate, List<String> reasons) {
        if (candidate == null || candidate.similarity() <= 0) {
            return 0;
        }
        int score = Math.max(10, Math.min(95, (int) Math.round(candidate.similarity() * 90)));
        reasons.add(candidate.reason());
        return score;
    }

    private int recallScore(SearchQuery query, MetadataSearchDocument document, boolean directHit, List<String> reasons) {
        String name = safe(document.getName());
        String code = safe(document.getCode());
        String graphPath = safe(document.getGraphPath());
        String searchText = safe(document.getSearchText());
        String haystack = String.join(" ", name, code, graphPath, searchText).toLowerCase(Locale.ROOT);
        int score = 0;

        if (directHit) {
            score += 35;
            reasons.add("全文/编码直接召回");
        }
        if (hasText(query.raw()) && (query.raw().contains(name) || searchText.contains(query.raw()))) {
            score += 220;
            reasons.add("原始查询与元数据名称/描述匹配");
        }
        if (hasText(code) && query.lower().contains(code.toLowerCase(Locale.ROOT))) {
            score += 180;
            reasons.add("编码精确召回");
        }
        for (String term : query.terms()) {
            String canonical = canonicalTerm(term);
            if (name.equals(term) || name.equals(canonical)) {
                score += 180;
                reasons.add("名称精确召回:" + canonical);
            } else if (name.contains(term) || name.contains(canonical)) {
                score += 80;
                reasons.add("名称包含召回:" + canonical);
            }
            if (code.equalsIgnoreCase(term)) {
                score += 150;
                reasons.add("编码精确召回:" + term);
            } else if (hasText(term) && code.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))) {
                score += 65;
                reasons.add("编码包含召回:" + term);
            }
            if (containsPathSegment(graphPath, term)) {
                score += 75;
                reasons.add("应用路径召回:" + term);
            }
            if (haystack.contains(term.toLowerCase(Locale.ROOT))) {
                score += 30;
                reasons.add("全文关键词召回:" + term);
            }
        }
        for (String expanded : query.expandedTerms()) {
            if (!query.terms().contains(expanded) && haystack.contains(expanded.toLowerCase(Locale.ROOT))) {
                score += 20;
                reasons.add("别名召回:" + expanded);
            }
        }
        if (hasCompoundBusinessTerms(query) && hasText(query.targetTerm())) {
            if (name.equals(query.targetTerm())) {
                score += 90;
                reasons.add("复合业务词目标对象:" + query.targetTerm());
            } else if (name.contains(query.targetTerm())) {
                score += 35;
                reasons.add("复合业务词相关对象:" + query.targetTerm());
            }
        }
        return score;
    }

    private int rankingScore(SearchQuery query, MetadataSearchDocument document, List<String> reasons) {
        List<String> terms = query.terms();
        String name = safe(document.getName());
        String code = safe(document.getCode());
        String graphPath = safe(document.getGraphPath());
        String searchText = safe(document.getSearchText());
        String haystack = String.join(" ", graphPath, code, searchText).toLowerCase(Locale.ROOT);

        int score = "entity".equals(document.getObjectType()) ? 50 : 0;
        if ("entity".equals(document.getObjectType())) {
            reasons.add("对象类型=业务模型");
        }
        for (String term : terms) {
            String canonical = canonicalTerm(term);
            if (name.equals(term) || name.equals(canonical)) {
                score += 120;
            } else if (name.contains(term) || name.contains(canonical)) {
                score += 60;
            }
        }
        if (hasText(query.targetTerm()) && name.equals(query.targetTerm())) {
            score += 520;
            reasons.add("目标业务对象精确匹配:" + query.targetTerm());
        }

        if (hasText(code) && query.raw().trim().equalsIgnoreCase(code)) {
            score += 520;
            reasons.add("code exact input match:" + code);
        }

        score += explicitAppPathScore(query, graphPath, reasons);

        for (String secondary : SECONDARY_OBJECT_MARKERS) {
            if (name.toLowerCase(Locale.ROOT).contains(secondary.toLowerCase(Locale.ROOT)) || code.toLowerCase(Locale.ROOT).contains(secondary.toLowerCase(Locale.ROOT))) {
                score -= 45;
                reasons.add("辅助/测试对象降权:" + secondary);
            }
        }
        return score;
    }

    private int explicitAppPathScore(SearchQuery query, String graphPath, List<String> reasons) {
        List<String> appSegments = appPathSegments(graphPath);
        int score = 0;
        for (String segment : appSegments) {
            if (queryContains(query.raw(), segment)) {
                score += 180;
                reasons.add("显式应用路径匹配:" + segment);
            }
        }
        return score;
    }

    private boolean hasCompoundBusinessTerms(SearchQuery query) {
        return query.terms().stream().map(this::canonicalTerm).distinct().count() >= 2;
    }

    private boolean containsPathSegment(String graphPath, String term) {
        if (!hasText(graphPath) || !hasText(term)) {
            return false;
        }
        for (String segment : graphPath.split("/")) {
            String value = segment.trim();
            if (value.equalsIgnoreCase(term) || value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> appPathSegments(String graphPath) {
        if (!hasText(graphPath)) {
            return List.of();
        }
        String[] segments = graphPath.split("/");
        List<String> values = new ArrayList<>();
        for (int i = 0; i < segments.length - 1; i++) {
            String segment = segments[i].trim();
            if (hasText(segment)) {
                values.add(segment);
            }
        }
        return values;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean queryContains(String query, String value) {
        return query != null && value != null && query.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private void addDistinct(List<String> values, String value) {
        if (hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String matchReason(SearchQuery query, ScoredDocument item) {
        String termText = query.terms().isEmpty() ? "原始查询" : String.join("、", query.terms());
        String reasonText = item.reasons().stream().distinct().limit(6).reduce((left, right) -> left + "；" + right).orElse("无显式召回原因");
        String retrievalMode = item.reasons().stream().anyMatch(reason -> reason.startsWith("向量语义召回"))
            ? "混合检索匹配"
            : "确定性检索匹配";
        return "按真实云枢 Metadata Index " + retrievalMode + "；业务关键词=" + termText
            + "；目标对象=" + (query.targetTerm().isBlank() ? "未识别" : query.targetTerm())
            + "；graphPath=" + safe(item.document().getGraphPath())
            + "；score=" + item.score()
            + "；召回依据=" + reasonText;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public MetadataSearchResult bestMatch(String query) {
        SearchQuery searchQuery = analyzeQuery(query);
        List<MetadataSearchResult> results = searchLocalMetadata(query);
        MetadataSearchResult entityMatch = bestBusinessEntityMatch(searchQuery);
        if (entityMatch != null) {
            return entityMatch;
        }
        if (!results.isEmpty()) {
            return results.getFirst();
        }
        return new MetadataSearchResult("unknown", "", "未匹配到本地元数据", "", "", "low", "本地 Metadata Index 暂无匹配，请先初始化云枢元数据");
    }

    /**
     * A business request targets a form, not an identically named field on a
     * different form. Prefer a semantically matching executable entity.
     */
    private MetadataSearchResult bestBusinessEntityMatch(SearchQuery query) {
        if (query.targetTerm().isBlank()) {
            return null;
        }
        return searchDocumentRepository.findAll().stream()
            .filter(document -> "entity".equals(document.getObjectType()))
            .map(document -> scoreDocument(query, document, false, null))
            .filter(item -> item.score() > 0 && entityMatchesTarget(item.document(), query.targetTerm()))
            .max(Comparator.comparingInt(item -> primaryEntityScore(item, query.targetTerm())))
            .map(item -> new MetadataSearchResult(
                item.document().getObjectType(),
                item.document().getObjectId(),
                item.document().getName(),
                item.document().getCode(),
                item.document().getGraphPath(),
                item.document().getRiskLevel(),
                matchReason(query, item) + "；已优先选择可执行业务对象"
            ))
            .orElse(null);
    }

    private boolean entityMatchesTarget(MetadataSearchDocument document, String targetTerm) {
        String name = safe(document.getName()).toLowerCase(Locale.ROOT);
        String code = safe(document.getCode()).toLowerCase(Locale.ROOT);
        String target = targetTerm.toLowerCase(Locale.ROOT);
        return name.contains(target) || code.contains(target);
    }

    private int primaryEntityScore(ScoredDocument item, String targetTerm) {
        MetadataSearchDocument document = item.document();
        String name = safe(document.getName()).toLowerCase(Locale.ROOT);
        String target = targetTerm.toLowerCase(Locale.ROOT);
        int score = item.score() + (name.equals(target) ? 340 : 220);
        if (SECONDARY_OBJECT_MARKERS.stream().anyMatch(marker -> name.contains(marker.toLowerCase(Locale.ROOT)))) {
            score -= 220;
        }
        return score;
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

    private record SearchQuery(String raw, String lower, String normalized, List<String> terms, List<String> expandedTerms, List<String> appHints, String targetTerm, List<String> searchQueries) {
    }

    private record ScoredDocument(MetadataSearchDocument document, int score, List<String> reasons) {
    }
}
