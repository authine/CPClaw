package com.cpclaw.task.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Structured delegation requirements produced by a host agent. */
public record TaskSpec(
    String protocolVersion,
    String goal,
    List<TaskDeliverable> deliverables,
    Map<String, Object> constraints,
    List<String> contextRefs,
    String presentationMode,
    String conversationId,
    String turnId,
    String clientRequestId,
    String continuationToken
) {
    public TaskSpec {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? "cpclaw-delegation/1.0" : protocolVersion;
        goal = goal == null ? "" : goal.trim();
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables.stream().filter(value -> value != null && !value.id().isBlank()).limit(32).toList());
        constraints = constraints == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(constraints));
        contextRefs = contextRefs == null ? List.of() : List.copyOf(contextRefs.stream().filter(value -> value != null && !value.isBlank()).limit(12).toList());
        presentationMode = presentationMode == null || presentationMode.isBlank() ? "agent_evidence" : presentationMode.trim();
        conversationId = conversationId == null ? "" : conversationId.trim();
        turnId = turnId == null ? "" : turnId.trim();
        clientRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        continuationToken = continuationToken == null ? "" : continuationToken.trim();
    }

    public static TaskSpec empty(String goal, String conversationId, String turnId, String clientRequestId) {
        return new TaskSpec("cpclaw-delegation/1.0", goal, List.of(), Map.of(), List.of(), "agent_evidence", conversationId, turnId, clientRequestId, "");
    }
}
