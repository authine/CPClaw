package com.cpclaw.yunshu.protocol;

/** Canonical protocol constants shared by every Yunshu Skill adapter. */
public final class YunshuProtocol {
    public static final String VERSION = "yunshu-skill/1.0";

    private YunshuProtocol() { }

    public enum Operation { start, input, action, get, events }

    public enum TaskKind {
        form, query, analysis, workflow, import_task;

        @com.fasterxml.jackson.annotation.JsonValue
        public String wireValue() { return this == import_task ? "import" : name(); }
    }

    public enum TaskStatus {
        accepted, needs_input, draft_ready, validation_error, ready_to_submit,
        confirmation_required, submitting, submitted, failed, unknown, manual_review, cancelled
    }

    public enum InteractionType { ask_user, edit_draft, request_confirmation, none }

    public enum HostActionType { ask_user, edit_draft, request_confirmation, respond, report_failure }

    public enum ErrorCode {
        NEEDS_INPUT,
        AMBIGUOUS_TARGET,
        VALIDATION_ERROR,
        STATE_VERSION_CONFLICT,
        PERMISSION_DENIED,
        CONTRACT_UNVERIFIED,
        CONFIRMATION_INVALID,
        REMOTE_UNKNOWN,
        RETRYABLE_FAILURE
    }
}
