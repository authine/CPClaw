package com.cpclaw.skill;

/** Domain language provider registered by a Skill, never by the platform core. */
public interface SkillSemanticProvider {
    String skillId();
    SkillQuestionSemantics semantics();
}
