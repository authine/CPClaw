package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.agent.MetadataExecutionPlanner.MetadataExecutionPlan;

/** Validates a metadata-derived plan against generic execution policy. */
public interface YunshuPlanValidator {
    YunshuPlanValidation validate(MetadataExecutionPlan plan, String intent, YunshuExecutionScope scope);
}
