package com.cpclaw.insight;

import com.cpclaw.insight.dto.InsightReportDto.Chart;
import com.cpclaw.insight.dto.InsightReportDto.Series;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class InsightVisualizationPlanner {

    private static final Set<String> OPTION_KEYS = Set.of("options", "items", "choices", "optionList", "dataSource");
    private static final List<String> OPTION_LABEL_KEYS = List.of("label", "name", "text", "title", "displayName", "value");

    private final ObjectMapper objectMapper;

    public InsightVisualizationPlanner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Chart planStageDistribution(
        String entityName,
        String question,
        CloudPivotDataItem stageField,
        Map<String, Long> values,
        long coverage,
        long total
    ) {
        List<String> labels = orderedStageLabels(stageField, values);
        boolean processStage = isProcessStage(question, stageField, labels);
        String type = processStage ? "funnel" : labels.size() <= 6 ? "donut" : "bar";
        String semantic = processStage ? "ordered_stage" : "composition";
        String description = processStage
            ? "漏斗宽度按当前阶段及后续阶段的累计数量计算，右侧保留当前阶段存量；用于观察推进层级，不代表严格的同批次转化率。"
            : type.equals("donut")
                ? "用于比较少量无序状态在整体中的构成占比。"
                : "状态类别较多，采用条形图便于比较和读取标签。";
        String title = entityName + (processStage ? "阶段漏斗" : "状态分布")
            + (coverage < total ? "（已识别 " + coverage + "/" + total + " 条）" : "");
        List<Double> currentValues = labels.stream().map(label -> values.getOrDefault(label, 0L).doubleValue()).toList();
        List<Series> series = processStage
            ? List.of(new Series("累计到达", cumulativeValues(currentValues)), new Series("当前阶段", currentValues))
            : List.of(new Series("数量", currentValues));
        return new Chart(
            "stage-distribution",
            type,
            title,
            "条",
            semantic,
            description,
            labels,
            series
        );
    }

    public Chart planStageAmounts(CloudPivotDataItem stageField, Map<String, Double> values) {
        List<String> labels = orderedStageLabels(stageField, values);
        return new Chart(
            "stage-amounts",
            "bar",
            "各阶段金额对比",
            "元",
            "comparison",
            "金额属于阶段间的横向比较指标，按业务阶段顺序排列，条形长度表示金额规模。",
            labels,
            List.of(new Series("金额", labels.stream().map(label -> values.getOrDefault(label, 0D)).toList()))
        );
    }

    public Chart planMonthlyTrend(Map<String, Long> values) {
        List<String> labels = new ArrayList<>(values.keySet());
        return new Chart(
            "monthly-trend",
            "line",
            "按月新增趋势",
            "条",
            "time_series",
            "时间序列按自然月份连续展示，用于观察变化方向和波动。",
            labels,
            List.of(new Series("新增", labels.stream().map(label -> values.getOrDefault(label, 0L).doubleValue()).toList()))
        );
    }

    public Optional<Chart> planBusinessFlow(List<String> labels, List<Double> values, boolean relationsVerified) {
        if (labels.size() < 2 || labels.size() != values.size()) return Optional.empty();
        boolean monotonic = isNonIncreasing(values);
        boolean conversionFunnel = relationsVerified && monotonic;
        String type = conversionFunnel ? "funnel" : "bar";
        String title = conversionFunnel ? "业务链路转化漏斗" : "业务链路规模与关联结果";
        String description = conversionFunnel
            ? "仅使用关联字段已核验且按业务顺序单调递减的数据展示转化漏斗。"
            : "当前各节点不是可直接相除的单调转化口径，采用条形对比避免误读为转化率。";
        return Optional.of(new Chart(
            "business-flow",
            type,
            title,
            "条",
            conversionFunnel ? "verified_conversion" : "comparison",
            description,
            labels,
            List.of(new Series("数量", values))
        ));
    }

    private boolean isProcessStage(String question, CloudPivotDataItem field, List<String> labels) {
        String fieldSignal = normalize((field == null ? "" : field.getName() + " " + field.getDataItemCode()));
        String questionSignal = normalize(question);
        if (containsAny(fieldSignal, "阶段", "stage", "进度", "pipeline")) return true;
        if (containsAny(questionSignal, "阶段", "漏斗", "推进过程", "pipeline")) return true;
        return labels.stream().filter(label -> stageRank(label, 0) < 2_000).count() >= Math.min(3, labels.size());
    }

    private <N extends Number> List<String> orderedStageLabels(CloudPivotDataItem field, Map<String, N> values) {
        List<String> visible = new ArrayList<>(values.keySet());
        if (visible.size() < 2) return visible;

        Map<String, String> visibleByKey = new LinkedHashMap<>();
        visible.forEach(label -> visibleByKey.putIfAbsent(normalize(label), label));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        for (String option : metadataOptionLabels(field)) {
            String visibleLabel = visibleByKey.get(normalize(option));
            if (visibleLabel != null) ordered.add(visibleLabel);
        }

        Map<String, Integer> sourceOrder = new LinkedHashMap<>();
        for (int index = 0; index < visible.size(); index++) sourceOrder.put(visible.get(index), index);
        visible.stream()
            .filter(label -> !ordered.contains(label))
            .sorted(Comparator
                .comparingInt((String label) -> stageRank(label, sourceOrder.getOrDefault(label, 0)))
                .thenComparingInt(label -> sourceOrder.getOrDefault(label, 0)))
            .forEach(ordered::add);
        return new ArrayList<>(ordered);
    }

    private List<String> metadataOptionLabels(CloudPivotDataItem field) {
        if (field == null || field.getRawJson() == null || field.getRawJson().isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(field.getRawJson());
            List<String> labels = new ArrayList<>();
            collectOptionLabels(root, labels);
            return labels.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void collectOptionLabels(JsonNode node, List<String> labels) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (OPTION_KEYS.contains(entry.getKey()) && value.isArray()) {
                    value.forEach(option -> extractOptionLabel(option).ifPresent(labels::add));
                }
                collectOptionLabels(value, labels);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectOptionLabels(child, labels));
        }
    }

    private Optional<String> extractOptionLabel(JsonNode option) {
        if (option == null || option.isNull()) return Optional.empty();
        if (option.isTextual() || option.isNumber()) return Optional.of(option.asText());
        if (!option.isObject()) return Optional.empty();
        for (String key : OPTION_LABEL_KEYS) {
            JsonNode value = option.get(key);
            if (value != null && (value.isTextual() || value.isNumber()) && !value.asText().isBlank()) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private int stageRank(String label, int fallbackIndex) {
        String value = normalize(label);
        if (containsAny(value, "线索", "潜在", "lead", "clue")) return 100;
        if (containsAny(value, "初步", "接洽", "识别", "initial", "contact")) return 200;
        if (containsAny(value, "商机确认", "机会确认", "立项")) return 300;
        if (containsAny(value, "需求", "requirement")) return 400;
        if (containsAny(value, "方案", "论证", "solution", "proposal")) return 500;
        if (containsAny(value, "招标", "采购", "bidding", "procurement")) return 600;
        if (containsAny(value, "谈判", "报价", "negotiation", "quote")) return 700;
        if (containsAny(value, "合同", "签约", "赢单", "成交", "contract", "signed", "won")) return 800;
        if (containsAny(value, "交付", "实施", "delivery", "implementation")) return 900;
        if (containsAny(value, "回款", "收款", "payment")) return 1_000;
        if (containsAny(value, "丢单", "停止", "终止", "取消", "lost", "cancel")) return 1_100;
        if (value.matches("^[a-e][-_].*")) return 800 - (value.charAt(0) - 'a') * 100;
        return 2_000 + fallbackIndex;
    }

    private boolean isNonIncreasing(List<Double> values) {
        for (int index = 1; index < values.size(); index++) {
            if (finite(values.get(index)) > finite(values.get(index - 1))) return false;
        }
        return true;
    }

    private List<Double> cumulativeValues(List<Double> currentValues) {
        List<Double> cumulative = new ArrayList<>(currentValues);
        double running = 0D;
        for (int index = currentValues.size() - 1; index >= 0; index--) {
            running += finite(currentValues.get(index));
            cumulative.set(index, running);
        }
        return cumulative;
    }

    private double finite(Double value) {
        return value != null && Double.isFinite(value) ? Math.max(0D, value) : 0D;
    }

    private boolean containsAny(String source, String... values) {
        for (String value : values) if (source.contains(normalize(value))) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s·:：/]+", "").trim();
    }
}
