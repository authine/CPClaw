package com.cpclaw.yunshu.protocol;

import java.util.Map;

/**
 * Canonical, host-neutral request. MCP, HTTP and CLI adapters must map to this
 * object before invoking a Yunshu Skill runtime.
 */
public record YunshuTaskRequest(
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
    public YunshuTaskRequest {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank()
            ? YunshuProtocol.VERSION : protocolVersion.trim();
        operation = operation == null ? YunshuProtocol.Operation.start : operation;
        taskId = taskId == null ? "" : taskId.trim();
        goal = goal == null ? "" : goal.trim();
        input = input == null ? Map.of() : Map.copyOf(input);
        action = action == null ? Map.of() : Map.copyOf(action);
        clientRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        continuationToken = continuationToken == null ? "" : continuationToken.trim();
    }

    /** Returns a protocol error message, or an empty string when valid. */
    public String validationError() {
        if (!YunshuProtocol.VERSION.equals(protocolVersion)) return "UNSUPPORTED_PROTOCOL_VERSION";
        if (operation == YunshuProtocol.Operation.start) {
            if (!taskId.isBlank()) return "START_MUST_NOT_INCLUDE_TASK_ID";
            if (goal.isBlank()) return "START_REQUIRES_GOAL";
            if (clientRequestId.isBlank()) return "CLIENT_REQUEST_ID_REQUIRED";
        } else {
            if (taskId.isBlank()) return "TASK_ID_REQUIRED";
            if (expectedStateVersion == null || expectedStateVersion < 0) return "EXPECTED_STATE_VERSION_REQUIRED";
            if ((operation == YunshuProtocol.Operation.input || operation == YunshuProtocol.Operation.action)
                && clientRequestId.isBlank()) return "CLIENT_REQUEST_ID_REQUIRED";
        }
        if (operation == YunshuProtocol.Operation.action && action.isEmpty()) return "ACTION_REQUIRED";
        return "";
    }

    public boolean isValid() { return validationError().isBlank(); }
}



