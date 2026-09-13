package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.skill.yunshu.form.FormValidationIssue;
import java.util.List;

public record ProviderDraftValidation(boolean contractVerified, List<FormValidationIssue> issues, String message) {
    public ProviderDraftValidation { issues = issues == null ? List.of() : List.copyOf(issues); }
    public static ProviderDraftValidation unverified(String message) {
        return new ProviderDraftValidation(false, List.of(), message);
    }
}
