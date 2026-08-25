package com.cpclaw.memory;

import com.cpclaw.memory.entity.AgentMemory;
import com.cpclaw.memory.repository.AgentMemoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import com.cpclaw.memory.dto.MemoryEntryDto;
import com.cpclaw.memory.dto.MemorySettingsResponse;
import com.cpclaw.memory.dto.SaveMemoryEntryRequest;

@Service
public class MemoryService {

    private static final long CONVERSATION_MEMORY_TTL_DAYS = 30;
    private final AgentMemoryRepository repository;
    private final ObjectMapper objectMapper;
    private final PrincipalContextService principalContextService;

    public MemoryService(AgentMemoryRepository repository, ObjectMapper objectMapper, PrincipalContextService principalContextService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.principalContextService = principalContextService;
    }

    public List<Map<String, Object>> recall(String conversationId) {
        return recallForPrincipal(conversationId, principalContextService.current()).stream()
            .filter(memory -> "SESSION".equals(memory.get("memoryScope")))
            .limit(10)
            .toList();
    }

    public List<Map<String, Object>> recallForPrincipal(String conversationId, PrincipalContext principal) {
        PrincipalContext effective = principal == null ? principalContextService.current() : principal;
        Instant now = Instant.now();
        List<AgentMemory> durable = new java.util.ArrayList<>();
        durable.addAll(repository.findActiveScoped("SYSTEM", "system", effective.tenantId(), now));
        durable.addAll(repository.findActiveScoped("USER", effective.principalId(), effective.tenantId(), now));
        List<Map<String, Object>> session = recallSession(conversationId, effective);
        return java.util.stream.Stream.concat(durable.stream().map(this::readMemory), session.stream()).limit(20).toList();
    }

    private List<Map<String, Object>> recallSession(String conversationId, PrincipalContext principal) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        Instant now = Instant.now();
        return repository.findActiveSession(conversationId, principal.principalId(), principal.tenantId(), now).stream()
            .map(this::readMemory)
            .toList();
    }

    public MemoryDecision evaluateWrite(String sourceType, boolean successful, boolean userConfirmed, boolean containsSensitiveData) {
        if (containsSensitiveData) {
            return new MemoryDecision(false, "sensitive_data");
        }
        if (!(successful || userConfirmed)) {
            return new MemoryDecision(false, "low_confidence_or_unconfirmed");
        }
        return new MemoryDecision(true, successful ? "successful_execution" : "user_correction");
    }

    public void rememberSuccessfulMapping(String conversationId, String agentRunId, String entityName, String schemaCode, boolean allowed) {
        if (!allowed || isBlank(conversationId) || isBlank(entityName) || isBlank(schemaCode)) {
            return;
        }
        Instant now = Instant.now();
        AgentMemory memory = new AgentMemory();
        memory.setId(UUID.randomUUID().toString());
        memory.setConversationId(conversationId);
        memory.setMemoryScope("SESSION");
        memory.setOwnerPrincipal(principalContextService.current().principalId());
        memory.setTenantId(principalContextService.current().tenantId());
        memory.setMemoryType("successful_metadata_mapping");
        memory.setContentJson(writeMemory(Map.of("entityName", entityName, "schemaCode", schemaCode, "source", "verified_agent_run")));
        memory.setSourceAgentRunId(agentRunId);
        memory.setConfidence(0.9D);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setExpiresAt(now.plus(CONVERSATION_MEMORY_TTL_DAYS, ChronoUnit.DAYS));
        repository.save(memory);
    }

    public void rememberUser(String principalId, String tenantId, String memoryType, Map<String, Object> content, int priority, long ttlDays) {
        saveScoped("USER", principalId, tenantId, memoryType, content, priority, ttlDays);
    }

    public void rememberSystem(String tenantId, String memoryType, Map<String, Object> content, int priority) {
        saveScoped("SYSTEM", "system", tenantId, memoryType, content, priority, 0);
    }

    private void saveScoped(String scope, String owner, String tenantId, String memoryType, Map<String, Object> content, int priority, long ttlDays) {
        if (isBlank(owner) || isBlank(memoryType) || content == null || content.isEmpty()) return;
        Instant now = Instant.now();
        AgentMemory memory = new AgentMemory();
        memory.setId(UUID.randomUUID().toString());
        memory.setMemoryScope(scope);
        memory.setOwnerPrincipal(owner);
        memory.setTenantId(isBlank(tenantId) ? "default" : tenantId);
        memory.setMemoryType(memoryType);
        memory.setConversationId(null);
        memory.setContentJson(writeMemory(content));
        memory.setConfidence(1.0D);
        memory.setPriority(priority);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setExpiresAt(ttlDays > 0 ? now.plus(ttlDays, ChronoUnit.DAYS) : null);
        repository.save(memory);
    }

    public long deleteByConversationId(String conversationId) {
        return repository.deleteByConversationId(conversationId);
    }

    public MemorySettingsResponse settingsForCurrentPrincipal() {
        PrincipalContext principal = principalContextService.current();
        List<MemoryEntryDto> personal = repository.findByMemoryScopeAndOwnerPrincipalAndTenantIdOrderByPriorityDescUpdatedAtDesc("USER", principal.principalId(), principal.tenantId()).stream().filter(this::notExpired).map(this::toDto).toList();
        List<MemoryEntryDto> global = principal.superAdmin()
            ? repository.findByMemoryScopeAndOwnerPrincipalAndTenantIdOrderByPriorityDescUpdatedAtDesc("SYSTEM", "system", principal.tenantId()).stream().filter(this::notExpired).map(this::toDto).toList()
            : List.of();
        return new MemorySettingsResponse(principal.principalId(), principal.displayName(), principal.superAdmin(), personal, global, principal.superAdmin());
    }

    public MemoryEntryDto savePersonal(SaveMemoryEntryRequest request) {
        PrincipalContext principal = principalContextService.current();
        return saveEntry("USER", principal.principalId(), principal.tenantId(), request);
    }

    public MemoryEntryDto saveGlobal(SaveMemoryEntryRequest request) {
        PrincipalContext principal = principalContextService.current();
        if (!principal.superAdmin()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "只有超级管理员可以修改全局记忆");
        return saveEntry("SYSTEM", "system", principal.tenantId(), request);
    }

    public void deletePersonal(String id) {
        deleteScoped(id, "USER", principalContextService.current().principalId(), principalContextService.current().tenantId());
    }

    public void deleteGlobal(String id) {
        PrincipalContext principal = principalContextService.current();
        if (!principal.superAdmin()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "只有超级管理员可以删除全局记忆");
        deleteScoped(id, "SYSTEM", "system", principal.tenantId());
    }

    private MemoryEntryDto saveEntry(String scope, String owner, String tenant, SaveMemoryEntryRequest request) {
        if (request == null || request.content() == null || request.content().isBlank()) throw new IllegalArgumentException("记忆内容不能为空");
        Instant now = Instant.now();
        AgentMemory memory = new AgentMemory();
        memory.setId(UUID.randomUUID().toString());
        memory.setMemoryScope(scope);
        memory.setOwnerPrincipal(owner);
        memory.setTenantId(tenant);
        memory.setConversationId(null);
        memory.setMemoryType(limit(request.memoryType(), 64, "manual").trim());
        memory.setContentJson(writeMemory(Map.of("text", limit(request.content(), 4000, ""))));
        memory.setPriority(Math.max(0, Math.min(100, request.priority() == null ? 0 : request.priority())));
        memory.setConfidence(1.0D);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        long ttl = request.ttlDays() == null ? ("SYSTEM".equals(scope) ? 0 : 365) : Math.max(0, Math.min(3650, request.ttlDays()));
        memory.setExpiresAt(ttl == 0 ? null : now.plus(ttl, ChronoUnit.DAYS));
        return toDto(repository.save(memory));
    }

    private void deleteScoped(String id, String scope, String owner, String tenant) {
        repository.findById(id).filter(memory -> scope.equals(memory.getMemoryScope()) && owner.equals(memory.getOwnerPrincipal()) && tenant.equals(memory.getTenantId())).ifPresent(repository::delete);
    }

    private MemoryEntryDto toDto(AgentMemory memory) {
        String content = memory.getContentJson();
        try {
            Map<String, Object> value = objectMapper.readValue(content, new TypeReference<Map<String, Object>>() { });
            content = String.valueOf(value.getOrDefault("text", content));
        } catch (JsonProcessingException ignored) { }
        return new MemoryEntryDto(memory.getId(), memory.getMemoryScope(), memory.getMemoryType(), content, memory.getPriority(), memory.getExpiresAt(), memory.getUpdatedAt(), memory.getOwnerPrincipal());
    }

    private boolean notExpired(AgentMemory memory) { return memory.getExpiresAt() == null || memory.getExpiresAt().isAfter(Instant.now()); }

    private String limit(String value, int max, String fallback) {
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        return safe.length() <= max ? safe : safe.substring(0, max);
    }

    private Map<String, Object> readMemory(AgentMemory memory) {
        try {
            Map<String, Object> result = new LinkedHashMap<>(objectMapper.readValue(memory.getContentJson(), new TypeReference<Map<String, Object>>() { }));
            result.put("memoryType", memory.getMemoryType());
            result.put("memoryScope", memory.getMemoryScope());
            result.put("confidence", memory.getConfidence());
            if (memory.getSourceAgentRunId() != null) result.put("sourceAgentRunId", memory.getSourceAgentRunId());
            return Map.copyOf(result);
        } catch (JsonProcessingException exception) {
            return Map.of("memoryType", memory.getMemoryType(), "status", "unreadable_memory_skipped");
        }
    }

    private String writeMemory(Map<String, Object> memory) {
        try {
            return objectMapper.writeValueAsString(memory);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Agent 记忆", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record MemoryDecision(boolean allowed, String reason) { }
}
