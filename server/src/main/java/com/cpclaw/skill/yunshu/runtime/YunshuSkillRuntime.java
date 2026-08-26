package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskProgressEvent;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Channel-neutral lifecycle contract for the universal Yunshu Skill. Concrete
 * providers and approved template plugins implement the stage details; host
 * adapters must not implement semantic branches themselves.
 */
public interface YunshuSkillRuntime {
    Map<String, Object> execute(SemanticTaskRequest request, YunshuExecutionScope scope,
            String taskId, Consumer<TaskProgressEvent> progress);
}
