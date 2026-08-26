package com.cpclaw.skill;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.identity.PrincipalContextService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Governed Markdown Skill registration; scripts, URLs and arbitrary executors are never accepted. */
@RestController
@RequestMapping("/api/settings/skills/markdown")
public class MarkdownSkillAdminController {
    private final SkillCatalog catalog;
    private final PrincipalContextService principals;
    public MarkdownSkillAdminController(SkillCatalog catalog, PrincipalContextService principals) { this.catalog = catalog; this.principals = principals; }

    @GetMapping
    public ApiResponse<List<Map<String,Object>>> list() {
        requireAdmin();
        return ApiResponse.ok(catalog.listAllGoverned().stream().map(this::view).toList());
    }

    @PostMapping
    public ApiResponse<Map<String,Object>> register(@RequestBody Registration request) {
        requireAdmin();
        if (request == null || request.markdown() == null || request.markdown().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill Markdown 不能为空");
        try { return ApiResponse.ok(view(catalog.registerMarkdownDraft(request.markdown(), request.source()))); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
    }

    @PostMapping("/{id}/{status}")
    public ApiResponse<Map<String,Object>> status(@PathVariable String id, @PathVariable String status) {
        requireAdmin();
        try { return ApiResponse.ok(view(catalog.setPublicationStatus(id, status.trim().toLowerCase()))); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
    }

    private void requireAdmin() { if (!principals.current().superAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有超级管理员可以管理 Markdown Skill"); }
    private Map<String,Object> view(SkillCatalog.SkillDefinition skill) {
        Map<String,Object> value = new LinkedHashMap<>(); value.put("id", skill.id()); value.put("name", skill.name()); value.put("scope", skill.scope());
        value.put("executorId", skill.executorId()); value.put("requiresConfirmationForWrite", skill.requiresConfirmationForWrite()); value.put("version", skill.version()); value.put("source", skill.source()); value.put("publicationStatus", skill.publicationStatus()); return value;
    }
    public record Registration(String markdown, String source) { }
}
