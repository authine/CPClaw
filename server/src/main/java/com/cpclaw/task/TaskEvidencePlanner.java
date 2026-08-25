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
            boolean fulfilled = payload.containsKey(evidenceKey) || payload.containsKey("artifact") && "overall_summary".equals(evidenceKey);
            states.put(evidenceKey, fulfilled ? "fulfilled" : "missing");
            if (!fulfilled && deliverable.required()) missing.add(Map.of("deliverableId", evidenceKey, "reason", "执行结果未提供该交付项的可核验证据"));
        }
        String state = !hasContract ? status : missing.isEmpty() && "completed".equals(status) ? "complete" : "partial";
        return Map.of("state", state, "answerReady", "complete".equals(state) || "partial".equals(state), "deliverables", states, "missingEvidence", missing, "continuationAllowed", false, "terminal", true);
    }
}
