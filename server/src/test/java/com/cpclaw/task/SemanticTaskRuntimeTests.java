package com.cpclaw.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskSpec;
import com.cpclaw.task.entity.SemanticTaskRun;
import com.cpclaw.task.repository.SemanticTaskEventRepository;
import com.cpclaw.task.repository.SemanticTaskRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SemanticTaskRuntimeTests {
    @Test
    void samePrincipalTurnAndRequestReplayWithoutExecutingAgain() {
        SemanticTaskRunRepository runs = mock(SemanticTaskRunRepository.class);
        SemanticTaskEventRepository events = mock(SemanticTaskEventRepository.class);
        AtomicReference<SemanticTaskRun> stored = new AtomicReference<>();
        when(runs.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndClientRequestId(any(), any(), any(), any()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(runs.findFirstByChannelAndInstallationKeyAndExternalPrincipalAndTurnId(any(), any(), any(), any()))
            .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(runs.saveAndFlush(any(SemanticTaskRun.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(runs.save(any(SemanticTaskRun.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SemanticTaskRuntime runtime = new SemanticTaskRuntime(
            runs, events, mapper, new TaskContinuationTokenService("test-secret", 900), new TaskEvidencePlanner()
        );
        TaskSpec spec = TaskSpec.empty("查询目标", "conversation-1", "turn-1", "request-1");
        SemanticTaskRequest request = new SemanticTaskRequest("mcp", "installation-1", "principal-a", "request-1", "turn-1", "查询目标", List.of(), spec);
        AtomicInteger executions = new AtomicInteger();

        TaskExperienceEnvelope first = runtime.execute(request, (taskId, progress) -> {
            executions.incrementAndGet();
            return Map.of("status", "completed", "understandingSummary", "完成", "result", Map.of("total", 1));
        }, ignored -> { });
        TaskExperienceEnvelope replay = runtime.execute(request, (taskId, progress) -> {
            executions.incrementAndGet();
            return Map.of("status", "completed", "understandingSummary", "不应再次执行");
        }, ignored -> { });

        assertEquals(1, executions.get());
        assertEquals(first.task().id(), replay.task().id());
    }

    @Test
    void presentationModeControlsCompletedHostActionWithoutBusinessCoupling() {
        SemanticTaskRunRepository runs = mock(SemanticTaskRunRepository.class);
        SemanticTaskEventRepository events = mock(SemanticTaskEventRepository.class);
        when(runs.saveAndFlush(any(SemanticTaskRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.save(any(SemanticTaskRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SemanticTaskRuntime runtime = new SemanticTaskRuntime(
            runs, events, mapper, new TaskContinuationTokenService("test-secret", 900), new TaskEvidencePlanner()
        );
        TaskSpec reportSpec = new TaskSpec("cpclaw-delegation/1.0", "生成 CPClaw 报告", List.of(), Map.of(), List.of(), "cpclaw_report", "conv", "turn", "req", "");
        SemanticTaskRequest reportRequest = new SemanticTaskRequest("mcp", "installation-1", "principal-a", "req", "turn", "生成报告", List.of(), reportSpec);
        TaskExperienceEnvelope report = runtime.execute(reportRequest, (taskId, progress) -> Map.of("status", "completed", "understandingSummary", "完成"), ignored -> { });
        assertEquals("respond_directly", report.hostAction().get("type"));

        TaskSpec evidenceSpec = TaskSpec.empty("提供证据", "conv", "turn-2", "req-2");
        SemanticTaskRequest evidenceRequest = new SemanticTaskRequest("mcp", "installation-1", "principal-a", "req-2", "turn-2", "提供证据", List.of(), evidenceSpec);
        TaskExperienceEnvelope evidence = runtime.execute(evidenceRequest, (taskId, progress) -> Map.of("status", "completed", "understandingSummary", "完成"), ignored -> { });
        assertEquals("compose_answer", evidence.hostAction().get("type"));
    }
}
