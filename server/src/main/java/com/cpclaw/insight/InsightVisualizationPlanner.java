package com.cpclaw.insight;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cpclaw.skill.yunshu.YunshuVisualizationPlanner;
import com.cpclaw.skill.yunshu.template.YunshuScenarioVisualizationTemplate;
import com.cpclaw.insight.dto.InsightReportDto.Chart;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Compatibility facade; visualization semantics belong to the Yunshu Skill. */
public class InsightVisualizationPlanner extends YunshuVisualizationPlanner {
    private final YunshuScenarioVisualizationTemplate template;
    public InsightVisualizationPlanner(ObjectMapper objectMapper) {
        super(objectMapper);
        this.template = new YunshuScenarioVisualizationTemplate(objectMapper);
    }

    @Override public Chart planStageDistribution(String entityName, String question, CloudPivotDataItem field, Map<String, Long> values, long coverage, long total) { return template.planStageDistribution(entityName, question, field, values, coverage, total); }
    @Override public Chart planStageAmounts(String entityName, String question, CloudPivotDataItem field, Map<String, Double> values, long coverage, long total) { return template.planStageAmounts(field, values); }
    @Override public Chart planMonthlyTrend(Map<String, Long> values) { return template.planMonthlyTrend(values); }
    @Override public Optional<Chart> planBusinessFlow(List<String> labels, List<Double> values, boolean relationsVerified) { return template.planBusinessFlow(labels, values, relationsVerified); }
}
