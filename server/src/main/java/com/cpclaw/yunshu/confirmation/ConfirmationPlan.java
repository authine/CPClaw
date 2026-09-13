package com.cpclaw.yunshu.confirmation;

import java.time.Instant;

/** Immutable execution plan snapshot that a human is asked to approve. */
public record ConfirmationPlan(
    String taskId,
    String tenantId,
    String subjectId,
    String hostId,
    String skillVersion,
    String formRef,
    String metadataVersion,
    long stateVersion,
    long draftVersion,
    String planHash,
    String idempotencyKey,
    Instant expiresAt
) {
    public ConfirmationPlan {
        taskId = text(taskId); tenantId = text(tenantId); subjectId = text(subjectId); hostId = text(hostId);
        skillVersion = text(skillVersion); formRef = text(formRef); metadataVersion = text(metadataVersion);
        planHash = text(planHash); idempotencyKey = text(idempotencyKey);
    }

    public String validationError() {
        if (taskId.isBlank() || tenantId.isBlank() || subjectId.isBlank() || hostId.isBlank()) return "TASK_SCOPE_REQUIRED";
        if (skillVersion.isBlank() || formRef.isBlank() || metadataVersion.isBlank()) return "PLAN_VERSION_REQUIRED";
        if (stateVersion < 0 || draftVersion < 0 || planHash.isBlank() || idempotencyKey.isBlank()) return "PLAN_BINDING_REQUIRED";
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) return "PLAN_EXPIRED";
        return "";
    }

    private static String text(String value) { return value == null ? "" : value.trim(); }
}
