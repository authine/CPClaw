package com.cpclaw.common.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    private static final String SENSITIVE_KEY = "password|token|cookie|authorization|api[_-]?key|apikey|secret|credential|session|access[_-]?token|accesstoken|refresh[_-]?token|refreshtoken";
    private static final List<MaskRule> RULES = List.of(
        new MaskRule(Pattern.compile("(?i)(\\\"(?:" + SENSITIVE_KEY + ")\\\"\\s*:\\s*)\\\"[^\\\"]*\\\""), "$1\"***\""),
        new MaskRule(Pattern.compile("(?i)('(?:" + SENSITIVE_KEY + ")'\\s*:\\s*)'[^']*'"), "$1'***'"),
        new MaskRule(Pattern.compile("(?i)((?:" + SENSITIVE_KEY + ")\\s*[:=]\\s*)[^\\s,;}&]+"), "$1***")
    );

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String masked = value;
        for (MaskRule rule : RULES) {
            masked = rule.pattern().matcher(masked).replaceAll(rule.replacement());
        }
        return masked;
    }

    private record MaskRule(Pattern pattern, String replacement) {
    }
}
