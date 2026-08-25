package com.cpclaw.task;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskExperienceEnvelope;
import com.cpclaw.task.dto.TaskProgressEvent;
import com.cpclaw.skill.SkillExecutionContext;
import java.util.function.Consumer;

/** Stable channel-neutral gateway shared by Web, MCP and future CLI/Remote adapters. */
public interface TaskGateway {
    TaskExperienceEnvelope execute(SemanticTaskRequest request, SemanticTaskRuntime.TaskExecutor executor, Consumer<TaskProgressEvent> downstream);
    TaskExperienceEnvelope execute(SemanticTaskRequest request, String skillId, SkillExecutionContext context, Consumer<TaskProgressEvent> downstream);
}
