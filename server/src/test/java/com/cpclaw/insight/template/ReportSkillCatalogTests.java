package com.cpclaw.insight.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportSkillCatalogTests {
    private final ReportSkillCatalog catalog = new ReportSkillCatalog();

    @Test
    void fallsBackToGenericTemplateWithoutDomainVocabulary() {
        ReportSkillDefinition first = catalog.resolve("EntityA", "生成结构化结果");
        ReportSkillDefinition second = catalog.resolve("EntityB", "生成结构化结果");

        assertEquals("yunshu-intelligent-inquiry", first.id());
        assertEquals(first.id(), second.id());
        assertTrue(first.description().contains("核验"));
    }
}
