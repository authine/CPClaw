package com.cpclaw.yunshu.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Canonical response envelope. It deliberately contains no CPClaw UI or URL
 * concepts so that any host can render it and continue the same task.
 */
public record YunshuTaskEnvelope(
    String protocolVersion,
    Task task,
    Interaction interaction,
    Draft draft,
    Validation validation,
    Next next,
    Completion completion,
    Map<String, Object> output,
    Map<String, Object> evidence,
    HostAction hostAction,
    Map<String, Object> auditRef,
    Error error
) {
    public YunshuTaskEnvelope {
        protocolVersion = protocolVersion == null || protocolVersion.isBlank()
            ? YunshuProtocol.VERSION : protocolVersion;
        if (!YunshuProtocol.VERSION.equals(protocolVersion)) throw new IllegalArgumentException("Unsupported Yunshu protocol version");
        task = task == null ? new Task("", YunshuProtocol.TaskKind.query, YunshuProtocol.TaskStatus.failed, 0, false, Instant.now()) : task;
        interaction = interaction == null ? new Interaction(YunshuProtocol.InteractionType.none, List.of()) : interaction;
        draft = draft == null ? new Draft(Map.of(), "", 0, List.of()) : draft;
        validation = validation == null ? new Validation(List.of()) : validation;
        next = next == null ? new Next(Map.of(), "", null) : next;
        completion = completion == null ? new Completion("incomplete", List.of()) : completion;
        output = output == null ? Map.of() : Map.copyOf(output);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        hostAction = hostAction == null ? new HostAction(YunshuProtocol.HostActionType.report_failure, false, Map.of()) : hostAction;
        auditRef = auditRef == null ? Map.of() : Map.copyOf(auditRef);
    }

    public record Task(String id, YunshuProtocol.TaskKind kind, YunshuProtocol.TaskStatus status,
                       long stateVersion, boolean retryable, Instant updatedAt) {
        public Task { id = id == null ? "" : id; updatedAt = updatedAt == null ? Instant.now() : updatedAt; }
    }

    public record Interaction(YunshuProtocol.InteractionType type, List<Question> questions) {
        public Interaction { type = type == null ? YunshuProtocol.InteractionType.none : type; questions = questions == null ? List.of() : List.copyOf(questions); }
    }

    public record Question(String id, List<String> slotRefs, String type, String prompt, boolean required,
                           List<Map<String, Object>> options, Map<String, Object> validation) {
        public Question { id = id == null ? "" : id; slotRefs = slotRefs == null ? List.of() : List.copyOf(slotRefs); type = type == null ? "text" : type; prompt = prompt == null ? "" : prompt; options = options == null ? List.of() : List.copyOf(options); validation = validation == null ? Map.of() : Map.copyOf(validation); }
    }

    public record Draft(Map<String, Object> formRef, String metadataVersion, long draftVersion, List<Field> fields) {
        public Draft { formRef = formRef == null ? Map.of() : Map.copyOf(formRef); metadataVersion = metadataVersion == null ? "" : metadataVersion; fields = fields == null ? List.of() : List.copyOf(fields); }
    }

    public record Field(String fieldCode, String displayName, String type, Object value, String source,
                        Double confidence, boolean required, boolean confirmed, String validationStatus, String error) {
        public Field { fieldCode = fieldCode == null ? "" : fieldCode; displayName = displayName == null ? "" : displayName; type = type == null ? "text" : type; source = source == null ? "" : source; validationStatus = validationStatus == null ? "unknown" : validationStatus; error = error == null ? "" : error; }
    }

    public record Validation(List<Issue> issues) { public Validation { issues = issues == null ? List.of() : List.copyOf(issues); } }
    public record Issue(String code, String message, String fieldCode, boolean blocking) { }
    public record Next(Map<String, Object> requiredInputSchema, String continuationToken, Instant expiresAt) { public Next { requiredInputSchema = requiredInputSchema == null ? Map.of() : Map.copyOf(requiredInputSchema); } }
    public record Completion(String state, List<String> missing) { public Completion { state = state == null ? "incomplete" : state; missing = missing == null ? List.of() : List.copyOf(missing); } }

    public record HostAction(YunshuProtocol.HostActionType type, boolean allowAnotherCallThisTurn, Map<String, Object> payload) {
        public HostAction {
            if (type == null) type = YunshuProtocol.HostActionType.report_failure;
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
        public static HostAction requestConfirmation(Map<String, Object> payload) {
            return new HostAction(YunshuProtocol.HostActionType.request_confirmation, false, payload);
        }
    }

    public record Error(YunshuProtocol.ErrorCode code, String message, boolean retryable) {
        public Error { message = message == null ? "" : message; }
    }
}
