package com.cpclaw.skill.yunshu.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormDraftServiceTests {
    private final FormDraftService service = new FormDraftService();

    @Test
    void reportsRequiredAndEnumErrorsAtFieldLevel() {
        var result = service.patchAndValidate(List.of(
                new FormFieldSlot("name", "名称", "text", "", "ai", .7, true, false, "pending", null, List.of()),
                new FormFieldSlot("level", "级别", "enum", "invalid", "user", 1d, false, false, "pending", null,
                        List.of("A", "B"))), Map.of());

        assertEquals(2, result.issues().size());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.fieldCode().equals("name") && issue.code().equals("REQUIRED")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.fieldCode().equals("level") && issue.code().equals("ENUM")));
        assertEquals("invalid", result.slots().get(0).validationState());
    }

    @Test
    void patchOnlyChangesValuesAndMarksUserSource() {
        var result = service.patchAndValidate(List.of(
                new FormFieldSlot("name", "名称", "text", null, "ai", .5, true, false, "pending", null, List.of())),
                Map.of("name", "测试"));

        assertTrue(result.issues().isEmpty());
        assertEquals("测试", result.slots().get(0).value());
        assertEquals("user", result.slots().get(0).source());
        assertEquals("valid", result.slots().get(0).validationState());
    }
}
