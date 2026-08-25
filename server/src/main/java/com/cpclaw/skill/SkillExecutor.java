package com.cpclaw.skill;

import com.cpclaw.task.dto.SemanticTaskRequest;
import com.cpclaw.task.dto.TaskProgressEvent;
import java.util.Map;
import java.util.function.Consumer;

/** Executable domain capability exposed through the generic task kernel. */
public interface SkillExecutor {
    String skillId();
    Map<String, Object> execute(SemanticTaskRequest request, SkillExecutionContext context, String taskId, Consumer<TaskProgressEvent> progress);
}
