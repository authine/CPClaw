package com.cpclaw.skill.yunshu.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Generic evidence normalizer; it never infers business meaning from names. */
@Component
public final class DefaultYunshuEvidenceComposer implements YunshuEvidenceComposer {
    @Override
    public Map<String, Object> compose(Map<String, Object> supplied, String scope, String provenance) {
        Map<String, Object> result = new LinkedHashMap<>(YunshuEvidenceComposer.safeMap(supplied));
        result.putIfAbsent("scope", scope == null || scope.isBlank() ? "当前账号可见范围" : scope);
        putList(result, "facts");
        putList(result, "metrics");
        putList(result, "riskSignals");
        putList(result, "relations");
        putList(result, "caveats");
        putList(result, "provenance");
        if (provenance != null && !provenance.isBlank()) {
            List<Object> values = new ArrayList<>((List<?>) result.get("provenance"));
            if (!values.contains(provenance)) values.add(provenance);
            result.put("provenance", List.copyOf(values));
        }
        result.putIfAbsent("coverage", Map.of());
        return Map.copyOf(result);
    }

    private void putList(Map<String, Object> result, String key) {
        if (!(result.get(key) instanceof List<?>)) result.put(key, List.of());
    }
}
