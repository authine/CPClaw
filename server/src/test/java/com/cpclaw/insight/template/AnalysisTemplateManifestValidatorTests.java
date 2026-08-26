package com.cpclaw.insight.template;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnalysisTemplateManifestValidatorTests {
    private final AnalysisTemplateManifestValidator validator = new AnalysisTemplateManifestValidator(new ObjectMapper());

    @Test
    void acceptsWhitelistedDeclarativePlan() {
        String manifest = """
            {"id":"template-a","version":"1.0","skillId":"skill-a",
             "plan":[{"op":"groupBy","input":"field.role","as":"groups","options":{}}]}
            """;
        assertTrue(validator.validate(manifest).valid());
    }

    @Test
    void rejectsScriptAndSqlPayloads() {
        String manifest = """
            {"id":"template-a","version":"1.0","skillId":"skill-a",
             "plan":[{"op":"filter","input":"select * from records","as":"rows","options":{}}]}
            """;
        assertFalse(validator.validate(manifest).valid());
    }
}
