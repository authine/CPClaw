package com.cpclaw.insight.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Resolves published declarative templates without embedding domain terms. */
@Service
public class AnalysisTemplateRegistry {
    private static final List<String> ALLOWED_OPERATORS = List.of(
        "count", "filter", "groupBy", "aggregate", "rank", "join", "render", "riskDetect"
    );

    private final ReportSkillTemplateRepository repository;
    private final ObjectMapper objectMapper;

    public AnalysisTemplateRegistry(ReportSkillTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<AnalysisTemplateManifest> resolve(String skillId, String templateId) {
        if (templateId == null || templateId.isBlank()) return Optional.empty();
        return repository.findById(templateId)
            .filter(ReportSkillTemplate::isEnabled)
            .filter(template -> "approved".equalsIgnoreCase(template.getPublicationStatus()))
            .filter(template -> skillId == null || skillId.isBlank() || skillId.equals(template.getSkillId()))
            .flatMap(this::parseAndValidate);
    }

    private Optional<AnalysisTemplateManifest> parseAndValidate(ReportSkillTemplate template) {
        if (template.getManifestJson() == null || template.getManifestJson().isBlank()) return Optional.empty();
        try {
            AnalysisTemplateManifest manifest = objectMapper.readValue(template.getManifestJson(), AnalysisTemplateManifest.class);
            if (manifest.id() == null || manifest.id().isBlank() || manifest.plan() == null) return Optional.empty();
            if (manifest.plan().stream().anyMatch(operation -> operation == null || !ALLOWED_OPERATORS.contains(operation.op()))) return Optional.empty();
            return Optional.of(manifest);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }
}
