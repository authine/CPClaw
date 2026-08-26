package com.cpclaw.skill.yunshu;

import com.cpclaw.insight.dto.InsightReportDto.Chart;
import com.cpclaw.insight.dto.InsightReportDto.Series;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generic visualization primitives. Domain labels and ordering come from templates. */
public class YunshuVisualizationPlanner {
    public YunshuVisualizationPlanner(ObjectMapper ignored) { }

    public Chart planStageDistribution(String entityName, String question, CloudPivotDataItem field, Map<String, Long> values, long coverage, long total) {
        return categorical("distribution", "bar", entityName, values, coverage, total, "按元数据分类字段统计");
    }
    public Chart planStageAmounts(String entityName, String question, CloudPivotDataItem field, Map<String, Double> values, long coverage, long total) {
        return numeric("metric-distribution", entityName, values, coverage, total, "按元数据数值字段汇总");
    }
    public Chart planMonthlyTrend(Map<String, Long> values) {
        Chart chart = categorical("time-series", "line", "时间序列", values, values.values().stream().mapToLong(Long::longValue).sum(), values.values().stream().mapToLong(Long::longValue).sum(), "按日期字段聚合");
        return new Chart(chart.id(), chart.type(), chart.title(), chart.unit(), "time_series", chart.description(), chart.labels(), chart.series());
    }
    public java.util.Optional<Chart> planBusinessFlow(List<String> labels, List<Double> values, boolean ignored) {
        return java.util.Optional.of(new Chart("flow", "bar", "流程分布", "条", "comparison", "按模板提供的分类顺序展示，避免误读为严格转化率。", labels == null ? List.of() : labels, List.of(new Series("数量", values == null ? List.of() : values))));
    }
    public Chart planBusinessFlow(String title, List<String> labels, List<Double> values, String description) {
        return new Chart("flow", "bar", title == null ? "流程分布" : title, "条", "ordered", description == null ? "按模板定义的分类顺序展示" : description, labels == null ? List.of() : labels, List.of(new Series("数量", values == null ? List.of() : values)));
    }
    private Chart categorical(String id, String type, String title, Map<String, ? extends Number> values, long coverage, long total, String description) {
        List<String> labels = new ArrayList<>(values == null ? Map.<String, Number>of().keySet() : values.keySet());
        List<Double> series = labels.stream().map(label -> values.get(label).doubleValue()).toList();
        return new Chart(id, type, title == null ? "分类分布" : title, "条", "composition", description, labels, List.of(new Series("数量", series)));
    }
    private Chart numeric(String id, String title, Map<String, Double> values, long coverage, long total, String description) {
        return categorical(id, "bar", title, values, coverage, total, description);
    }
}
