CREATE TABLE IF NOT EXISTS cloudpivot_data_items (
    id VARCHAR(36) PRIMARY KEY,
    entity_id VARCHAR(36) NOT NULL,
    data_item_code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    data_type VARCHAR(64),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    is_reference BOOLEAN NOT NULL DEFAULT FALSE,
    reference_entity_id VARCHAR(36),
    description TEXT,
    raw_json TEXT,
    synced_at TIMESTAMP NULL,
    INDEX idx_data_items_entity (entity_id, data_item_code)
);

INSERT INTO cloudpivot_data_items (
    id,
    entity_id,
    data_item_code,
    name,
    data_type,
    required,
    is_reference,
    reference_entity_id,
    description,
    raw_json,
    synced_at
)
SELECT
    legacy.id,
    legacy.entity_id,
    legacy.field_code,
    legacy.name,
    legacy.data_type,
    legacy.required,
    legacy.is_reference,
    legacy.reference_entity_id,
    legacy.description,
    legacy.raw_json,
    legacy.synced_at
FROM cloudpivot_entity_fields legacy
WHERE NOT EXISTS (
    SELECT 1 FROM cloudpivot_data_items item WHERE item.id = legacy.id
);

ALTER TABLE cloudpivot_entity_relations ADD COLUMN source_data_item_id VARCHAR(36) NULL;

UPDATE cloudpivot_entity_relations
SET source_data_item_id = source_field_id
WHERE source_data_item_id IS NULL AND source_field_id IS NOT NULL;
