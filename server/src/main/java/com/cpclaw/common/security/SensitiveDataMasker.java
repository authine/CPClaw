package com.cpclaw.common.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataMasker {

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("(?i)(password|token|cookie|authorization|api[_-]?key|secret|credential|session|key)\\s*[:=]\\s*[^\\s,;]+")
    );

    public String mask(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String masked = value;
        for (Pattern pattern : PATTERNS) {
            masked = pattern.matcher(masked).replaceAll("$1=***");
        }
        return masked;
    }
}
