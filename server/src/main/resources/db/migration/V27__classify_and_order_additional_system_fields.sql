UPDATE cloudpivot_data_items
SET field_category = 'SYSTEM'
WHERE LOWER(REPLACE(REPLACE(REPLACE(data_item_code, '_', ''), '-', ''), ' ', '')) IN ('id', 'ownerdeptquerycode');

UPDATE cloudpivot_data_items
SET description = CASE
    WHEN LOWER(REPLACE(REPLACE(REPLACE(data_item_code, '_', ''), '-', ''), ' ', '')) = 'id'
        THEN '云枢系统字段：业务对象ID，用于唯一标识一条业务对象记录。'
    ELSE '云枢系统字段：部门查询编码，用于云枢按归属部门进行查询和权限过滤。'
END
WHERE field_category = 'SYSTEM'
  AND LOWER(REPLACE(REPLACE(REPLACE(data_item_code, '_', ''), '-', ''), ' ', '')) IN ('id', 'ownerdeptquerycode');
