package com.cpclaw.mcp;

import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Host-facing renderer. Structured hosts receive an artifact; text-only hosts
 * receive the same business conclusion as Markdown rather than a transport
 * acknowledgement.
 */
@Component
public class McpTaskExperienceRenderer {

    public RenderedInsight renderInsight(InsightExecutionResult result) {
        InsightReportDto report = result.report();
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("type", "data_insight");
        artifact.put("title", report.title());
        artifact.put("subject", report.subject());
        artifact.put("period", report.periodLabel());
        artifact.put("scope", report.scopeLabel());
        artifact.put("confidence", report.confidence());
        artifact.put("kpis", report.kpis());
        artifact.put("charts", report.charts());
        artifact.put("sections", report.sections());
        artifact.put("warnings", report.warnings());
        artifact.put("sources", report.dataSources());
        artifact.put("followUps", report.relatedQuestions());
        return new RenderedInsight(markdown(result.answer(), report), Map.copyOf(artifact));
    }

    public String markdown(TaskExperienceEnvelope envelope) {
        if (envelope == null) return "任务执行失败，请稍后重试。";
        String message = text(envelope.output().get("message"));
        Object artifact = envelope.output().get("artifact");
        if (artifact instanceof Map<?, ?> map && "data_insight".equals(text(map.get("type")))) return message;
        StringBuilder result = new StringBuilder(message);
        Object raw = envelope.output().get("result");
        if (raw instanceof Map<?, ?> data) appendResult(result, data);
        if ("needs_input".equals(envelope.task().status())) result.append("\n\n请补充上述信息后继续。");
        if ("confirmation_required".equals(envelope.task().status())) result.append("\n\n> 此操作尚未执行，需在 CPClaw 的可信确认界面确认后才会继续。");
        return result.toString().trim();
    }

    private String markdown(String narrative, InsightReportDto report) {
        StringBuilder value = new StringBuilder();
        if (!isBlank(narrative)) value.append(narrative.trim()).append("\n\n");
        value.append("### 数据范围与口径\n")
            .append("- 范围：").append(report.scopeLabel()).append("\n")
            .append("- 期间：").append(report.periodLabel()).append("\n")
            .append("- 置信度：").append(report.confidence()).append("\n\n");
        if (!report.kpis().isEmpty()) {
            value.append("### 核心指标\n");
            report.kpis().forEach(kpi -> value.append("- ").append(kpi.label()).append("：")
                .append(kpi.value()).append(isBlank(kpi.unit()) ? "" : kpi.unit())
                .append(isBlank(kpi.description()) ? "" : "（" + kpi.description() + "）").append("\n"));
            value.append("\n");
        }
        if (!report.charts().isEmpty()) {
            value.append("### 图表数据\n");
            report.charts().forEach(chart -> {
                value.append("- ").append(chart.title()).append("：");
                List<String> points = new ArrayList<>();
                for (InsightReportDto.Series series : chart.series()) {
                    for (int i = 0; i < Math.min(chart.labels().size(), series.values().size()); i++) {
                        points.add((chart.series().size() > 1 ? series.name() + "-" : "") + chart.labels().get(i) + " " + series.values().get(i));
                    }
                }
                value.append(String.join("；", points)).append("\n");
            });
            value.append("\n");
        }
        if (!report.warnings().isEmpty()) value.append("> 数据口径提示：").append(String.join("；", report.warnings())).append("\n\n");
        if (!report.relatedQuestions().isEmpty()) {
            value.append("### 可继续追问\n");
            report.relatedQuestions().forEach(question -> value.append("- ").append(question).append("\n"));
        }
        return value.toString().trim();
    }

    private void appendResult(StringBuilder result, Map<?, ?> data) {
        String cardType = text(data.get("cardType"));
        if ("data-table".equals(cardType) || "analysis-data".equals(cardType)) {
            result.append("\n\n### 查询结果\n")
                .append("- 对象：").append(text(data.get("entityName"))).append("\n")
                .append("- 总数：").append(text(data.get("total"))).append("\n");
            appendSummaries(result, data.get("records"));
        } else if ("workflow-list".equals(cardType)) {
            result.append("\n\n### 流程结果\n- 总数：").append(text(data.get("total"))).append("\n");
            appendSummaries(result, data.get("items"));
        }
    }

    private void appendSummaries(StringBuilder result, Object value) {
        if (!(value instanceof List<?> records) || records.isEmpty()) return;
        result.append("\n| 序号 | 摘要 |\n| --- | --- |\n");
        int index = 1;
        for (Object record : records.stream().limit(20).toList()) {
            String summary = record instanceof Map<?, ?> map ? text(map.get("summary")) : text(record);
            if (!isBlank(summary)) result.append("| ").append(index++).append(" | ").append(summary.replace("|", "\\|").replace("\n", " ")).append(" |\n");
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    public record RenderedInsight(String markdown, Map<String, Object> artifact) { }
}
