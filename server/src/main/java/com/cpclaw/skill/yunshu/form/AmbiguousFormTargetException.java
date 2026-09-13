package com.cpclaw.skill.yunshu.form;

import java.util.List;
import java.util.Map;

/** No target is selected until the user resolves a metadata-backed ambiguity. */
public final class AmbiguousFormTargetException extends RuntimeException {
    private final List<Map<String, Object>> candidates;

    public AmbiguousFormTargetException(List<Map<String, Object>> candidates) {
        super("找到多个可填写的业务表单，请补充应用或选择具体表单。");
        this.candidates = List.copyOf(candidates);
    }

    public List<Map<String, Object>> candidates() { return candidates; }
}
