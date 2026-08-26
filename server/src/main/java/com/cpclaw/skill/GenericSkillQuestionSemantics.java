package com.cpclaw.skill;

import com.cpclaw.cloudpivot.CloudPivotRuntimeProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/** Neutral baseline semantics. It recognizes interaction shape only. */
@Component
public final class GenericSkillQuestionSemantics implements SkillQuestionSemantics {
    @Override public boolean isCountQuestion(String content) { return hasAny(content, "多少", "数量", "总数", "计数"); }
    @Override public boolean isAnalysisQuestion(String content) { return hasAny(content, "分析", "洞察", "概况", "趋势", "比较", "分布", "统计"); }
    @Override public boolean isDetailCollectionQuestion(String content) { return hasAny(content, "列表", "清单", "明细"); }
    @Override public boolean isSingleRecordDetailQuestion(String content) { return hasAny(content, "第一条", "第1条", "指定记录", "记录详情"); }
    @Override public boolean isPlainListQuestion(String content) { return !isCountQuestion(content) && !isAnalysisQuestion(content); }
    @Override public boolean requiresFullDimensionDetails(String content) { return isAnalysisQuestion(content) || isDetailCollectionQuestion(content); }
    @Override public int requestedRecordOrdinal(String content) { return 1; }
    @Override public int queryPageSize(String content, CloudPivotRuntimeProperties.Query query, boolean ignored) { return isCountQuestion(content) ? query.getCountPageSize() : isAnalysisQuestion(content) ? query.getAnalysisPageSize() : query.getListPageSize(); }
    @Override public int queryRecordLimit(String content, CloudPivotRuntimeProperties.Query query, boolean ignored) { return isCountQuestion(content) ? query.getCountRecordLimit() : isAnalysisQuestion(content) ? query.getAnalysisRecordLimit() : query.getListRecordLimit(); }
    @Override public String detectIntent(String content) {
        if (hasAny(content, "处理待办", "审批通过", "审批驳回", "同意", "驳回", "转交", "撤回")) return "workflow_action";
        if (hasAny(content, "待办", "流程", "审批", "工作项", "发起", "已办")) return "query_workflow";
        return isAnalysisQuestion(content) ? "analyze_data" : isCountQuestion(content) || isDetailCollectionQuestion(content) ? "query_data" : "unknown";
    }
    private boolean hasAny(String value, String... terms) { if (value == null) return false; for (String term : terms) if (value.contains(term)) return true; return false; }
}
