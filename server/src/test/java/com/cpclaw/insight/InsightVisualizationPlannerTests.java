package com.cpclaw.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.insight.dto.InsightReportDto.Chart;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InsightVisualizationPlannerTests {

    private final InsightVisualizationPlanner planner = new InsightVisualizationPlanner(new ObjectMapper());

    @Test
    void presentsOrderedOpportunityStagesAsFunnelInBusinessOrder() {
        CloudPivotDataItem field = field("sales_stage", "销售阶段");
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("E-商机确认", 109L);
        values.put("A-谈判&合同", 75L);
        values.put("D-需求确认", 18L);
        values.put("B-招标采购", 13L);
        values.put("线索", 12L);
        values.put("C-方案论证", 10L);

        Chart chart = planner.planStageDistribution("商机", "商机阶段情况怎么样？", field, values, 237, 237);

        assertEquals("funnel", chart.type());
        assertEquals("ordered_stage", chart.semantic());
        assertEquals(List.of("线索", "E-商机确认", "D-需求确认", "C-方案论证", "B-招标采购", "A-谈判&合同"), chart.labels());
        assertEquals(List.of(237D, 225D, 116D, 98D, 88D, 75D), chart.series().getFirst().values());
        assertEquals(List.of(12D, 109D, 18D, 10D, 13D, 75D), chart.series().get(1).values());
        assertTrue(chart.description().contains("不代表"));
    }

    @Test
    void usesMetadataOptionOrderBeforeSemanticFallback() {
        CloudPivotDataItem field = field("custom_stage", "业务阶段");
        field.setRawJson("{\"options\":[{\"label\":\"待受理\"},{\"label\":\"处理中\"},{\"label\":\"已完成\"}]}");
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("已完成", 8L);
        values.put("待受理", 20L);
        values.put("处理中", 12L);

        Chart chart = planner.planStageDistribution("工单", "工单阶段", field, values, 40, 40);

        assertEquals(List.of("待受理", "处理中", "已完成"), chart.labels());
    }

    @Test
    void keepsUnorderedStatusesAsCompositionInsteadOfFunnel() {
        CloudPivotDataItem field = field("customer_status", "客户状态");
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("正常", 80L);
        values.put("停用", 12L);
        values.put("待审核", 8L);

        Chart chart = planner.planStageDistribution("客户", "客户状态分布", field, values, 100, 100);

        assertEquals("donut", chart.type());
        assertEquals("composition", chart.semantic());
    }

    @Test
    void rejectsMisleadingConversionFunnelWhenValuesIncrease() {
        Chart chart = planner.planBusinessFlow(
            List.of("私海线索（同期）", "已关联私海线索", "商机"),
            List.of(786D, 103D, 237D),
            true
        ).orElseThrow();

        assertEquals("bar", chart.type());
        assertTrue(chart.description().contains("避免误读"));
    }

    @Test
    void usesLineForNaturalTimeSeries() {
        Map<String, Long> values = new LinkedHashMap<>();
        values.put("2026-01", 12L);
        values.put("2026-02", 18L);
        values.put("2026-03", 15L);

        Chart chart = planner.planMonthlyTrend(values);

        assertEquals("line", chart.type());
        assertEquals("time_series", chart.semantic());
    }

    private CloudPivotDataItem field(String code, String name) {
        CloudPivotDataItem field = new CloudPivotDataItem();
        field.setDataItemCode(code);
        field.setName(name);
        return field;
    }
}
