package com.cpclaw.task.dto;

import java.util.List;

/** Channel-neutral user request; credentials are intentionally not part of this object. */
public record SemanticTaskRequest(
    String channel,
    String installationId,
    String externalPrincipal,
    String clientRequestId,
    String turnId,
    String userGoal,
    List<String> context,
    TaskSpec taskSpec,
    String continuationOfTaskId
) {
    public SemanticTaskRequest(
        String channel,
        String installationId,
        String externalPrincipal,
        String clientRequestId,
        String turnId,
        String userGoal,
        List<String> context,
        TaskSpec taskSpec
    ) {
        this(channel, installationId, externalPrincipal, clientRequestId, turnId, userGoal, context, taskSpec, "");
    }

    public SemanticTaskRequest(String channel, String installationId, String externalPrincipal, String clientRequestId, String userGoal, List<String> context) {
        this(channel, installationId, externalPrincipal, clientRequestId, "", userGoal, context, TaskSpec.empty(userGoal, "", "", clientRequestId), "");
    }

    public SemanticTaskRequest {
        channel = channel == null || channel.isBlank() ? "unknown" : channel;
        installationId = installationId == null ? "" : installationId;
        externalPrincipal = externalPrincipal == null ? "" : externalPrincipal;
        clientRequestId = clientRequestId == null ? "" : clientRequestId;
        turnId = turnId == null ? "" : turnId;
        userGoal = userGoal == null ? "" : userGoal;
        context = context == null ? List.of() : List.copyOf(context.stream().filter(value -> value != null && !value.isBlank()).limit(6).toList());
        taskSpec = taskSpec == null ? TaskSpec.empty(userGoal, "", turnId, clientRequestId) : taskSpec;
        continuationOfTaskId = continuationOfTaskId == null ? "" : continuationOfTaskId.trim();
    }
}
