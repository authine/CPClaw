package com.cpclaw.task;

import com.cpclaw.task.entity.SemanticTaskEvent;
import com.cpclaw.task.entity.SemanticTaskRun;
import com.cpclaw.task.repository.SemanticTaskEventRepository;
import com.cpclaw.task.repository.SemanticTaskRunRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns only short-lived task persistence transactions. Network/model/skill
 * execution must happen outside these methods so a slow downstream call cannot
 * hold a database connection or a pessimistic lock.
 */
@Service
public class SemanticTaskPersistenceService {
    private final SemanticTaskRunRepository runRepository;
    private final SemanticTaskEventRepository eventRepository;

    public SemanticTaskPersistenceService(SemanticTaskRunRepository runRepository, SemanticTaskEventRepository eventRepository) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SemanticTaskRun createRunningTask(SemanticTaskRun run) {
        return runRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendEvent(String taskId, int sequence, String eventJson) {
        SemanticTaskEvent stored = new SemanticTaskEvent();
        stored.setId(UUID.randomUUID().toString());
        stored.setTaskId(taskId);
        stored.setEventSequence(sequence);
        stored.setEventJson(eventJson);
        stored.setCreatedAt(Instant.now());
        eventRepository.save(stored);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(SemanticTaskRun run, String status, Instant updatedAt, Instant completedAt,
                             String resultJson, String completionJson, String evidenceJson) {
        run.setStatus(status);
        run.setUpdatedAt(updatedAt);
        run.setCompletedAt(completedAt);
        run.setResultJson(resultJson);
        run.setCompletionJson(completionJson);
        run.setEvidenceJson(evidenceJson);
        runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public Optional<SemanticTaskRun> findByClientRequest(String channel, String installationKey,
                                                          String externalPrincipal, String clientRequestId) {
        return runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndClientRequestId(
            channel, installationKey, externalPrincipal, clientRequestId);
    }

    @Transactional(readOnly = true)
    public Optional<SemanticTaskRun> findByTurn(String channel, String installationKey,
                                                String externalPrincipal, String turnId) {
        return runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndTurnId(
            channel, installationKey, externalPrincipal, turnId);
    }

    @Transactional
    public Optional<SemanticTaskRun> findLockedById(String taskId) {
        return runRepository.findLockedById(taskId);
    }

    @Transactional
    public boolean consumeContinuation(String taskId) {
        return runRepository.consumeContinuation(taskId) == 1;
    }
}
