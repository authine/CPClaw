package com.cpclaw.task.dto;

import java.util.List;
import java.util.Map;

/** Completion of requested evidence, distinct from final language quality. */
public record EvidenceCompletion(
    String state,
    boolean answerReady,
    Map<String, String> deliverables,
    List<Map<String, Object>> missingEvidence,
    boolean continuationAllowed
) {
    public EvidenceCompletion {
        state = state == null || state.isBlank() ? "failed" : state;
        deliverables = deliverables == null ? Map.of() : Map.copyOf(deliverables);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
    }
}
