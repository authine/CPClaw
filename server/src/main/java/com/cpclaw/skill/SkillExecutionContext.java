package com.cpclaw.skill;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-only capability context supplied by a transport after authentication.
 * It is deliberately separate from the user-controlled task request and is
 * never serialized into task events or MCP output.
 */
public record SkillExecutionContext(Map<String, Object> capabilities) {
    public SkillExecutionContext {
        capabilities = capabilities == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(capabilities));
    }

    public static SkillExecutionContext empty() {
        return new SkillExecutionContext(Map.of());
    }

    public <T> T require(String key, Class<T> type) {
        Object value = capabilities.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("执行 Skill 所需的受认证能力不可用。");
        }
        return type.cast(value);
    }
}
