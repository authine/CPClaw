package com.cpclaw.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskSpec;
import com.cpclaw.task.entity.SemanticTaskRun;
import com.cpclaw.task.repository.SemanticTaskEventRepository;
import com.cpclaw.task.repository.SemanticTaskRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticTaskRuntimeContinuationTests {
    @Mock private SemanticTaskRunRepository runRepository;
    @Mock private SemanticTaskEventRepository eventRepository;
    @Mock private TaskEvidencePlanner evidencePlanner;

    private TaskContinuationTokenService tokenService;
    private ObjectMapper objectMapper;
    private SemanticTaskRuntime runtime;

    @BeforeEach
    void setUp() {
        tokenService = new TaskContinuationTokenService("runtime-test-secret", 900);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        runtime = new SemanticTaskRuntime(runRepository, eventRepository, objectMapper, tokenService, evidencePlanner);
        lenient().when(runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndClientRequestId(any(), any(), any(), any())).thenReturn(Optional.empty());
        lenient().when(runRepository.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndTurnId(any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void continuationRestoresParentAndCreatesChildOnce() throws Exception {
        String token = tokenService.issue("parent-1", "alice");
        SemanticTaskRun parent = parent("parent-1", "alice", false, token, "needs_input");
        when(runRepository.findLockedById("parent-1")).thenReturn(Optional.of(parent));
        when(runRepository.consumeContinuation("parent-1")).thenReturn(1);
        AtomicInteger executions = new AtomicInteger();

        SemanticTaskRequest request = request("alice", token, "child-1");
        TaskExperienceEnvelope result = runtime.execute(request, (taskId, events) -> {
            executions.incrementAndGet();
            return Map.of("status", "completed", "understandingSummary", "已完成", "completion", Map.of("state", "complete", "answerReady", true), "evidence", Map.of());
        }, null);

        assertEquals("completed", result.task().status());
        assertEquals(1, executions.get());
        verify(runRepository).consumeContinuation("parent-1");
        verify(runRepository).save(any(SemanticTaskRun.class));
    }

    @Test
    void wrongPrincipalCannotContinueAndExecutorIsNotCalled() throws Exception {
        String token = tokenService.issue("parent-1", "alice");
        SemanticTaskRun parent = parent("parent-1", "alice", false, token, "needs_input");
        AtomicInteger executions = new AtomicInteger();

        TaskExperienceEnvelope result = runtime.execute(request("bob", token, "child-1"), (taskId, events) -> {
            executions.incrementAndGet();
            return Map.of("status", "completed");
        }, null);

        assertEquals("blocked", result.task().status());
        assertEquals(0, executions.get());
        verify(runRepository, never()).consumeContinuation(any());
    }

    @Test
    void consumedParentCannotBeContinuedAgain() throws Exception {
        String token = tokenService.issue("parent-1", "alice");
        SemanticTaskRun parent = parent("parent-1", "alice", true, token, "needs_input");
        when(runRepository.findLockedById("parent-1")).thenReturn(Optional.of(parent));
        AtomicInteger executions = new AtomicInteger();

        TaskExperienceEnvelope result = runtime.execute(request("alice", token, "child-2"), (taskId, events) -> {
            executions.incrementAndGet();
            return Map.of("status", "completed");
        }, null);

        assertEquals("blocked", result.task().status());
        assertEquals(0, executions.get());
        verify(runRepository, never()).consumeContinuation(any());
    }

    private SemanticTaskRequest request(String principal, String token, String clientRequestId) {
        TaskSpec spec = new TaskSpec("cpclaw-delegation/1.0", "补充信息", List.of(), Map.of(), List.of(), "agent_evidence", "conv-1", "turn-2", clientRequestId, token);
        return new SemanticTaskRequest("mcp", "install-1", principal, clientRequestId, "turn-2", "补充信息", List.of(), spec);
    }

    private SemanticTaskRun parent(String id, String principal, boolean consumed, String token, String status) throws Exception {
        SemanticTaskRun parent = new SemanticTaskRun();
        parent.setId(id);
        parent.setChannel("mcp");
        parent.setInstallationKey("install-1");
        parent.setExternalPrincipal(principal);
        parent.setClientRequestId("parent-request");
        parent.setTurnId("turn-1");
        parent.setStatus(status);
        parent.setContinuationConsumed(consumed);
        parent.setUpdatedAt(Instant.now());
        parent.setTaskSpecJson(objectMapper.writeValueAsString(TaskSpec.empty("原任务", "conv-1", "turn-1", "parent-request")));
        TaskExperienceEnvelope envelope = new TaskExperienceEnvelope("2.0", new TaskExperienceEnvelope.Task(id, status, Instant.now(), false), Map.of(), List.of(), Map.of("message", "补充"), Map.of("type", "clarify"), Map.of("type", "ask_user", "allowAnotherMcpCallThisTurn", false), Map.of("state", status, "answerReady", false), Map.of(), Map.of("allowed", true, "token", token));
        parent.setResultJson(objectMapper.writeValueAsString(envelope));
        return parent;
    }
}
