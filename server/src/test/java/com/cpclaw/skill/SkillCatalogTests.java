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

    @Test
    void approvedMarkdownSkillBindsOnlyToServerOwnedExecutor() {
        catalog.registerMarkdown("---\nid: custom-react\nversion: 1.0.0\nname: 自定义\nexecutor: metadata-react-executor\nrisk: READ\n---\n# Skill", "test", true);
        SkillRegistry registry = new SkillRegistry(java.util.List.of(), java.util.List.of(new SkillExecutor() {
            public String skillId() { return SkillCatalog.YUNSHU_BUSINESS_SYSTEM; }
            public java.util.Map<String,Object> execute(com.cpclaw.task.dto.SemanticTaskRequest request, SkillExecutionContext context, String taskId, java.util.function.Consumer<com.cpclaw.task.dto.TaskProgressEvent> progress) { return java.util.Map.of("status", "completed"); }
        }), catalog);
        org.junit.jupiter.api.Assertions.assertNotNull(registry.executor("custom-react"));
    }

    @Test
    void newMarkdownSkillStartsAsDraftUntilPublished() {
        SkillCatalog.SkillDefinition definition = catalog.registerMarkdownDraft("---\nid: draft-skill\nversion: 1.0.0\nname: 草稿\nexecutor: metadata-react-executor\n---\n# Skill", "test");
        assertTrue("draft".equals(definition.publicationStatus()));
        assertFalse(catalog.isRegistered("draft-skill"));
    }
}
