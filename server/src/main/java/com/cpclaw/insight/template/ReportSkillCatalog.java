package com.cpclaw.insight.template;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Resolves a report strategy from persisted, declarative templates. Templates
 * describe reporting intent only; they never contain credentials or endpoints.
 */
@Service
public class ReportSkillCatalog {
    private final ReportSkillTemplateRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ReportSkillCatalog(ReportSkillTemplateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Constructor used by focused unit tests that exercise the legacy service directly. */
    public ReportSkillCatalog() {
        this.repository = null;
        this.objectMapper = new ObjectMapper();
    }

    public ReportSkillDefinition resolve(String objectName, String question) {
        String object = normalize(objectName);
        String query = normalize(question);
        List<ReportSkillTemplate> templates = repository == null
            ? builtInFallbackTemplates()
            : repository.findByEnabledTrueOrderByPriorityDesc();
        ReportSkillTemplate best = null;
        int bestScore = 0;
        for (ReportSkillTemplate template : templates) {
            int score = score(template, object, query);
            if (score > bestScore) {
                best = template;
                bestScore = score;
            }
        }
        if (best == null) {
            return new ReportSkillDefinition(
                "generic-business-analysis", "通用业务分析", "根据用户要求组织可验证的业务分析报告", 1,
                "{\"sections\":\"dynamic\",\"charts\":\"dynamic\"}", "未匹配专项报告 Skill，使用通用分析"
            );
        }
        return new ReportSkillDefinition(best.getSkillCode(), best.getName(), best.getDescription(), best.getVersion(), best.getConfigJson(), "已匹配报告 Skill：" + best.getName());
    }

    private int score(ReportSkillTemplate template, String object, String query) {
        List<String> aliases = jsonList(template.getObjectAliasesJson());
        List<String> hints = jsonList(template.getTriggerHintsJson());
        int score = Math.max(0, template.getPriority());
        if (aliases.isEmpty()) score += 10;
        for (String alias : aliases) if (!alias.isBlank() && object.contains(normalize(alias))) score += 100;
        for (String hint : hints) if (!hint.isBlank() && query.contains(normalize(hint))) score += 20;
        if (!aliases.isEmpty() && aliases.stream().noneMatch(alias -> object.contains(normalize(alias)))) return 0;
        return score;
    }

    private List<String> jsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<ReportSkillTemplate> builtInFallbackTemplates() {
        List<ReportSkillTemplate> templates = new ArrayList<>();
        templates.add(template("yunshu-intelligent-inquiry", "云枢智能问数", "根据用户目标动态规划数据、图形和业务总结", "[]", "[\"分析\",\"报告\",\"洞察\",\"概览\",\"诊断\",\"经营\"]", 100));
        templates.add(template("generic-business-analysis", "通用业务分析", "在未匹配专用分析能力时组织可验证的业务分析报告", "[]", "[\"分析\",\"报告\",\"洞察\",\"概览\",\"诊断\"]", 1));
        return templates;
    }

    private ReportSkillTemplate template(String code, String name, String description, String aliases, String hints, int priority) {
        ReportSkillTemplate value = new ReportSkillTemplate();
        value.setSkillCode(code);
        value.setName(name);
        value.setDescription(description);
        value.setObjectAliasesJson(aliases);
        value.setTriggerHintsJson(hints);
        value.setConfigJson("{\"sections\":\"dynamic\",\"charts\":\"dynamic\"}");
        value.setPriority(priority);
        value.setVersion(1);
        value.setEnabled(true);
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s`'\"，。！？、：；（）()_-]+", "");
    }
}
