package com.cpclaw.cloudpivot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CloudPivotMetadataSemanticsTests {
    @Test
    void classifiesProviderSystemFieldsAndDefaultsOthersToBusiness() {
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("ownerDeptId", "拥有者部门"));
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("modifiedTime", "修改时间"));
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("workflowInstanceId", "流程实例ID"));
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("sequenceNo", "单据号"));
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("ownerDeptQueryCode", "部门查询编码"));
        assertEquals(CloudPivotMetadataSemantics.SYSTEM_FIELD, CloudPivotMetadataSemantics.fieldCategory("id", "业务对象ID"));
        assertEquals(CloudPivotMetadataSemantics.BUSINESS_FIELD, CloudPivotMetadataSemantics.fieldCategory("customerType", "客户类型"));
    }

    @Test
    void ordersBusinessObjectIdBeforeOtherSystemAndBusinessFields() {
        assertEquals(0, CloudPivotMetadataSemantics.fieldOrder("id"));
        assertEquals(1, CloudPivotMetadataSemantics.fieldOrder("ownerDeptQueryCode"));
        assertEquals(2, CloudPivotMetadataSemantics.fieldOrder("customerType"));
    }

    @Test
    void generatesChineseDescriptionsWhenProviderMetadataIsEmpty() {
        String app = CloudPivotMetadataSemantics.appDescription("crm", "客户管理", null);
        String entity = CloudPivotMetadataSemantics.entityDescription("customer", "客户", null);
        String field = CloudPivotMetadataSemantics.fieldDescription("customerType", "客户类型", "TEXT", "", CloudPivotMetadataSemantics.BUSINESS_FIELD);
        org.junit.jupiter.api.Assertions.assertTrue(app.contains("客户管理"));
        org.junit.jupiter.api.Assertions.assertTrue(entity.contains("客户"));
        org.junit.jupiter.api.Assertions.assertTrue(field.contains("业务字段"));
        org.junit.jupiter.api.Assertions.assertFalse(CloudPivotMetadataSemantics.appDescription("crm", "客户管理", "{\"name\":\"客户管理\"}").startsWith("{"));
        assertEquals("应用说明", CloudPivotMetadataSemantics.appDescription("crm", "客户管理", "{\"appDescription\":\"应用说明\"}"));
    }

    @Test
    void exposesProviderTypeNamesInsteadOfNumericCodes() {
        assertEquals("文本（Text）", CloudPivotMetadataSemantics.fieldTypeDisplayName("0"));
        assertEquals("日期时间（DateTime）", CloudPivotMetadataSemantics.fieldTypeDisplayName("3"));
        assertEquals("人员（User）", CloudPivotMetadataSemantics.fieldTypeDisplayName("50"));
        assertEquals("部门（Department）", CloudPivotMetadataSemantics.fieldTypeDisplayName("60"));
        assertEquals("MultiSelect", CloudPivotMetadataSemantics.fieldTypeDisplayName("MultiSelect"));
    }
}
