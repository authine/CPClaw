package com.cpclaw.mcp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.insight.dto.InsightReportDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class McpTaskExperienceRendererTests {

    private final McpTaskExperienceRenderer renderer = new McpTaskExperienceRenderer();

    @Test
    void suppliesCompleteMarkdownFallbackAndSafeStructuredInsightArtifact() {
        InsightReportDto report = new InsightReportDto(
            "yunshu-intelligent-inquiry", "云枢智能问数", 1,
            "在建项目分析", "项目", "全部时间", "当前可见数据", "high",
            List.of(new InsightReportDto.Kpi("项目数", "12", "条", "primary", "按已验证范围统计")),
            List.of(new InsightReportDto.Chart("stage", "bar", "项目阶段", "条", "distribution", "", List.of("在建"), List.of(new InsightReportDto.Series("项目", List.of(12D))))),
            List.of(new InsightReportDto.Section("核心结论", List.of("已确认 12 个在建项目。"))),
            List.of("查看按负责人分布"), List.of("项目"), List.of("阶段枚举已核验")
        );

        McpTaskExperienceRenderer.RenderedInsight rendered = renderer.renderInsight(
            new InsightExecutionResult("已完成项目分析。", report, "项目", "internal-schema", 12, List.of("/private/api"), "internal")
        );

        assertTrue(rendered.markdown().contains("### 数据范围与口径"));
        assertTrue(rendered.markdown().contains("### 核心指标"));
        assertTrue(rendered.markdown().contains("### 图表数据"));
        assertTrue(rendered.markdown().contains("### 可继续追问"));
        assertTrue(rendered.markdown().contains("项目数"));
        assertTrue(rendered.markdown().contains("12"));
        assertTrue(rendered.artifact().toString().contains("项目数"));
        assertTrue(rendered.artifact().toString().contains("12"));
        assertFalse(rendered.markdown().contains("internal-schema"));
        assertFalse(rendered.artifact().toString().contains("internal-schema"));
        assertFalse(rendered.artifact().toString().contains("/private/api"));
    }
}
