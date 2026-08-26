ALTER TABLE cloudpivot_entities ADD COLUMN description TEXT NULL;
ALTER TABLE cloudpivot_data_items ADD COLUMN field_category VARCHAR(32) NOT NULL DEFAULT 'BUSINESS';

UPDATE cloudpivot_data_items
SET field_category = CASE
    WHEN LOWER(REPLACE(REPLACE(data_item_code, '_', ''), '-', '')) IN
      ('ownerdeptid', 'modifiedtime', 'name', 'createddeptid', 'creater', 'sequenceno', 'createdtime', 'modifier', 'owner', 'workflowinstanceid', 'processinstanceid')
    THEN 'SYSTEM'
    ELSE 'BUSINESS'
END
WHERE field_category IS NULL OR field_category = '';
