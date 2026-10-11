package com.cpclaw.skill.yunshu.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import com.cpclaw.skill.yunshu.form.FormTaskPersistenceService;
import com.cpclaw.skill.yunshu.form.FormTaskScope;
import com.cpclaw.skill.yunshu.form.FormTaskSnapshot;
import com.cpclaw.skill.yunshu.form.YunshuConnectionResolver;
import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import com.cpclaw.skill.yunshu.runtime.repository.YunshuOperationExecutionRepository;
import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class YunshuUnknownReconciliationSchedulerTests {

    @Test
    void reconciledRecordCompletesLedgerTaskAndReservedTicketWithoutWritingAgain() {
        YunshuOperationExecutionRepository executions = mock(YunshuOperationExecutionRepository.class);
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        YunshuConnectionResolver connections = mock(YunshuConnectionResolver.class);
        YunshuProvider provider = mock(YunshuProvider.class);
        YunshuOperationExecutionService executionService = mock(YunshuOperationExecutionService.class);
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        YunshuOperationExecution execution = execution("exec-1", "task-1", "tenant-a", "subject-a", "cpclaw-web", "record-1");
        FormTaskScope scope = new FormTaskScope("tenant-a", "subject-a", "cpclaw-web");
        FormTaskSnapshot task = task(scope, "unknown");
        BoundCloudPivotConnection connection = new BoundCloudPivotConnection("cpclaw-web", "https://yunshu", "user", "password");
        when(executions.findTop20ByStatusOrderByUpdatedAtAsc(YunshuOperationExecutionService.UNKNOWN)).thenReturn(List.of(execution));
        when(executions.claimForReconciliation(any(), any(), any(), any(), any())).thenReturn(1);
        when(tasks.get("task-1", scope)).thenReturn(task);
        when(connections.resolve(any(YunshuHostContext.class))).thenReturn(Optional.of(connection));
        when(provider.reconcileRecord(any(), any(), any(), any())).thenReturn(
                new ProviderOperationResult(true, true, false, "reconcile:record-1", "record-1", "ok", Map.of("remoteReconciled", true)));

        new YunshuUnknownReconciliationScheduler(executions, tasks, connections, provider, executionService, tickets).reconcileUnknown();

        verify(executionService).complete(execution.getId(), "record-1", Map.of("remoteReconciled", true));
        verify(tasks).markSubmitted("task-1", scope, 4L);
        verify(tickets).consumeReconciled("task-1", new YunshuHostContext("tenant-a", "subject-a", "cpclaw-web", Map.of()), "idem-1");
        verify(provider, never()).createRecord(any(), any(), any(), any());
    }

    @Test
    void unmatchedRecordStaysUnknownAndNeverCompletes() {
        YunshuOperationExecutionRepository executions = mock(YunshuOperationExecutionRepository.class);
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        YunshuConnectionResolver connections = mock(YunshuConnectionResolver.class);
        YunshuProvider provider = mock(YunshuProvider.class);
        YunshuOperationExecutionService executionService = mock(YunshuOperationExecutionService.class);
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        YunshuOperationExecution execution = execution("exec-2", "task-2", "tenant-a", "subject-a", "cpclaw-web", "record-2");
        FormTaskScope scope = new FormTaskScope("tenant-a", "subject-a", "cpclaw-web");
        when(executions.findTop20ByStatusOrderByUpdatedAtAsc(YunshuOperationExecutionService.UNKNOWN)).thenReturn(List.of(execution));
        when(executions.claimForReconciliation(any(), any(), any(), any(), any())).thenReturn(1);
        when(tasks.get("task-2", scope)).thenReturn(task(scope, "unknown"));
        when(connections.resolve(any(YunshuHostContext.class))).thenReturn(Optional.of(new BoundCloudPivotConnection("cpclaw-web", "https://yunshu", "user", "password")));
        when(provider.reconcileRecord(any(), any(), any(), any())).thenReturn(
                new ProviderOperationResult(true, false, true, "reconcile", "record-2", "not found", Map.of("remoteReconciled", false)));

        new YunshuUnknownReconciliationScheduler(executions, tasks, connections, provider, executionService, tickets).reconcileUnknown();

        verify(executionService, never()).complete(any(), any(), any());
        verify(tasks, never()).markSubmitted(any(), any(), anyLong());
        verify(tickets, never()).consumeReconciled(any(), any(), any());
        verify(provider, never()).createRecord(any(), any(), any(), any());
    }

    @Test
    void reconciliationTransportFailureLeavesUnknownAndNeverWritesAgain() {
        YunshuOperationExecutionRepository executions = mock(YunshuOperationExecutionRepository.class);
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        YunshuConnectionResolver connections = mock(YunshuConnectionResolver.class);
        YunshuProvider provider = mock(YunshuProvider.class);
        YunshuOperationExecutionService executionService = mock(YunshuOperationExecutionService.class);
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        YunshuOperationExecution execution = execution("exec-3", "task-3", "tenant-a", "subject-a", "cpclaw-web", "record-3");
        FormTaskScope scope = new FormTaskScope("tenant-a", "subject-a", "cpclaw-web");
        when(executions.findTop20ByStatusOrderByUpdatedAtAsc(YunshuOperationExecutionService.UNKNOWN)).thenReturn(List.of(execution));
        when(executions.claimForReconciliation(any(), any(), any(), any(), any())).thenReturn(1);
        when(tasks.get("task-3", scope)).thenReturn(task(scope, "unknown"));
        when(connections.resolve(any(YunshuHostContext.class))).thenReturn(Optional.of(new BoundCloudPivotConnection("cpclaw-web", "https://yunshu", "user", "password")));
        when(provider.reconcileRecord(any(), any(), any(), any())).thenThrow(new RuntimeException("simulated timeout"));

        new YunshuUnknownReconciliationScheduler(executions, tasks, connections, provider, executionService, tickets).reconcileUnknown();

        verify(executionService, never()).complete(any(), any(), any());
        verify(tasks, never()).markSubmitted(any(), any(), anyLong());
        verify(tickets, never()).consumeReconciled(any(), any(), any());
        verify(provider, never()).createRecord(any(), any(), any(), any());
    }

    @Test
    void unclaimedExecutionIsSkippedSoAnotherInstanceCanReconcileIt() {
        YunshuOperationExecutionRepository executions = mock(YunshuOperationExecutionRepository.class);
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        YunshuConnectionResolver connections = mock(YunshuConnectionResolver.class);
        YunshuProvider provider = mock(YunshuProvider.class);
        YunshuOperationExecutionService executionService = mock(YunshuOperationExecutionService.class);
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        YunshuOperationExecution execution = execution("exec-4", "task-4", "tenant-a", "subject-a", "cpclaw-web", "record-4");
        when(executions.findTop20ByStatusOrderByUpdatedAtAsc(YunshuOperationExecutionService.UNKNOWN)).thenReturn(List.of(execution));
        when(executions.claimForReconciliation(any(), any(), any(), any(), any())).thenReturn(0);

        new YunshuUnknownReconciliationScheduler(executions, tasks, connections, provider, executionService, tickets).reconcileUnknown();

        verify(provider, never()).reconcileRecord(any(), any(), any(), any());
        verify(tasks, never()).get(any(), any());
        verify(executionService, never()).complete(any(), any(), any());
    }

    private YunshuOperationExecution execution(String id, String taskId, String tenant, String subject, String host, String recordRef) {
        YunshuOperationExecution execution = new YunshuOperationExecution();
        execution.setId(id); execution.setTaskId(taskId); execution.setTenantId(tenant); execution.setSubjectId(subject);
        execution.setHostId(host); execution.setStatus(YunshuOperationExecutionService.UNKNOWN);
        execution.setRecordRef(recordRef); execution.setIdempotencyKey("idem-" + id.substring(id.length() - 1));
        return execution;
    }

    private FormTaskSnapshot task(FormTaskScope scope, String status) {
        return new FormTaskSnapshot("task-" + scope.subjectId().replace("subject-a", "1"), scope, status,
                Map.of("applicationCode", "test", "schemaCode", "test_multi_branch", "formCode", "test_multi_branch"),
                "metadata", 4L, 2L,
                List.of(new FormFieldSlot("ShortText1784110275677", "所走分支", "text", "value", "user", 1d, false, true, "valid", null, List.of())), List.of());
    }
}

