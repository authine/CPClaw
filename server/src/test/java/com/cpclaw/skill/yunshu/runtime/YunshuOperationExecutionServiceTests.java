package com.cpclaw.skill.yunshu.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import com.cpclaw.skill.yunshu.runtime.repository.YunshuOperationExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * The ledger is the only durable evidence that a Yunshu write happened. These
 * tests pin the replay, conflict and terminal-state rules the provider loop
 * depends on.
 */
class YunshuOperationExecutionServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final YunshuOperationExecutionRepository repository = mock(YunshuOperationExecutionRepository.class);
    private final YunshuOperationExecutionService service =
            new YunshuOperationExecutionService(repository, objectMapper, transactionManager());

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        return manager;
    }

    @Test
    void beginCreatesDurableStartedExecutionWithScopedIdempotency() {
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        YunshuOperationExecution execution = service.begin("task-1", "t", "u", "h", "create", "op-1",
                Map.of("schemaCode", "sql_line"));

        assertEquals("task-1", execution.getTaskId());
        assertEquals("STARTED", execution.getStatus());
        assertEquals("op-1", execution.getIdempotencyKey());
        verify(repository).saveAndFlush(any(YunshuOperationExecution.class));
    }

    @Test
    void beginReplaysIdenticalExecutionInsteadOfStartingAnotherOperation() {
        YunshuOperationExecution existing = started("task-1", "create", Map.of("schemaCode", "sql_line"));
        existing.setStatus("UNKNOWN");
        existing.setId("exec-1");
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.of(existing));

        YunshuOperationExecution replay = service.begin("task-1", "t", "u", "h", "create", "op-1",
                Map.of("schemaCode", "sql_line"));

        assertSame(existing, replay);
        assertEquals("UNKNOWN", replay.getStatus());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void beginRejectsReusingTheSameKeyForADifferentPayload() {
        YunshuOperationExecution existing = started("task-1", "create", Map.of("schemaCode", "sql_line"));
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> service.begin("task-1", "t", "u", "h", "create",
                "op-1", Map.of("schemaCode", "int_bu_oppor")));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void beginRejectsReusingTheSameKeyForADifferentOperation() {
        YunshuOperationExecution existing = started("task-1", "create", Map.of("schemaCode", "sql_line"));
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.of(existing));

        assertThrows(IdempotencyConflictException.class, () -> service.begin("task-1", "t", "u", "h", "update",
                "op-1", Map.of("schemaCode", "sql_line")));
    }

    @Test
    void beginHonoursSamePayloadWhenTheInsertedRowWasSerializedInAnotherKeyOrder() {
        YunshuOperationExecution existing = new YunshuOperationExecution();
        existing.setId("exec-1");
        existing.setTaskId("task-1");
        existing.setOperation("create");
        existing.setRequestJson("{\"schemaCode\":\"sql_line\",\"data\":{\"name\":\"奥哲\"}}");
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.of(existing));

        YunshuOperationExecution replay = service.begin("task-1", "t", "u", "h", "create", "op-1",
                Map.of("data", Map.of("name", "奥哲"), "schemaCode", "sql_line"));

        assertSame(existing, replay);
    }

    @Test
    void rejectedExecutionIsReplayedWithoutRestartingOrErasingEvidence() {
        YunshuOperationExecution existing = started("task-1", "create", Map.of("schemaCode", "sql_line"));
        existing.setStatus("FAILED");
        existing.setResponseJson("{\"errcode\":400}");
        when(repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey("t", "u", "h", "op-1"))
                .thenReturn(Optional.of(existing));

        YunshuOperationExecution restarted = service.begin("task-1", "t", "u", "h", "create", "op-1",
                Map.of("schemaCode", "sql_line"));

        assertEquals("FAILED", restarted.getStatus());
        assertEquals("{\"errcode\":400}", restarted.getResponseJson());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void blankIdempotencyIsRejectedBeforeProviderExecution() {
        assertThrows(IllegalArgumentException.class,
                () -> service.begin("task-1", "t", "u", "h", "create", " ", Map.of()));
    }

    @Test
    void completeRequiresRealRecordIdentity() {
        assertThrows(IllegalArgumentException.class, () -> service.complete("exec-1", " ", Map.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void completeRecordsTerminalEvidenceForAStartedExecution() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setId("exec-1");
        execution.setReconciliationOwner("worker-a");
        execution.setReconciliationClaimedAt(Instant.now());
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        YunshuOperationExecution completed = service.complete("exec-1", "record-1", Map.of("httpStatus", 200));

        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("record-1", completed.getRecordRef());
        assertNull(completed.getReconciliationOwner());
        assertNull(completed.getReconciliationClaimedAt());
        verify(repository).save(execution);
    }

    @Test
    void completeReplayWithTheSameRecordIdentityIsIdempotent() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setId("exec-1");
        execution.setStatus("COMPLETED");
        execution.setRecordRef("record-1");
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));

        assertSame(execution, service.complete("exec-1", "record-1", Map.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void completeCannotOverwriteAnExistingRecordIdentity() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setId("exec-1");
        execution.setStatus("COMPLETED");
        execution.setRecordRef("record-1");
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));

        assertThrows(IdempotencyConflictException.class,
                () -> service.complete("exec-1", "record-2", Map.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void completedExecutionCannotBeDowngradedToUnknownOrFailed() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setId("exec-1");
        execution.setStatus("COMPLETED");
        execution.setRecordRef("record-1");
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));

        assertThrows(IdempotencyConflictException.class, () -> service.unknown("exec-1", Map.of()));
        assertThrows(IdempotencyConflictException.class, () -> service.fail("exec-1", Map.of()));
    }

    @Test
    void unknownAndFailedTransitionsAreRecordedAndIdempotent() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setId("exec-1");
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("UNKNOWN", service.unknown("exec-1", Map.of("reason", "timeout")).getStatus());
        assertSame(execution, service.unknown("exec-1", Map.of("reason", "timeout")));
        assertThrows(IdempotencyConflictException.class, () -> service.fail("exec-1", Map.of("errcode", 400)));
        verify(repository).save(execution);
    }

    @Test
    void failedExecutionCannotBeCompletedOrChangedToUnknown() {
        YunshuOperationExecution execution = started("task-1", "create", Map.of());
        execution.setStatus("FAILED");
        when(repository.findByIdForUpdate("exec-1")).thenReturn(Optional.of(execution));
        assertThrows(IdempotencyConflictException.class, () -> service.complete("exec-1", "record-1", Map.of()));
        assertThrows(IdempotencyConflictException.class, () -> service.unknown("exec-1", Map.of()));
        verify(repository, never()).save(any());
    }

    @Test
    void aLedgerWithoutTransactionManagementFailsClosed() {
        YunshuOperationExecutionService unsafe = new YunshuOperationExecutionService(repository, objectMapper);
        assertThrows(IllegalStateException.class,
                () -> unsafe.begin("task-1", "t", "u", "h", "create", "op-1", Map.of()));
        verify(repository, never()).saveAndFlush(any());
    }

    private YunshuOperationExecution started(String taskId, String operation, Map<String, Object> request) {
        YunshuOperationExecution execution = new YunshuOperationExecution();
        execution.setTaskId(taskId);
        execution.setOperation(operation);
        execution.setStatus("STARTED");
        try {
            execution.setRequestJson(objectMapper.writeValueAsString(request));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return execution;
    }
}
