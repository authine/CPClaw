package com.cpclaw.insight.template;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.identity.PrincipalContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Administrator workbench API for declarative Skill template lifecycle. */
@RestController
@RequestMapping("/api/templates/analysis")
public class AnalysisTemplateAdminController {
    private final ReportSkillTemplateRepository repository;
    private final AnalysisTemplateManifestValidator validator;
    private final PrincipalContextService principalContextService;
    private final ObjectMapper objectMapper;

    public AnalysisTemplateAdminController(ReportSkillTemplateRepository repository,
            AnalysisTemplateManifestValidator validator,
            PrincipalContextService principalContextService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.validator = validator;
        this.principalContextService = principalContextService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        requireAdmin();
        return ApiResponse.ok(repository.findAllByOrderByUpdatedAtDesc().stream().map(this::view).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody RegistrationRequest request) {
        requireAdmin();
        AnalysisTemplateManifestValidator.ValidationResult result = validator.validate(request.manifestJson());
        if (!result.valid()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("；", result.errors()));
        ReportSkillTemplate template = request.id() == null || request.id().isBlank()
            ? new ReportSkillTemplate() : repository.findById(request.id()).orElseGet(ReportSkillTemplate::new);
        Instant now = Instant.now();
        if (template.getId() == null || template.getId().isBlank()) template.setId(UUID.randomUUID().toString());
        template.setSkillCode(required(request.skillCode(), result.manifest().skillId()));
        template.setSkillId(result.manifest().skillId());
        template.setName(required(request.name(), result.manifest().id()));
        template.setDescription(request.description() == null ? "" : request.description().trim());
        template.setManifestJson(request.manifestJson());
        template.setSignature(signature(request.manifestJson()));
        template.setTemplateKind(request.templateKind() == null || request.templateKind().isBlank() ? "generic" : request.templateKind().trim());
        template.setPublicationStatus("draft");
        template.setEnabled(false);
        template.setPriority(request.priority());
        template.setVersion(Math.max(1, template.getVersion()));
        if (template.getCreatedAt() == null) template.setCreatedAt(now);
        template.setUpdatedAt(now);
        return ApiResponse.ok(view(repository.save(template)));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable String id) {
        requireAdmin();
        ReportSkillTemplate template = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        AnalysisTemplateManifestValidator.ValidationResult result = validator.validate(template.getManifestJson());
        if (!result.valid()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("；", result.errors()));
        template.setPublicationStatus("approved");
        template.setEnabled(true);
        template.setUpdatedAt(Instant.now());
        return ApiResponse.ok(view(repository.save(template)));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Map<String, Object>> disable(@PathVariable String id) {
        requireAdmin();
        ReportSkillTemplate template = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        template.setPublicationStatus("disabled");
        template.setEnabled(false);
        template.setUpdatedAt(Instant.now());
        return ApiResponse.ok(view(repository.save(template)));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<Map<String, Object>> review(@PathVariable String id, @RequestBody ReviewRequest request) {
        requireAdmin();
        ReportSkillTemplate template = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模板不存在"));
        String status = request == null || request.status() == null ? "rejected" : request.status().trim().toLowerCase();
        if (!List.of("approved", "rejected").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审核状态只能是 approved 或 rejected");
        template.setPublicationStatus(status);
        template.setEnabled("approved".equals(status));
        template.setUpdatedAt(Instant.now());
        return ApiResponse.ok(view(repository.save(template)));
    }

    private void requireAdmin() {
        if (!principalContextService.current().superAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有超级管理员可以管理分析模板");
    }

    private Map<String, Object> view(ReportSkillTemplate template) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", template.getId());
        value.put("skillCode", template.getSkillCode());
        value.put("skillId", template.getSkillId() == null ? "" : template.getSkillId());
        value.put("name", template.getName());
        value.put("description", template.getDescription() == null ? "" : template.getDescription());
        value.put("templateKind", template.getTemplateKind());
        value.put("publicationStatus", template.getPublicationStatus());
        value.put("enabled", template.isEnabled());
        value.put("version", template.getVersion());
        value.put("priority", template.getPriority());
        value.put("signature", template.getSignature() == null ? "" : template.getSignature());
        value.put("manifestJson", template.getManifestJson() == null ? "" : template.getManifestJson());
        value.put("updatedAt", template.getUpdatedAt());
        return value;
    }

    private String signature(String manifestJson) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(manifestJson.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法生成模板签名", exception); }
    }

    private String required(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

    public record RegistrationRequest(String id, String skillCode, String name, String description, String manifestJson, String templateKind, int priority) { }
    public record ReviewRequest(String status) { }
}
