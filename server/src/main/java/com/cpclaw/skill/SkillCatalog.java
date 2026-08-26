package com.cpclaw.skill;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Server-owned skill allowlist. A model may recommend a skill, but it never
 * receives an executable connector, endpoint, or credential from this catalog.
 */
@Service
public class SkillCatalog {

    public static final String YUNSHU_BUSINESS_SYSTEM = "yunshu-business-system";
    public static final String YUNSHU_INTELLIGENT_INQUIRY = "yunshu-intelligent-inquiry";

    private static final List<SkillDefinition> BUILTIN_SKILLS = List.of(
        new SkillDefinition(
            YUNSHU_BUSINESS_SYSTEM,
            "云枢业务系统",
            "基于已同步的云枢元数据查询、分析和受确认保护的业务操作",
            "metadata-react-executor",
            true
        ),
        new SkillDefinition(
            YUNSHU_INTELLIGENT_INQUIRY,
            "云枢智能问数",
            "根据用户目标和已同步元数据，规划数据范围、指标、关联、图形表达和业务总结",
            "metadata-insight-planner",
            false
        )
    );
    private final Map<String, SkillDefinition> installed = new ConcurrentHashMap<>();
    private final MarkdownSkillRepository repository;

    @Autowired
    public SkillCatalog(MarkdownSkillRepository repository) {
        this.repository = repository;
        BUILTIN_SKILLS.forEach(skill -> installed.put(skill.id(), skill));
        repository.findAllByPublicationStatusOrderByUpdatedAtDesc("approved").forEach(this::bindPersisted);
    }

    /** Lightweight constructor retained for isolated framework tests. */
    public SkillCatalog() {
        this.repository = null;
        BUILTIN_SKILLS.forEach(skill -> installed.put(skill.id(), skill));
    }

    public Optional<SkillDefinition> findRegistered(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(installed.get(skillId.trim()));
    }

    public boolean isRegistered(String skillId) {
        return findRegistered(skillId).isPresent();
    }

    /** Registers only declarative Markdown manifests; execution remains bound to a server allowlisted executor. */
    public SkillDefinition registerMarkdown(String markdown, String source, boolean approved) {
        if (!approved) throw new IllegalArgumentException("Markdown Skill 尚未通过管理员审核");
        Map<String, String> frontMatter = parseFrontMatter(markdown);
        String id = required(frontMatter, "id");
        String version = frontMatter.getOrDefault("version", "1.0.0");
        String executor = required(frontMatter, "executor");
        if (!List.of("metadata-react-executor", "metadata-insight-planner", "workflow-read-executor").contains(executor)) {
            throw new IllegalArgumentException("Skill executor 不在服务端白名单内");
        }
        SkillDefinition definition = new SkillDefinition(id, frontMatter.getOrDefault("name", id), frontMatter.getOrDefault("description", ""), executor, !"READ".equalsIgnoreCase(frontMatter.getOrDefault("risk", "READ")), version, source == null ? "markdown" : source, "approved");
        if (repository != null) {
            MarkdownSkill record = repository.findById(id).orElseGet(MarkdownSkill::new);
            record.setId(id); record.setName(definition.name()); record.setScope(definition.scope());
            record.setExecutorId(executor); record.setRequiresConfirmationForWrite(definition.requiresConfirmationForWrite());
            record.setVersion(version); record.setSource(definition.source()); record.setMarkdown(markdown);
            record.setPublicationStatus("approved"); record.setSignature(signature(markdown));
            Instant now = Instant.now(); if (record.getCreatedAt() == null) record.setCreatedAt(now); record.setUpdatedAt(now);
            repository.save(record);
        }
        installed.put(id, definition);
        return definition;
    }

    /** Stores a new declaration as draft; publication requires a separate review transition. */
    public SkillDefinition registerMarkdownDraft(String markdown, String source) {
        Map<String, String> frontMatter = parseFrontMatter(markdown);
        String id = required(frontMatter, "id");
        String version = frontMatter.getOrDefault("version", "1.0.0");
        String executor = required(frontMatter, "executor");
        if (!allowedExecutor(executor)) throw new IllegalArgumentException("Skill executor 不在服务端白名单内");
        SkillDefinition definition = new SkillDefinition(id, frontMatter.getOrDefault("name", id), frontMatter.getOrDefault("description", ""), executor, !"READ".equalsIgnoreCase(frontMatter.getOrDefault("risk", "READ")), version, source == null ? "markdown" : source, "draft");
        if (repository != null) {
            MarkdownSkill record = repository.findById(id).orElseGet(MarkdownSkill::new);
            record.setId(id); record.setName(definition.name()); record.setScope(definition.scope()); record.setExecutorId(executor);
            record.setRequiresConfirmationForWrite(definition.requiresConfirmationForWrite()); record.setVersion(version); record.setSource(definition.source());
            record.setMarkdown(markdown); record.setPublicationStatus("draft"); record.setSignature(signature(markdown));
            Instant now = Instant.now(); if (record.getCreatedAt() == null) record.setCreatedAt(now); record.setUpdatedAt(now); repository.save(record);
        }
        return definition;
    }

    public List<SkillDefinition> listPublished() { return installed.values().stream().filter(item -> "approved".equals(item.publicationStatus())).toList(); }

    public List<SkillDefinition> listAllGoverned() {
        if (repository == null) return List.copyOf(installed.values());
        return repository.findAll().stream().map(skill -> new SkillDefinition(skill.getId(), skill.getName(), skill.getScope(), skill.getExecutorId(), skill.isRequiresConfirmationForWrite(), skill.getVersion(), skill.getSource(), skill.getPublicationStatus())).toList();
    }

    public SkillDefinition setPublicationStatus(String id, String status) {
        if (id == null || status == null || !List.of("draft", "review", "approved", "disabled", "rejected").contains(status)) throw new IllegalArgumentException("无效的 Skill 发布状态");
        if (repository != null) {
            MarkdownSkill record = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Skill 不存在"));
            record.setPublicationStatus(status); record.setUpdatedAt(Instant.now()); repository.save(record);
            if ("approved".equals(status)) bindPersisted(record); else installed.remove(id);
        } else if (!installed.containsKey(id)) {
            throw new IllegalArgumentException("Skill 不存在");
        }
        return installed.get(id);
    }

    private void bindPersisted(MarkdownSkill skill) {
        if (skill == null || !allowedExecutor(skill.getExecutorId())) return;
        installed.put(skill.getId(), new SkillDefinition(skill.getId(), skill.getName(), skill.getScope(), skill.getExecutorId(), skill.isRequiresConfirmationForWrite(), skill.getVersion(), skill.getSource(), skill.getPublicationStatus()));
    }

    private boolean allowedExecutor(String executor) { return List.of("metadata-react-executor", "metadata-insight-planner", "workflow-read-executor").contains(executor); }
    private String signature(String markdown) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(markdown.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法生成 Skill 签名", exception); }
    }

    private Map<String, String> parseFrontMatter(String markdown) {
        if (markdown == null || !markdown.trim().startsWith("---")) throw new IllegalArgumentException("Skill Markdown 必须包含 front matter");
        Map<String, String> result = new java.util.LinkedHashMap<>();
        String[] lines = markdown.trim().split("\\R");
        for (int i = 1; i < lines.length && !lines[i].trim().equals("---"); i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) result.put(lines[i].substring(0, colon).trim(), lines[i].substring(colon + 1).trim().replaceAll("^\\\"|\\\"$", ""));
        }
        return result;
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9._-]{2,96}")) throw new IllegalArgumentException("Skill manifest 缺少合法 " + key);
        return value;
    }

    public record SkillDefinition(
        String id,
        String name,
        String scope,
        String executorId,
        boolean requiresConfirmationForWrite,
        String version,
        String source,
        String publicationStatus
    ) {
        public SkillDefinition(String id, String name, String scope, String executorId, boolean requiresConfirmationForWrite) {
            this(id, name, scope, executorId, requiresConfirmationForWrite, "builtin", "server", "approved");
        }
    }
}
