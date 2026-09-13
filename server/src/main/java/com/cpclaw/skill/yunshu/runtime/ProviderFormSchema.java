package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.skill.yunshu.form.FormFieldSlot;
import java.util.List;

public record ProviderFormSchema(FormReference formRef, String metadataVersion, List<FormFieldSlot> fields) {
    public ProviderFormSchema { fields = fields == null ? List.of() : List.copyOf(fields); }
}
