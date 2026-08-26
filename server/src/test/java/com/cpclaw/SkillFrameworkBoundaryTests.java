package com.cpclaw;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.skill.SkillQuestionSemantics;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Architectural guard: domain vocabulary may only live in template plugins. */
class SkillFrameworkBoundaryTests {
    private static final Set<String> DOMAIN_TERMS = Set.of("商机", "项目", "客户", "合同", "opportunity", "projectAmount", "opportunityAmount");

    @Test
    void genericRuntimeDoesNotContainDomainVocabulary() throws Exception {
        Path root = Path.of("src/main/java/com/cpclaw/skill/yunshu");
        for (Path file : (Iterable<Path>) Files.walk(root)::iterator) {
            if (!file.toString().endsWith(".java") || file.toString().contains("\\template\\") || file.toString().contains("/template/")) continue;
            String source = Files.readString(file, StandardCharsets.UTF_8).toLowerCase();
            for (String term : DOMAIN_TERMS) assertFalse(source.contains(term.toLowerCase()), file + " contains domain term " + term);
        }
    }

    @Test
    void semanticContractContainsNoScenarioNamedMethods() {
        for (Method method : SkillQuestionSemantics.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("customer") || name.contains("opportunity") || name.contains("project"));
        }
        assertTrue(SkillQuestionSemantics.class.getDeclaredMethods().length > 0);
    }
}
