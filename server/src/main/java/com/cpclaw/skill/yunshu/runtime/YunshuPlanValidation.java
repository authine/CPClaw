package com.cpclaw.skill.yunshu.runtime;

import java.util.List;

/** Outcome of the generic plan and policy gate. */
public record YunshuPlanValidation(String state, boolean executable, boolean requiresConfirmation, List<String> reasons) {
    public YunshuPlanValidation {
        state = state == null ? "blocked" : state;
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static YunshuPlanValidation blocked(String reason) {
        return new YunshuPlanValidation("blocked", false, false, List.of(reason));
    }
}
