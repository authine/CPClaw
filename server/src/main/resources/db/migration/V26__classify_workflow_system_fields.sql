UPDATE cloudpivot_data_items
SET field_category = 'SYSTEM'
WHERE LOWER(REPLACE(REPLACE(data_item_code, '_', ''), '-', '')) IN (
  'workflowinstanceid', 'processinstanceid', 'sequencestatus', 'workflowstatus',
  'processstatus', 'systemstatus', 'datastatus', 'recordstatus', 'createdby',
  'modifiedby', 'deleted', 'tenantid'
);
