package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection;
import com.cpclaw.skill.yunshu.form.FormTaskPersistenceService;
import com.cpclaw.skill.yunshu.form.FormTaskScope;
import com.cpclaw.skill.yunshu.form.FormTaskSnapshot;
import com.cpclaw.skill.yunshu.form.YunshuConnectionResolver;
import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import com.cpclaw.skill.yunshu.runtime.repository.YunshuOperationExecutionRepository;
import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import java.util.Map;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Read-only UNKNOWN reconciliation. It never calls save/submit and only
 * completes an execution when the original recordRef is found remotely.
 */
@Component
@ConditionalOnProperty(name = "cpclaw.yunshu-skill.reconciliation.enabled", havingValue = "true")
public class YunshuUnknownReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(YunshuUnknownReconciliationScheduler.class);
    private final YunshuOperationExecutionRepository executions;
    private final FormTaskPersistenceService tasks;
    private final YunshuConnectionResolver connections;
    private final YunshuProvider provider;
    private final YunshuOperationExecutionService executionService;
    private final ConfirmationTicketService tickets;
    private final String workerId;
    private final Duration leaseDuration;

    @Autowired
    public YunshuUnknownReconciliationScheduler(YunshuOperationExecutionRepository executions,
            FormTaskPersistenceService tasks, YunshuConnectionResolver connections, YunshuProvider provider,
            YunshuOperationExecutionService executionService, ConfirmationTicketService tickets,
            @Value("${cpclaw.yunshu-skill.reconciliation.instance-id:local}") String workerId,
            @Value("${cpclaw.yunshu-skill.reconciliation.lease-duration-ms:120000}") long leaseDurationMs) {
        this.executions = executions;
        this.tasks = tasks;
        this.connections = connections;
        this.provider = provider;
        this.executionService = executionService;
        this.tickets = tickets;
        this.workerId = workerId == null || workerId.isBlank() ? UUID.randomUUID().toString() : workerId.trim();
        this.leaseDuration = Duration.ofMillis(Math.max(1_000L, leaseDurationMs));
    }

    public YunshuUnknownReconciliationScheduler(YunshuOperationExecutionRepository executions,
            FormTaskPersistenceService tasks, YunshuConnectionResolver connections, YunshuProvider provider,
            YunshuOperationExecutionService executionService, ConfirmationTicketService tickets) {
        this(executions, tasks, connections, provider, executionService, tickets, "local", 120_000L);
    }

    @Scheduled(fixedDelayString = "${cpclaw.yunshu-skill.reconciliation.fixed-delay-ms:30000}")
    public void reconcileUnknown() {
        for (YunshuOperationExecution execution : executions.findTop20ByStatusOrderByUpdatedAtAsc(YunshuOperationExecutionService.UNKNOWN)) {
            Instant claimedAt = Instant.now();
            int claimed = executions.claimForReconciliation(execution.getId(), YunshuOperationExecutionService.UNKNOWN,
                    workerId, claimedAt, claimedAt.minus(leaseDuration));
            if (claimed != 1) continue;
            try {
                reconcile(execution);
            } catch (RuntimeException exception) {
                log.warn("Yunshu UNKNOWN reconciliation skipped: executionId={}, reason={}", execution.getId(), exception.getMessage());
            }
        }
    }

    private void reconcile(YunshuOperationExecution execution) {
        FormTaskScope scope = new FormTaskScope(execution.getTenantId(), execution.getSubjectId(), execution.getHostId());
        FormTaskSnapshot task = tasks.get(execution.getTaskId(), scope);
        BoundCloudPivotConnection connection = connections.resolve(
                new YunshuHostContext(scope.tenantId(), scope.subjectId(), scope.hostId(), Map.of())).orElse(null);
        if (connection == null) return;
        ProviderOperationResult result = provider.reconcileRecord(connection,
                new FormReference(String.valueOf(task.formRef().getOrDefault("applicationCode", "")),
                        String.valueOf(task.formRef().getOrDefault("schemaCode", "")),
                        String.valueOf(task.formRef().getOrDefault("formCode", ""))),
                task.slots(), execution.getRecordRef());
        if (!result.completed() || result.recordRef() == null || result.recordRef().isBlank()) return;
        executionService.complete(execution.getId(), result.recordRef(), result.evidence());
        if ("unknown".equals(task.status())) tasks.markSubmitted(task.taskId(), scope, task.stateVersion());
        tickets.consumeReconciled(task.taskId(), new YunshuHostContext(scope.tenantId(), scope.subjectId(), scope.hostId(), Map.of()), execution.getIdempotencyKey());
    }
}
