package com.cpclaw.insight.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReportSkillCatalogTests {
    private final ReportSkillCatalog catalog = new ReportSkillCatalog();

    @Test
    void usesOneGenericYunshuSkillAcrossBusinessObjects() {
        ReportSkillDefinition opportunity = catalog.resolve("商机", "分析商机阶段和金额，输出报告");
        ReportSkillDefinition customer = catalog.resolve("客户", "分析客户趋势并生成图表");

        assertEquals("yunshu-intelligent-inquiry", opportunity.id());
        assertEquals("yunshu-intelligent-inquiry", customer.id());
    }
}
