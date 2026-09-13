-- MySQL versions used by CPClaw do not support ADD COLUMN IF NOT EXISTS.
-- This migration is applied once by Flyway, so use portable ADD COLUMN syntax.
ALTER TABLE report_skill_templates
    ADD COLUMN manifest_json LONGTEXT NULL,
    ADD COLUMN template_kind VARCHAR(64) NOT NULL DEFAULT 'generic',
    ADD COLUMN skill_id VARCHAR(128) NULL,
    ADD COLUMN publication_status VARCHAR(32) NOT NULL DEFAULT 'approved',
    ADD COLUMN signature VARCHAR(512) NULL;

UPDATE report_skill_templates
SET template_kind = CASE
        WHEN skill_code = 'generic-business-analysis' THEN 'generic'
        ELSE 'legacy-compatible'
    END,
    publication_status = COALESCE(publication_status, 'approved')
WHERE template_kind IS NULL OR template_kind = '';
