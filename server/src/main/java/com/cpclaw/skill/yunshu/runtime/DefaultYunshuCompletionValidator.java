package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.task.dto.TaskDeliverable;
import com.cpclaw.task.dto.TaskSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Generic completion validator with no business deliverable vocabulary. */
@Component
public final class DefaultYunshuCompletionValidator implements YunshuCompletionValidator {
    @Override
    public Map<String, Object> validate(TaskSpec spec, Map<String, Object> evidence, String taskStatus) {
        Map<String, Object> declarations = evidence != null && evidence.get("deliverables") instanceof Map<?, ?> map
            ? cast(map) : Map.of();
        Map<String, String> states = new LinkedHashMap<>();
        List<Map<String, Object>> missing = new ArrayList<>();
        if (spec != null) {
            for (TaskDeliverable item : spec.deliverables()) {
                Object declaration = declarations.get(item.id());
                String state = declaration == null || String.valueOf(declaration).isBlank() ? "unverified" : String.valueOf(declaration);
                states.put(item.id(), state);
                if (item.required() && !"fulfilled".equalsIgnoreCase(state)) {
                    missing.add(Map.of("deliverableId", item.id(), "reason", "未提供该交付物的显式证据声明"));
                }
            }
        }
        String state = missing.isEmpty() ? ("completed".equals(taskStatus) ? "complete" : taskStatus) : "partial";
        boolean ready = "complete".equals(state) || "partial".equals(state) || "completed_with_gaps".equals(state);
        return Map.of("state", state, "answerReady", ready, "deliverables", states,
            "missingEvidence", missing, "continuationAllowed", false, "terminal", true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> value) { return (Map<String, Object>) value; }
}
