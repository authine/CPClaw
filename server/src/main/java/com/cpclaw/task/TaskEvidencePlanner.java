package com.cpclaw.task;

import com.cpclaw.task.dto.TaskDeliverable;
import com.cpclaw.task.dto.TaskSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Generic evidence completion evaluator. It knows deliverable contracts, not domain-specific names. */
@Service
public class TaskEvidencePlanner {
    public Map<String, Object> evaluate(TaskSpec spec, String status, Map<String, Object> payload) {
        Map<String, String> states = new LinkedHashMap<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        boolean hasContract = spec != null && !spec.deliverables().isEmpty();
        for (TaskDeliverable deliverable : hasContract ? spec.deliverables() : List.<TaskDeliverable>of()) {
            String evidenceKey = deliverable.id();
            boolean fulfilled = hasMeaningfulEvidence(payload.get(evidenceKey))
                || "overall_summary".equals(evidenceKey) && hasMeaningfulEvidence(payload.get("artifact"));
            states.put(evidenceKey, fulfilled ? "fulfilled" : "missing");
            if (!fulfilled && deliverable.required()) missing.add(Map.of("deliverableId", evidenceKey, "reason", "执行结果未提供该交付项的可核验证据"));
        }
        String state = !hasContract ? status : missing.isEmpty() && "completed".equals(status) ? "complete" : "partial";
        return Map.of("state", state, "answerReady", "complete".equals(state) || "partial".equals(state), "deliverables", states, "missingEvidence", missing, "continuationAllowed", false, "terminal", true);
    }

    private boolean hasMeaningfulEvidence(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof List<?> list) return !list.isEmpty() && list.stream().anyMatch(this::hasMeaningfulEvidence);
        if (value instanceof Map<?, ?> map) return !map.isEmpty() && map.values().stream().anyMatch(this::hasMeaningfulEvidence);
        return true;
    }
}
