package com.cpclaw.yunshu.protocol;

import java.util.Map;

/** Public command name used by HTTP, MCP and CLI host adapters. */
public record CanonicalTaskCommand(
    String protocolVersion,
    YunshuProtocol.Operation operation,
    String taskId,
    String goal,
    Map<String, Object> input,
    Map<String, Object> action,
    String clientRequestId,
    Long expectedStateVersion,
    String continuationToken
) {
    public CanonicalTaskCommand {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? YunshuProtocol.VERSION : protocolVersion.trim();
        operation = operation == null ? YunshuProtocol.Operation.start : operation;
        taskId = taskId == null ? "" : taskId.trim();
        goal = goal == null ? "" : goal.trim();
        input = input == null ? Map.of() : Map.copyOf(input);
        action = action == null ? Map.of() : Map.copyOf(action);
        clientRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        continuationToken = continuationToken == null ? "" : continuationToken.trim();
    }

    public YunshuTaskRequest toRequest() {
        return new YunshuTaskRequest(protocolVersion, operation, taskId, goal, input, action,
            clientRequestId, expectedStateVersion, continuationToken);
    }
}
