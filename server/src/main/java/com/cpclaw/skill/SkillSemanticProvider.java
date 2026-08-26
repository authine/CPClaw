package com.cpclaw.skill;

/** Optional semantic provider supplied by a Skill template/plugin. */
public interface SkillSemanticProvider {
    String skillId();
    SkillQuestionSemantics semantics();
}
