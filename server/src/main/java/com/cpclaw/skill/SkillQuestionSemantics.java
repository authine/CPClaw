package com.cpclaw.skill;

import com.cpclaw.cloudpivot.CloudPivotRuntimeProperties;
import java.util.List;

/**
 * Business-language semantics supplied by an installed Skill.  The platform
 * runtime only consumes these capabilities; it must not contain vocabulary
 * for a particular business domain or analysis style.
 */
public interface SkillQuestionSemantics {
    boolean isCountQuestion(String content);
    boolean isAnalysisQuestion(String content);
    boolean isDetailCollectionQuestion(String content);
    boolean isSingleRecordDetailQuestion(String content);
    boolean isNewOldCustomerQuestion(String content);
    boolean isOwnerOpportunityRankingQuestion(String content);
    boolean isStatusAmountAggregationQuestion(String content);
    boolean isAmountAggregationQuestion(String content);
    boolean isAmountRankingQuestion(String content);
    boolean isStageDistributionQuestion(String content);
    boolean isYearlyDistributionQuestion(String content);
    boolean isProvinceDistributionQuestion(String content);
    boolean isBroadBusinessAnalysisQuestion(String content);
    boolean isPlainListQuestion(String content);
    boolean requiresCompleteAggregation(String content);
    boolean requiresFullDimensionDetails(String content);
    List<String> requestedStatusFilters(String content);
    boolean statusMatches(String actualStatus, List<String> targetStatuses);
    QuestionPlan plan(String content);
    int requestedRecordOrdinal(String content);
    int queryPageSize(String content, CloudPivotRuntimeProperties.Query query, boolean ownerFilterPresent);
    int queryRecordLimit(String content, CloudPivotRuntimeProperties.Query query, boolean ownerFilterPresent);
    String detectIntent(String content);
    boolean looksLikeAnalysis(String content);
    String analysisDimension(String content);
    String filterSummary(String content);
    String ownerFilter(String content);
    List<String> expandSearchTerms(List<String> terms);

    record QuestionPlan(String operation, String metric, int limit) {
        public boolean ranking() { return "RANKING".equals(operation); }
    }
}
