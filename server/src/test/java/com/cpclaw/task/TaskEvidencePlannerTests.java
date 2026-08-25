package com.cpclaw.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cpclaw.task.dto.TaskDeliverable;
import com.cpclaw.task.dto.TaskSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskEvidencePlannerTests {
    private final TaskEvidencePlanner planner = new TaskEvidencePlanner();

    @Test
    void marksCompositeDeliverablesCompleteOnlyWhenEachEvidenceKeyIsPresent() {
        TaskSpec spec = new TaskSpec(
            "cpclaw-delegation/1.0",
            "复合领域任务",
            List.of(
                new TaskDeliverable("overall_summary", true, "整体摘要", List.of()),
                new TaskDeliverable("risk_basis", true, "风险依据", List.of()),
                new TaskDeliverable("owner_mapping", true, "负责人映射", List.of())
            ),
            Map.of(), List.of(), "agent_evidence", "conv-1", "turn-1", "req-1", ""
        );

        Map<String, Object> completion = planner.evaluate(spec, "completed", Map.of(
            "overall_summary", Map.of("count", 12),
            "risk_basis", List.of("stagnation"),
            "owner_mapping", List.of(Map.of("record", "A", "owner", "U1"))
        ));

        assertEquals("complete", completion.get("state"));
        assertTrue(((Map<?, ?>) completion.get("deliverables")).values().stream().allMatch("fulfilled"::equals));
        assertTrue(((List<?>) completion.get("missingEvidence")).isEmpty());
    }

    @Test
    void reportsMissingRequiredEvidenceAsPartial() {
        TaskSpec spec = new TaskSpec(
            "cpclaw-delegation/1.0", "复合领域任务",
            List.of(new TaskDeliverable("overall_summary", true, "整体摘要", List.of()), new TaskDeliverable("risk_basis", true, "风险依据", List.of())),
            Map.of(), List.of(), "agent_evidence", "conv-1", "turn-1", "req-1", ""
        );

        Map<String, Object> completion = planner.evaluate(spec, "completed", Map.of("overall_summary", Map.of("count", 1)));

        assertEquals("partial", completion.get("state"));
        assertEquals("missing", ((Map<?, ?>) completion.get("deliverables")).get("risk_basis"));
        assertEquals(1, ((List<?>) completion.get("missingEvidence")).size());
    }
}
