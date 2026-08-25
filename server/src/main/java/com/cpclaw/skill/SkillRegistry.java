package com.cpclaw.skill;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Resolves installed Skill capabilities by stable skill id. */
@Service
public class SkillRegistry {
    private final Map<String, SkillSemanticProvider> semantics;
    private final Map<String, SkillExecutor> executors;

    public SkillRegistry(List<SkillSemanticProvider> providers, List<SkillExecutor> executors) {
        this.semantics = providers == null ? Map.of() : providers.stream().collect(Collectors.toUnmodifiableMap(SkillSemanticProvider::skillId, Function.identity(), (left, right) -> left));
        this.executors = executors == null ? Map.of() : executors.stream().collect(Collectors.toUnmodifiableMap(SkillExecutor::skillId, Function.identity(), (left, right) -> left));
    }

    public SkillSemanticProvider semantic(String skillId) {
        return semantics.get(skillId);
    }

    public SkillExecutor executor(String skillId) {
        return executors.get(skillId);
    }
}
