package com.cpclaw.skill.yunshu.form;

/** Trusted identity scope supplied by the Gateway, never reconstructed from host input. */
public record FormTaskScope(String tenantId, String subjectId, String hostId) { }
