package com.cpclaw.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillCatalogTests {

    private final SkillCatalog catalog = new SkillCatalog();

    @Test
    void onlyRegisteredYunshuSkillCanEnterTaskExecution() {
        assertTrue(catalog.isRegistered(SkillCatalog.YUNSHU_BUSINESS_SYSTEM));
        assertFalse(catalog.isRegistered("arbitrary-model-skill"));
        assertFalse(catalog.isRegistered(""));
    }

    @Test
    void acceptsReviewedMarkdownOnlyWithAllowlistedExecutor() {
        SkillCatalog.SkillDefinition definition = catalog.registerMarkdown("---\nid: custom-analysis\nversion: 1.2.0\nname: 自定义分析\nexecutor: metadata-insight-planner\nrisk: READ\n---\n# Skill", "test", true);
        assertTrue(catalog.isRegistered("custom-analysis"));
        assertTrue("1.2.0".equals(definition.version()));
    }

    @Test
    void rejectsUnapprovedOrUnallowlistedMarkdown() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> catalog.registerMarkdown("---\nid: unsafe\nexecutor: shell\n---", "test", true));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> catalog.registerMarkdown("---\nid: unsafe\nexecutor: metadata-react-executor\n---", "test", false));
    }
}
