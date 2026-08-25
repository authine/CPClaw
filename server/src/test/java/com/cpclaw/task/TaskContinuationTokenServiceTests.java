package com.cpclaw.task;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskContinuationTokenServiceTests {
    @Test
    void tokenBindsTaskAndPrincipalAndContainsUniqueNonce() {
        TaskContinuationTokenService service = new TaskContinuationTokenService("test-secret", 900);
        String first = service.issue("task-1", "alice@example.com");
        String second = service.issue("task-1", "alice@example.com");

        assertNotEquals(first, second);
        assertTrue(service.verify(first, "task-1", "alice@example.com"));
        assertFalse(service.verify(first, "task-2", "alice"));
        assertFalse(service.verify(first, "task-1", "bob@example.com"));
        assertNotNull(service.verifyAndRead(first));
        assertEquals("task-1", service.verifyAndRead(first).taskId());
        assertEquals("alice@example.com", service.verifyAndRead(first).principal());
    }

    @Test
    void tamperedOrMalformedTokenIsRejected() {
        TaskContinuationTokenService service = new TaskContinuationTokenService("test-secret", 900);
        String token = service.issue("task-1", "alice");

        assertFalse(service.verify(token + "x", "task-1", "alice"));
        assertFalse(service.verify("not-a-token", "task-1", "alice"));
        assertNull(service.verifyAndRead("not-a-token"));
    }
}
