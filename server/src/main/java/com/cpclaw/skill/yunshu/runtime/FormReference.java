package com.cpclaw.skill.yunshu.runtime;

/** Provider-neutral form reference. Concrete CloudPivot route names are deliberately not assumed here. */
public record FormReference(String applicationCode, String schemaCode, String formCode) { }
