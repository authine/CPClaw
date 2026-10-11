package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.skill.yunshu.runtime.entity.YunshuOperationExecution;
import com.cpclaw.skill.yunshu.runtime.repository.YunshuOperationExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable execution ledger used to make provider retries auditable and
 * replayable.
 *
 * <p>State machine (one row per tenant + subject + host + idempotency key):
 * <pre>
 *   STARTED -&gt; COMPLETED   provider returned a real record identity
 *   STARTED -&gt; UNKNOWN     transport failed or succeeded without identity
 *   STARTED -&gt; FAILED      provider rejected the write; nothing was created
 *   UNKNOWN -&gt; COMPLETED   independently verified reconciliation supplied identity
 *   COMPLETED is terminal and can only be replayed with the same record identity.
 * </pre>
 */
@Service
public class YunshuOperationExecutionService {
    public static final String STARTED = "STARTED";
    public static final String SUBMITTING = "SUBMITTING";
    public static final String COMPLETED = "COMPLETED";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String FAILED = "FAILED";

    private final YunshuOperationExecutionRepository repository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    public YunshuOperationExecutionService(YunshuOperationExecutionRepository repository, ObjectMapper objectMapper) {
        this(repository, objectMapper, null);
    }

    @Autowired
    public YunshuOperationExecutionService(YunshuOperationExecutionRepository repository, ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionManager = transactionManager;
    }

    /**
     * Idempotent entry point for every Yunshu write attempt. A matching earlier
     * execution is replayed without granting permission to execute again;
     * a mismatching payload is refused.
     */
    public YunshuOperationExecution begin(String taskId, String tenantId, String subjectId, String hostId,
            String operation, String idempotencyKey, Map<String, Object> request) {
        try {
            return inTransaction(true, () -> beginInTransaction(taskId, tenantId, subjectId, hostId, operation,
                    idempotencyKey, request));
        } catch (DataIntegrityViolationException ex) {
            // The scoped unique key arbitrates concurrent starts. The losing
            // insert transaction is discarded, so the winner is read back in a
            // brand-new transaction instead of inside a rollback-only one.
            return inTransaction(true, () -> replayInTransaction(taskId, tenantId, subjectId, hostId, operation,
                    idempotencyKey, request));
        }
    }

    public Optional<YunshuOperationExecution> latestForTask(String taskId, String tenantId, String subjectId, String hostId) {
        if (blank(taskId) || blank(tenantId) || blank(subjectId) || blank(hostId)) return Optional.empty();
        return repository.findByTaskIdAndTenantIdAndSubjectIdAndHostIdOrderByUpdatedAtDesc(taskId, tenantId, subjectId, hostId)
                .stream().findFirst();
    }

    /** Marks a write as completed and returns the ledger row for the record identity. */
    public YunshuOperationExecution complete(String executionId, String recordRef, Map<String, Object> response) {
        if (blank(recordRef)) {
            throw new IllegalArgumentException("Provider 未返回真实记录标识，不能判定为写入完成");
        }
        return inTransaction(false, () -> {
            YunshuOperationExecution current = locked(executionId);
            if (COMPLETED.equals(current.getStatus())) {
                if (recordRef.equals(current.getRecordRef())) return current;
                throw new IdempotencyConflictException("执行记录已完成且记录标识不一致，拒绝覆盖既有结果");
            }
            requireState(current, STARTED, SUBMITTING, UNKNOWN);
            current.setStatus(COMPLETED);
            current.setRecordRef(recordRef);
            current.setResponseJson(json(response));
            clearReconciliationLease(current);
            current.setUpdatedAt(Instant.now());
            return repository.save(current);
        });
    }

    /** Marks a write whose remote outcome is not observable; the row must be reconciled later. */
    public YunshuOperationExecution unknown(String executionId, Map<String, Object> response) {
        return inTransaction(false, () -> {
            YunshuOperationExecution current = locked(executionId);
            if (UNKNOWN.equals(current.getStatus())) return current;
            if (COMPLETED.equals(current.getStatus())) {
                throw new IdempotencyConflictException("执行已完成，不能回退为未知状态");
            }
            requireState(current, STARTED, SUBMITTING);
            current.setStatus(UNKNOWN);
            current.setResponseJson(json(response));
            clearReconciliationLease(current);
            current.setUpdatedAt(Instant.now());
            return repository.save(current);
        });
    }

    public YunshuOperationExecution markSubmitting(String executionId) {
        return inTransaction(false, () -> {
            YunshuOperationExecution current = locked(executionId);
            if (SUBMITTING.equals(current.getStatus())) return current;
            requireState(current, STARTED);
            current.setStatus(SUBMITTING); current.setUpdatedAt(Instant.now());
            return repository.save(current);
        });
    }

    /** Records an explicitly rejected attempt; retry requires a new authorized operation. */
    public YunshuOperationExecution fail(String executionId, Map<String, Object> response) {
        return inTransaction(false, () -> {
            YunshuOperationExecution current = locked(executionId);
            if (FAILED.equals(current.getStatus())) return current;
            if (COMPLETED.equals(current.getStatus())) {
                throw new IdempotencyConflictException("执行已完成，不能改判为失败");
            }
            requireState(current, STARTED);
            current.setStatus(FAILED);
            current.setResponseJson(json(response));
            clearReconciliationLease(current);
            current.setUpdatedAt(Instant.now());
            return repository.save(current);
        });
    }

    private YunshuOperationExecution beginInTransaction(String taskId, String tenantId, String subjectId,
            String hostId, String operation, String idempotencyKey, Map<String, Object> request) {
        requireIdempotencyKey(idempotencyKey);
        Optional<YunshuOperationExecution> existing = repository
                .findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(tenantId, subjectId, hostId, idempotencyKey);
        return existing.map(row -> replay(row, taskId, operation, request))
                .orElseGet(() -> create(taskId, tenantId, subjectId, hostId, operation, idempotencyKey, request));
    }

    private YunshuOperationExecution replayInTransaction(String taskId, String tenantId, String subjectId,
            String hostId, String operation, String idempotencyKey, Map<String, Object> request) {
        YunshuOperationExecution existing = repository
                .findByTenantIdAndSubjectIdAndHostIdAndIdempotencyKey(tenantId, subjectId, hostId, idempotencyKey)
                .orElseThrow(() -> new IdempotencyConflictException(
                        "同一幂等键的并发写入未找到已提交的执行记录，请稍后重试"));
        return replay(existing, taskId, operation, request);
    }

    private YunshuOperationExecution replay(YunshuOperationExecution existing, String taskId,
            String operation, Map<String, Object> request) {
        verifySameRequest(existing, taskId, operation, request);
        return existing;
    }

    private void verifySameRequest(YunshuOperationExecution existing, String taskId, String operation,
            Map<String, Object> request) {
        if (operation == null || !operation.equals(existing.getOperation())) {
            throw new IdempotencyConflictException("幂等键已绑定不同的操作类型，拒绝复用：" + existing.getOperation());
        }
        if (taskId != null && !taskId.equals(existing.getTaskId())) {
            throw new IdempotencyConflictException("幂等键已绑定不同的任务，拒绝复用：" + existing.getTaskId());
        }
        if (!canonicalJson(json(request)).equals(canonicalJson(existing.getRequestJson()))) {
            throw new IdempotencyConflictException("幂等键已绑定不同的请求内容，拒绝复用");
        }
    }

    private YunshuOperationExecution create(String taskId, String tenantId, String subjectId, String hostId,
            String operation, String idempotencyKey, Map<String, Object> request) {
        YunshuOperationExecution entity = new YunshuOperationExecution();
        entity.setId(UUID.randomUUID().toString());
        entity.setTaskId(taskId);
        entity.setTenantId(tenantId);
        entity.setSubjectId(subjectId);
        entity.setHostId(hostId);
        entity.setOperation(operation);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setStatus(STARTED);
        entity.setRequestJson(json(request));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        // Flush here: the database unique key is the concurrency arbiter, and a
        // pre-read alone is not safe under two simultaneous requests.
        return repository.saveAndFlush(entity);
    }

    private YunshuOperationExecution locked(String executionId) {
        if (blank(executionId)) {
            throw new IllegalArgumentException("执行记录标识不能为空");
        }
        return repository.findByIdForUpdate(executionId)
                .orElseThrow(() -> new IllegalArgumentException("执行记录不存在：" + executionId));
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (blank(idempotencyKey)) throw new IllegalArgumentException("写操作必须提供幂等键");
    }

    private void clearReconciliationLease(YunshuOperationExecution execution) {
        execution.setReconciliationOwner(null);
        execution.setReconciliationClaimedAt(null);
    }

    private void requireState(YunshuOperationExecution execution, String... allowed) {
        if (!List.of(allowed).contains(execution.getStatus())) {
            throw new IdempotencyConflictException("执行记录状态不允许本次转换");
        }
    }

    private <T> T inTransaction(boolean requiresNew, Supplier<T> work) {
        if (transactionManager == null) throw new IllegalStateException("Execution ledger requires a transaction manager");
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(requiresNew ? TransactionDefinition.PROPAGATION_REQUIRES_NEW
                : TransactionDefinition.PROPAGATION_REQUIRED);
        return template.execute(status -> work.get());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法记录 Yunshu 执行请求", ex);
        }
    }

    /** Canonical form so key order and formatting never decide idempotency. */
    private String canonicalJson(String rawJson) {
        if (rawJson == null) return "";
        try {
            return objectMapper.writeValueAsString(sort(objectMapper.readTree(rawJson)));
        } catch (JsonProcessingException ex) {
            return rawJson;
        }
    }

    private JsonNode sort(JsonNode node) {
        if (node == null || node.isNull()) return node;
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            ObjectNode sorted = objectMapper.createObjectNode();
            for (String name : names) sorted.set(name, sort(node.get(name)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            for (JsonNode child : node) sorted.add(sort(child));
            return sorted;
        }
        return node;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
