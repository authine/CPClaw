package com.cpclaw.task;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.skill.SkillExecutionContext;
import com.cpclaw.skill.SkillExecutor;
import com.cpclaw.skill.SkillRegistry;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Default gateway delegates lifecycle, idempotency, persistence and policy boundaries to the runtime. */
@Service
public class DefaultTaskGateway implements TaskGateway {
    private final SemanticTaskRuntime runtime;
    private final SkillRegistry skillRegistry;
    public DefaultTaskGateway(SemanticTaskRuntime runtime, SkillRegistry skillRegistry) {
        this.runtime = runtime;
        this.skillRegistry = skillRegistry;
    }
    @Override
    public TaskExperienceEnvelope execute(SemanticTaskRequest request, String skillId, SkillExecutionContext context, Consumer<TaskProgressEvent> downstream) {
        SkillExecutor executor = skillRegistry.executor(skillId);
        if (executor == null) {
            return runtime.execute(request, (taskId, progress) -> Map.of(
                "status", "clarification_required",
                "understandingSummary", "当前请求的 Skill 尚未安装或未获批准，无法安全执行。",
                "question", "请在 CPClaw 中选择或安装已批准的 Skill。"
            ), downstream);
        }
        SkillExecutionContext safeContext = context == null ? SkillExecutionContext.empty() : context;
        return runtime.execute(request, (taskId, progress) -> executor.execute(request, safeContext, taskId, progress), downstream);
    }

}
