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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists the FormTask aggregate and every draft revision; state and draft versions are caller-visible CAS guards. */
@Service
public class FormTaskPersistenceService {
    private final FormTaskRepository taskRepository;
    private final FormTaskRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public FormTaskPersistenceService(FormTaskRepository taskRepository, FormTaskRevisionRepository revisionRepository,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormTaskSnapshot create(FormTaskScope scope, Map<String, Object> formRef, String metadataVersion,
            List<FormFieldSlot> slots, String idempotencyKey) {
        Instant now = Instant.now();
        FormTask task = new FormTask();
        task.setId(UUID.randomUUID().toString());
        task.setTenantId(scope.tenantId()); task.setSubjectId(scope.subjectId()); task.setHostId(scope.hostId());
        task.setStatus("needs_input"); task.setFormRefJson(json(formRef)); task.setMetadataVersion(metadataVersion);
        task.setStateVersion(1); task.setDraftVersion(1); task.setDraftJson(json(slots));
        List<FormValidationIssue> issues = new FormDraftValidator().validate(slots);
        task.setValidationJson(json(issues)); task.setIdempotencyKey(idempotencyKey);
        task.setCreatedAt(now); task.setUpdatedAt(now);
        taskRepository.save(task);
        appendRevision(task, "initial", now);
        return snapshot(task);
    }

    @Transactional
    public FormTaskSnapshot updateDraft(String taskId, FormTaskScope scope, long expectedStateVersion,
            long expectedDraftVersion, List<FormFieldSlot> slots, String source) {
        FormTask task = findScoped(taskId, scope);
        if (task.getStateVersion() != expectedStateVersion || task.getDraftVersion() != expectedDraftVersion) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        List<FormValidationIssue> issues = new FormDraftValidator().validate(slots);
        task.setDraftJson(json(slots)); task.setValidationJson(json(issues));
        task.setDraftVersion(task.getDraftVersion() + 1); task.setStateVersion(task.getStateVersion() + 1);
        task.setStatus(issues.isEmpty() ? "ready_to_submit" : "validation_error"); task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        appendRevision(task, source == null || source.isBlank() ? "user" : source, task.getUpdatedAt());
        return snapshot(task);
    }

    @Transactional
    public FormTaskSnapshot cancel(String taskId, FormTaskScope scope, long expectedStateVersion) {
        FormTask task = findScoped(taskId, scope);
        if (task.getStateVersion() != expectedStateVersion) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        task.setStatus("cancelled"); task.setStateVersion(task.getStateVersion() + 1); task.setUpdatedAt(Instant.now());
        taskRepository.save(task); appendRevision(task, "cancelled", task.getUpdatedAt());
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
        taskRepository.save(task);
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
