package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.task.dto.TaskSpec;
import java.util.Map;

/** Evaluates deliverables from explicit evidence declarations only. */
public interface YunshuCompletionValidator {
    Map<String, Object> validate(TaskSpec spec, Map<String, Object> evidence, String taskStatus);
}
