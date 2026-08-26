ALTER TABLE report_skill_templates
    ADD COLUMN IF NOT EXISTS manifest_json LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS template_kind VARCHAR(64) NOT NULL DEFAULT 'generic',
    ADD COLUMN IF NOT EXISTS skill_id VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(32) NOT NULL DEFAULT 'approved',
    ADD COLUMN IF NOT EXISTS signature VARCHAR(512) NULL;

UPDATE report_skill_templates
SET template_kind = CASE
        WHEN skill_code = 'generic-business-analysis' THEN 'generic'
        ELSE 'legacy-compatible'
    END,
    publication_status = COALESCE(publication_status, 'approved')
WHERE template_kind IS NULL OR template_kind = '';
