package com.cpclaw.conversation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConversationExecutionRegistryTests {

    private final ConversationExecutionRegistry registry = new ConversationExecutionRegistry();

    @AfterEach
    void tearDown() {
        registry.shutdown();
    }

    @Test
    void cancelInterruptsRunningExecutionAndIsIdempotent() throws Exception {
        ConversationExecutionRegistry.ExecutionHandle handle = registry.register("execution-1");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        registry.submit(handle, () -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(30));
            } catch (InterruptedException exception) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                stopped.countDown();
            }
        });

        assertTrue(started.await(2, TimeUnit.SECONDS));
        assertTrue(registry.cancel("execution-1").cancelled());
        assertTrue(registry.cancel("execution-1").cancelled());
        assertTrue(stopped.await(2, TimeUnit.SECONDS));
        assertTrue(handle.isCancelled());
        assertTrue(interrupted.get());

        registry.complete(handle);
        assertFalse(registry.cancel("execution-1").cancelled());
    }

    @Test
    void duplicateExecutionIdIsRejected() {
        registry.register("execution-2");
        assertThrows(IllegalArgumentException.class, () -> registry.register("execution-2"));
    }

    @Test
    void commitAndCancellationCannotBothWin() {
        ConversationExecutionRegistry.ExecutionHandle commitWinner = registry.register("execution-3");
        assertTrue(commitWinner.tryBeginCommit());
        assertFalse(registry.cancel("execution-3").cancelled());
        commitWinner.markCompleted();

        ConversationExecutionRegistry.ExecutionHandle cancelWinner = registry.register("execution-4");
        assertTrue(registry.cancel("execution-4").cancelled());
        assertFalse(cancelWinner.tryBeginCommit());
        cancelWinner.markCancelled();
    }
}
