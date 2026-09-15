package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import com.cpclaw.skill.yunshu.runtime.repository.YunshuOperationExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable execution ledger used to make provider retries auditable and replayable. */
@Service
public class YunshuOperationExecutionService {
    private final YunshuOperationExecutionRepository repository;
    private final ObjectMapper objectMapper;

    public YunshuOperationExecutionService(YunshuOperationExecutionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public YunshuOperationExecution begin(String taskId, String tenantId, String subjectId, String hostId,
            String operation, String idempotencyKey, Map<String, Object> request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("写操作必须提供幂等键");
        return repository.findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(tenantId, subjectId, hostId, idempotencyKey)
                .orElseGet(() -> create(taskId, tenantId, subjectId, hostId, operation, idempotencyKey, request));
    }

    @Transactional
    public YunshuOperationExecution complete(YunshuOperationExecution execution, String recordRef, Map<String, Object> response) {
        execution.setStatus("COMPLETED"); execution.setRecordRef(recordRef); execution.setResponseJson(json(response)); execution.setUpdatedAt(Instant.now());
        return repository.save(execution);
    }

    @Transactional
    public YunshuOperationExecution unknown(YunshuOperationExecution execution, Map<String, Object> response) {
        execution.setStatus("UNKNOWN"); execution.setResponseJson(json(response)); execution.setUpdatedAt(Instant.now());
        return repository.save(execution);
    }

    private YunshuOperationExecution create(String taskId, String tenantId, String subjectId, String hostId,
            String operation, String idempotencyKey, Map<String, Object> request) {
        YunshuOperationExecution entity = new YunshuOperationExecution();
        entity.setId(UUID.randomUUID().toString()); entity.setTaskId(taskId); entity.setTenantId(tenantId);
        entity.setSubjectId(subjectId); entity.setHostId(hostId); entity.setOperation(operation);
        entity.setIdempotencyKey(idempotencyKey); entity.setStatus("STARTED"); entity.setRequestJson(json(request));
        Instant now = Instant.now(); entity.setCreatedAt(now); entity.setUpdatedAt(now);
        // The scoped unique key is the concurrency arbiter. Callers must map a
        // duplicate-key exception to a replay read in a fresh transaction;
        // querying here would run inside a rollback-only transaction.
        return repository.saveAndFlush(entity);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("无法记录 Yunshu 执行请求", ex); }
    }
}
