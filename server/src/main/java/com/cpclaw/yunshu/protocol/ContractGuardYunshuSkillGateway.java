package com.cpclaw.yunshu.protocol;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Safe initial Gateway implementation. It establishes the independently
 * addressable protocol endpoint without pretending that the legacy CPClaw
 * executor is an independently verified Yunshu Skill runtime. WP3-WP5 replace
 * this guard with the persistent Task/FormTask runtime.
 */
public class ContractGuardYunshuSkillGateway implements YunshuCanonicalTaskRuntime {
    @Override
    public YunshuTaskEnvelope execute(YunshuTaskRequest request, YunshuHostContext hostContext) {
        String invalid = request.validationError();
        if (!invalid.isBlank()) return failure(request.taskId(), YunshuProtocol.ErrorCode.VALIDATION_ERROR,
            "协议请求不合法：" + invalid, false);
        if (hostContext.tenantId().isBlank() || hostContext.subjectId().isBlank() || hostContext.hostId().isBlank()) {
            return failure(request.taskId(), YunshuProtocol.ErrorCode.PERMISSION_DENIED,
                "缺少可信的租户、主体或宿主上下文。", false);
        }
        return failure(request.taskId(), YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED,
            "Yunshu Skill Runtime 尚未完成 Provider 契约验证；当前 Gateway 仅开放协议联调，不执行云枢读写。", false);
    }

    private YunshuTaskEnvelope failure(String taskId, YunshuProtocol.ErrorCode code, String message, boolean retryable) {
        return new YunshuTaskEnvelope(
            YunshuProtocol.VERSION,
            new YunshuTaskEnvelope.Task(taskId, YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.failed, 0, retryable, Instant.now()),
            new YunshuTaskEnvelope.Interaction(YunshuProtocol.InteractionType.none, List.of()),
            new YunshuTaskEnvelope.Draft(Map.of(), "", 0, List.of()),
            new YunshuTaskEnvelope.Validation(List.of()),
            new YunshuTaskEnvelope.Next(Map.of(), "", null),
            new YunshuTaskEnvelope.Completion("failed", List.of()),
            Map.of("message", message), Map.of(),
            new YunshuTaskEnvelope.HostAction(YunshuProtocol.HostActionType.report_failure, false, Map.of()),
            Map.of(), new YunshuTaskEnvelope.Error(code, message, retryable)
        );
    }
}
