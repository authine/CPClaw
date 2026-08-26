package com.cpclaw.skill.yunshu.runtime;

/**
 * Pluggable operation planner for the universal Yunshu Skill. The planner
 * classifies interaction shape only; object names, field aliases and business
 * rules remain metadata/template concerns.
 */
public interface YunshuIntentPlanner {
    String classify(String query);

    String normalizeModelIntent(String value, String fallback);

    String workflowApiCode(String query);

    default boolean isRead(String intent) {
        return "query_data".equals(intent) || "analyze_data".equals(intent);
    }

    default boolean isAnalysis(String intent) {
        return "analyze_data".equals(intent);
    }

    default boolean isWrite(String intent) {
        return "create_data".equals(intent) || "update_data".equals(intent) || "delete_data".equals(intent);
    }

    default boolean isWorkflow(String intent) {
        return "workflow_query".equals(intent);
    }

    default boolean isWorkflowAction(String intent) {
        return "workflow_action".equals(intent);
    }
}
