package com.cpclaw.skill.yunshu.form;

import com.cpclaw.skill.yunshu.form.entity.FormTask;
import com.cpclaw.skill.yunshu.form.entity.FormTaskRevision;
import com.cpclaw.skill.yunshu.form.repository.FormTaskRepository;
import com.cpclaw.skill.yunshu.form.repository.FormTaskRevisionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Persists the FormTask aggregate and every draft revision; state and draft versions are caller-visible CAS guards. */
@Service
public class FormTaskPersistenceService {
    private final FormTaskRepository taskRepository;
    private final FormTaskRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public FormTaskPersistenceService(FormTaskRepository taskRepository, FormTaskRevisionRepository revisionRepository,
            ObjectMapper objectMapper) {
        this(taskRepository, revisionRepository, objectMapper, null);
    }

    @Autowired
    public FormTaskPersistenceService(FormTaskRepository taskRepository, FormTaskRevisionRepository revisionRepository,
            ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    public FormTaskSnapshot create(FormTaskScope scope, Map<String, Object> formRef, String metadataVersion,
            List<FormFieldSlot> slots, String idempotencyKey) {
        if (transactionManager == null) {
            throw new IllegalStateException("Form task persistence requires a transaction manager");
        }
        return createInIsolatedTransaction(scope, formRef, metadataVersion, slots, idempotencyKey);
    }

    private FormTaskSnapshot createInIsolatedTransaction(FormTaskScope scope, Map<String, Object> formRef,
            String metadataVersion, List<FormFieldSlot> slots, String idempotencyKey) {
        Instant now = Instant.now();
        FormTask task = new FormTask();
        task.setId(UUID.randomUUID().toString());
        task.setTenantId(scope.tenantId()); task.setSubjectId(scope.subjectId()); task.setHostId(scope.hostId());
        task.setStatus("needs_input"); task.setFormRefJson(json(formRef)); task.setMetadataVersion(metadataVersion);
        task.setStateVersion(1); task.setDraftVersion(1); task.setDraftJson(json(slots));
        List<FormValidationIssue> issues = new FormDraftValidator().validate(slots);
        task.setValidationJson(json(issues)); task.setIdempotencyKey(idempotencyKey);
        task.setCreatedAt(now); task.setUpdatedAt(now);
        try {
            // Flush here: the database unique key is the arbiter for concurrent
            // starts.  A pre-read alone is not safe under two simultaneous requests.
            return new TransactionTemplate(transactionManager).execute(status -> {
                taskRepository.saveAndFlush(task);
                appendRevision(task, "initial", now);
                return snapshot(task);
            });
        } catch (DataIntegrityViolationException ex) {
            // Another request won the same idempotency key.  Return its durable
            // snapshot in a fresh transaction; the failed insert transaction is
            // never allowed to become rollback-only before the replay read.
            return new TransactionTemplate(transactionManager).execute(status ->
                    taskRepository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(
                            scope.tenantId(), scope.subjectId(), scope.hostId(), idempotencyKey)
                            .map(existing -> replayOrReject(existing, formRef, metadataVersion, slots))
                            .orElseThrow(() -> new FormTaskVersionConflictException(
                                    "A concurrent form task creation conflicted; retry with the same clientRequestId")));
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new FormTaskVersionConflictException("Form task changed concurrently; reload the latest envelope");
        }
    }

    private FormTaskSnapshot replayOrReject(FormTask existing, Map<String, Object> formRef,
            String metadataVersion, List<FormFieldSlot> slots) {
        boolean samePayload = metadataVersionEquals(existing.getMetadataVersion(), metadataVersion)
                && jsonEquals(existing.getFormRefJson(), json(formRef))
                && jsonEquals(existing.getDraftJson(), json(slots));
        if (!samePayload) {
            throw new FormTaskVersionConflictException(
                    "The clientRequestId is already bound to a different form task payload");
        }
        return snapshot(existing);
    }

    private boolean metadataVersionEquals(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private boolean jsonEquals(String left, String right) {
        try {
            return objectMapper.readTree(left).equals(objectMapper.readTree(right));
        } catch (JsonProcessingException ex) {
            return left == null ? right == null : left.equals(right);
        }
    }

    @Transactional
    public FormTaskSnapshot updateDraft(String taskId, FormTaskScope scope, long expectedStateVersion,
            long expectedDraftVersion, List<FormFieldSlot> slots, String source) {
        FormTask task = findScoped(taskId, scope);
        if (task.getStateVersion() != expectedStateVersion || task.getDraftVersion() != expectedDraftVersion || isTerminal(task.getStatus())) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        List<FormValidationIssue> issues = new FormDraftValidator().validate(slots);
        task.setDraftJson(json(slots)); task.setValidationJson(json(issues));
        task.setDraftVersion(task.getDraftVersion() + 1); task.setStateVersion(task.getStateVersion() + 1);
        task.setStatus(issues.isEmpty() ? "ready_to_submit" : "validation_error"); task.setUpdatedAt(Instant.now());
        saveVersioned(task);
        appendRevision(task, source == null || source.isBlank() ? "user" : source, task.getUpdatedAt());
        return snapshot(task);
    }

    @Transactional
    public FormTaskSnapshot cancel(String taskId, FormTaskScope scope, long expectedStateVersion) {
        FormTask task = findScoped(taskId, scope);
        if (task.getStateVersion() != expectedStateVersion || isTerminal(task.getStatus())) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        task.setStatus("cancelled"); task.setStateVersion(task.getStateVersion() + 1); task.setUpdatedAt(Instant.now());
        saveVersioned(task);
        return snapshot(task);
    }

    @Transactional
    public FormTaskSnapshot requireConfirmation(String taskId, FormTaskScope scope, long expectedStateVersion, String planHash) {
        FormTask task = findScoped(taskId, scope);
        if (task.getStateVersion() != expectedStateVersion || !"ready_to_submit".equals(task.getStatus())) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        task.setStatus("confirmation_required"); task.setPlanHash(planHash);
        task.setStateVersion(task.getStateVersion() + 1); task.setUpdatedAt(Instant.now());
        // Confirmation changes task state only; it does not create a new draft.
        // The draft revision was already persisted by updateDraft, and the
        // (task_id, draft_version) key is intentionally unique.
        saveVersioned(task);
        return snapshot(task);
    }

    @Transactional(readOnly = true)
    public FormTaskSnapshot get(String taskId, FormTaskScope scope) { return snapshot(findScoped(taskId, scope)); }

    @Transactional(readOnly = true)
    public FormTaskSnapshot findByIdempotencyKey(FormTaskScope scope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        return taskRepository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(
                scope.tenantId(), scope.subjectId(), scope.hostId(), idempotencyKey).map(this::snapshot).orElse(null);
    }

    private boolean isTerminal(String status) { return "cancelled".equals(status) || "completed".equals(status) || "failed".equals(status); }

    private void saveVersioned(FormTask task) {
        try {
            taskRepository.saveAndFlush(task);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new FormTaskVersionConflictException("Form task changed concurrently; reload the latest envelope");
        }
    }

    private FormTask findScoped(String taskId, FormTaskScope scope) {
        return taskRepository.findByIdAndTenantIdAndSubjectIdAndHostId(taskId, scope.tenantId(), scope.subjectId(), scope.hostId())
                .orElseThrow(() -> new FormTaskNotFoundException("Form task not found"));
    }

    private void appendRevision(FormTask task, String source, Instant at) {
        FormTaskRevision revision = new FormTaskRevision();
        revision.setId(UUID.randomUUID().toString()); revision.setTaskId(task.getId());
        revision.setDraftVersion(task.getDraftVersion()); revision.setStateVersion(task.getStateVersion());
        revision.setDraftJson(task.getDraftJson()); revision.setValidationJson(task.getValidationJson());
        revision.setSource(source); revision.setCreatedAt(at); revisionRepository.save(revision);
    }

    private FormTaskSnapshot snapshot(FormTask task) {
        return new FormTaskSnapshot(task.getId(), new FormTaskScope(task.getTenantId(), task.getSubjectId(), task.getHostId()),
                task.getStatus(), readMap(task.getFormRefJson()), task.getMetadataVersion(), task.getStateVersion(),
                task.getDraftVersion(), readSlots(task.getDraftJson()), readIssues(task.getValidationJson()));
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize form task", ex); } }
    private Map<String, Object> readMap(String value) { try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read form task", ex); } }
    private List<FormFieldSlot> readSlots(String value) { try { return objectMapper.readValue(value, new TypeReference<>() { }); } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read form draft", ex); } }
    private List<FormValidationIssue> readIssues(String value) { try { return value == null ? List.of() : objectMapper.readValue(value, new TypeReference<>() { }); } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot read form validation", ex); } }
}
