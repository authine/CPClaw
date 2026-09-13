package com.cpclaw.skill.yunshu.form;

import com.cpclaw.skill.yunshu.runtime.ProviderFormSchema;

/** Resolves a read-only form schema for a trusted task scope; write contracts are separate. */
public interface YunshuFormSchemaProvider {
    ProviderFormSchema resolve(String goal, FormTaskScope scope);
}
