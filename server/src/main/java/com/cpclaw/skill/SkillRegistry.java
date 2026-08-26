package com.cpclaw.skill;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Resolves installed Skill capabilities by stable skill id. */
@Service
public class SkillRegistry {
    private final Map<String, SkillSemanticProvider> semantics;
    private final Map<String, SkillExecutor> executors;
    private final SkillCatalog catalog;

    @Autowired
    public SkillRegistry(List<SkillSemanticProvider> providers, List<SkillExecutor> executors, SkillCatalog catalog) {
        this.semantics = providers == null ? Map.of() : providers.stream().collect(Collectors.toUnmodifiableMap(SkillSemanticProvider::skillId, Function.identity(), (left, right) -> left));
        this.executors = executors == null ? Map.of() : executors.stream().collect(Collectors.toUnmodifiableMap(SkillExecutor::skillId, Function.identity(), (left, right) -> left));
        this.catalog = catalog;
    }

    public SkillRegistry(List<SkillSemanticProvider> providers, List<SkillExecutor> executors) {
        this(providers, executors, new SkillCatalog());
    }

    public SkillSemanticProvider semantic(String skillId) {
        return semantics.get(skillId);
    }

    public SkillExecutor executor(String skillId) {
        SkillExecutor direct = executors.get(skillId);
        if (direct != null) return direct;
        SkillCatalog.SkillDefinition definition = catalog.findRegistered(skillId).orElse(null);
        if (definition == null) return null;
        // Declarative Markdown Skills never supply executable code. They may
        // bind only to an already registered server-owned executor.
        if ("metadata-react-executor".equals(definition.executorId())) {
            return executors.get(SkillCatalog.YUNSHU_BUSINESS_SYSTEM);
        }
        return null;
    }
}
