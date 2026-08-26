package com.cpclaw.insight.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Validates the declarative template DSL before it can be published. */
@Service
public class AnalysisTemplateManifestValidator {
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
        "count", "filter", "groupBy", "aggregate", "rank", "join", "render", "riskDetect"
    );
    private final ObjectMapper objectMapper;

    public AnalysisTemplateManifestValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(String json) {
        List<String> errors = new ArrayList<>();
        if (json == null || json.isBlank()) return new ValidationResult(false, List.of("manifest 不能为空"), null);
        try {
            AnalysisTemplateManifest manifest = objectMapper.readValue(json, AnalysisTemplateManifest.class);
            if (blank(manifest.id())) errors.add("manifest.id 不能为空");
            if (blank(manifest.version())) errors.add("manifest.version 不能为空");
            if (blank(manifest.skillId())) errors.add("manifest.skillId 不能为空");
            if (manifest.plan() == null || manifest.plan().isEmpty()) errors.add("manifest.plan 不能为空");
            if (manifest.plan() != null) {
                manifest.plan().forEach(operation -> {
                    if (operation == null || blank(operation.op()) || !ALLOWED_OPERATORS.contains(operation.op())) {
                        errors.add("plan 只能使用已批准的通用算子");
                    }
                    if (operation != null && containsForbiddenPayload(operation)) {
                        errors.add("plan 不得包含脚本、SQL、URL 或凭据");
                    }
                });
            }
            return new ValidationResult(errors.isEmpty(), List.copyOf(errors), manifest);
        } catch (Exception exception) {
            return new ValidationResult(false, List.of("manifest JSON 或字段结构无效"), null);
        }
    }

    private boolean containsForbiddenPayload(AnalysisTemplateManifest.Operation operation) {
        String value = String.valueOf(operation.input()) + String.valueOf(operation.as()) + String.valueOf(operation.options());
        return value.matches("(?is).*\\b(select|insert|update|delete|drop|http://|https://|script|password|token|secret|credential)\\b.*");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public record ValidationResult(boolean valid, List<String> errors, AnalysisTemplateManifest manifest) { }
}
