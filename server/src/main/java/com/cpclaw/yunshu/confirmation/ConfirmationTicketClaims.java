package com.cpclaw.yunshu.confirmation;

import java.time.Instant;

/** Signed claims intentionally duplicate persisted bindings for tamper detection. */
record ConfirmationTicketClaims(
    String ticketId, String kid, String alg, String aud,
    String taskId, String tenantId, String subjectId, String hostId,
    String skillVersion, String formRef, String metadataVersion,
    long stateVersion, long draftVersion, String planHash, String idempotencyKey, Instant expiresAt
) { }
