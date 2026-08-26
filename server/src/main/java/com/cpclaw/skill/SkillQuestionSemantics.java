package com.cpclaw.skill;

import com.cpclaw.cloudpivot.CloudPivotRuntimeProperties;
import java.util.List;

/**
 * Business-language semantics supplied by an installed Skill.  The platform
 * runtime only consumes these capabilities; it must not contain vocabulary
 * for a particular business domain or analysis style.
 */
public interface SkillQuestionSemantics {
    default boolean isCountQuestion(String content) { return false; }
    default boolean isAnalysisQuestion(String content) { return false; }
    default boolean isDetailCollectionQuestion(String content) { return false; }
    default boolean isSingleRecordDetailQuestion(String content) { return false; }
    default boolean isPlainListQuestion(String content) { return true; }
    default boolean requiresFullDimensionDetails(String content) { return false; }
    default List<String> requestedStatusFilters(String content) { return List.of(); }
    default QuestionPlan plan(String content) { return new QuestionPlan("DEFAULT", "NONE", 10); }
    default int requestedRecordOrdinal(String content) { return 1; }
    default int queryPageSize(String content, CloudPivotRuntimeProperties.Query query, boolean ownerFilterPresent) { return query.getListPageSize(); }
    default int queryRecordLimit(String content, CloudPivotRuntimeProperties.Query query, boolean ownerFilterPresent) { return query.getListRecordLimit(); }
    default String detectIntent(String content) { return "unknown"; }
    default boolean looksLikeAnalysis(String content) { return false; }
    default String analysisDimension(String content) { return ""; }
    default String filterSummary(String content) { return ""; }
    default String ownerFilter(String content) { return ""; }
    default List<String> expandSearchTerms(List<String> terms) { return terms == null ? List.of() : terms; }

    record QuestionPlan(String operation, String metric, int limit) {
        public boolean ranking() { return "RANKING".equals(operation); }
    }
}
