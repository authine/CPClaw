package com.cpclaw.skill.yunshu.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes provider/template output into the shared evidence shape. */
public interface YunshuEvidenceComposer {
    Map<String, Object> compose(Map<String, Object> supplied, String scope, String provenance);

    static Map<String, Object> safeMap(Map<String, Object> supplied) {
        return supplied == null ? Map.of() : supplied;
    }
}
