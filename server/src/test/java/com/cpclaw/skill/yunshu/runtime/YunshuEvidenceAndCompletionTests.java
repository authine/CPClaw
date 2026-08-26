package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.task.dto.TaskDeliverable;
import com.cpclaw.task.dto.TaskSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YunshuEvidenceAndCompletionTests {
    @Test
    void evidenceComposerAddsStableGenericShape() {
        Map<String, Object> evidence = new DefaultYunshuEvidenceComposer().compose(Map.of("facts", List.of("fact")), "scope", "runtime");
        assertEquals("scope", evidence.get("scope"));
        assertTrue(evidence.containsKey("metrics"));
        assertTrue(((List<?>) evidence.get("provenance")).contains("runtime"));
    }

    @Test
    void completionValidatorUsesExplicitDeclarationsOnly() {
        TaskSpec spec = new TaskSpec("cpclaw-delegation/1.0", "目标", List.of(new TaskDeliverable("deliverable_a", true, "交付", List.of())), Map.of(), List.of(), "agent_evidence", "c", "t", "r", "");
        Map<String, Object> missing = new DefaultYunshuCompletionValidator().validate(spec, Map.of(), "completed");
        assertEquals("partial", missing.get("state"));
        Map<String, Object> complete = new DefaultYunshuCompletionValidator().validate(spec, Map.of("deliverables", Map.of("deliverable_a", "fulfilled")), "completed");
        assertEquals("complete", complete.get("state"));
    }
}
