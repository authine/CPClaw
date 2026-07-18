package com.cpclaw.insight.dto;

import java.util.List;

public record InsightReportDto(
    String title,
    String subject,
    String periodLabel,
    String scopeLabel,
    String confidence,
    List<Kpi> kpis,
    List<Chart> charts,
    List<Section> sections,
    List<String> relatedQuestions,
    List<String> dataSources,
    List<String> warnings
) {
    public record Kpi(
        String label,
        String value,
        String unit,
        String tone,
        String description
    ) {
    }

    public record Chart(
        String id,
        String type,
        String title,
        String unit,
        String semantic,
        String description,
        List<String> labels,
        List<Series> series
    ) {
        public Chart(String id, String type, String title, String unit, List<String> labels, List<Series> series) {
            this(id, type, title, unit, "comparison", "", labels, series);
        }
    }

    public record Series(String name, List<Double> values) {
    }

    public record Section(String title, List<String> findings) {
    }
}
