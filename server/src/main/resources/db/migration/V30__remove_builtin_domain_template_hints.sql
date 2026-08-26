UPDATE report_skill_templates
SET object_aliases_json = '[]',
    trigger_hints_json = '[]',
    template_kind = CASE WHEN skill_code = 'generic-business-analysis' THEN 'generic' ELSE 'legacy-compatible' END
WHERE skill_code IN ('yunshu-intelligent-inquiry', 'generic-business-analysis');
