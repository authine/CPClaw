package com.cpclaw.skill.yunshu.runtime;

/**
 * Raised when one scoped idempotency key is reused for a different operation,
 * task or request payload. Replay is only safe for the identical request; a
 * mismatch is a caller bug that must never silently reuse another result.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
