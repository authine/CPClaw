CREATE TABLE IF NOT EXISTS report_skill_templates (
    id VARCHAR(36) PRIMARY KEY,
    skill_code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    object_aliases_json TEXT,
    trigger_hints_json TEXT,
    config_json TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO report_skill_templates (id, skill_code, name, description, object_aliases_json, trigger_hints_json, config_json, enabled, priority, version)
SELECT 'report-skill-yunshu-inquiry-v1', 'yunshu-intelligent-inquiry', '云枢智能问数',
       '根据用户目标动态规划数据、图形和业务总结',
       '[]', '["分析","报告","洞察","概览","诊断","经营"]',
       '{"sections":"dynamic","charts":"dynamic","planning":"model_guided"}', TRUE, 100, 1
WHERE NOT EXISTS (SELECT 1 FROM report_skill_templates WHERE skill_code = 'yunshu-intelligent-inquiry');

INSERT INTO report_skill_templates (id, skill_code, name, description, object_aliases_json, trigger_hints_json, config_json, enabled, priority, version)
SELECT 'report-skill-generic-v1', 'generic-business-analysis', '通用业务分析',
       '根据用户要求组织可验证的业务分析报告',
       '[]', '["分析","报告","洞察","概览","诊断"]',
       '{"sections":"dynamic","charts":"dynamic"}', TRUE, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM report_skill_templates WHERE skill_code = 'generic-business-analysis');
