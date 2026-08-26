package com.cpclaw.skill.yunshu.runtime;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Default operation-shape planner; it contains no business object vocabulary. */
@Component
public final class DefaultYunshuIntentPlanner implements YunshuIntentPlanner {
    @Override
    public String classify(String query) {
        String value = normalize(query);
        if (contains(value, "同意", "驳回", "审核", "批准", "批复", "转交", "撤回", "处理待办", "提交审批", "发起流程", "提交流程", "启动流程")) return "workflow_action";
        if (contains(value, "待办", "未完成", "已办", "已处理", "我发起", "流程", "审批")) return "workflow_query";
        if (contains(value, "删除", "移除", "作废")) return "delete_data";
        if (contains(value, "新增", "创建", "填单", "填表")) return "create_data";
        if (contains(value, "修改", "更新", "变更", "保存")) return "update_data";
        if (contains(value, "分析", "报告", "趋势", "分布", "占比", "对比", "排名", "阶段", "较高", "较低")) return "analyze_data";
        if (contains(value, "查", "查询", "统计", "多少", "几条", "列表", "数据", "情况")) return "query_data";
        return "clarify_intent";
    }

    @Override
    public String normalizeModelIntent(String value, String fallback) {
        String intent = normalize(value);
        return List.of("query_data", "analyze_data", "create_data", "update_data", "delete_data", "clarify_intent", "workflow_query", "workflow_action").contains(intent)
            ? intent : fallback;
    }

    @Override
    public String workflowApiCode(String query) {
        String value = normalize(query);
        if (contains(value, "已办", "已处理")) return "workflow_list_finished";
        if (contains(value, "我发起", "发起")) return "workflow_list_started";
        return "workflow_list_pending";
    }

    private boolean contains(String value, String... terms) {
        for (String term : terms) if (value.contains(term)) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
