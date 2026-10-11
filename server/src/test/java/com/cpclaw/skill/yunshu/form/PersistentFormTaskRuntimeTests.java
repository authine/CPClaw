package com.cpclaw.skill.yunshu.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cpclaw.skill.yunshu.runtime.YunshuProvider;
import com.cpclaw.skill.yunshu.runtime.FormReference;
import com.cpclaw.skill.yunshu.runtime.ProviderFormSchema;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.cpclaw.yunshu.protocol.YunshuTaskEnvelope;
import com.cpclaw.yunshu.protocol.YunshuTaskRequest;
import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PersistentFormTaskRuntimeTests {
    private final YunshuHostContext owner = new YunshuHostContext("tenant-a", "user-a", "workbuddy", Map.of());

    @Test
    void startInputGetAndCancelUseStableTaskAndCasVersions() {
        PersistentFormTaskRuntime runtime = runtimeWithLeadSchema();
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增私海线索", Map.of(), Map.of(), null, "turn-1"), owner);
        assertEquals(YunshuProtocol.TaskStatus.needs_input, started.task().status());
        assertFalse(started.task().id().isBlank());
        assertEquals("sql_line", started.draft().formRef().get("schemaCode"));
        assertEquals("sql_line_name", started.draft().fields().getFirst().fieldCode());

        YunshuTaskEnvelope filled = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "",
                Map.of("sql_line_name", "奥哲"), Map.of(), started.task().stateVersion(), "turn-2"), owner);
        assertEquals(started.task().id(), filled.task().id());
        assertEquals(YunshuProtocol.TaskStatus.confirmation_required, filled.task().status());
        assertNotEquals(started.task().stateVersion(), filled.task().stateVersion());

        YunshuTaskEnvelope read = runtime.handle(request(YunshuProtocol.Operation.get, filled.task().id(), "", Map.of(), Map.of(),
                filled.task().stateVersion(), ""), owner);
        assertEquals(filled.draft().draftVersion(), read.draft().draftVersion());

        YunshuTaskEnvelope cancelled = runtime.handle(request(YunshuProtocol.Operation.action, read.task().id(), "", Map.of(),
                Map.of("type", "cancel"), read.task().stateVersion(), "turn-3"), owner);
        assertEquals(YunshuProtocol.TaskStatus.cancelled, cancelled.task().status());
    }

    @Test
    void staleInputReturnsLatestEnvelopeAndConfirmCannotWrite() {
        PersistentFormTaskRuntime runtime = runtimeWithLeadSchema();
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增私海线索", Map.of(), Map.of(), null, "turn-1"), owner);
        YunshuTaskEnvelope filled = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "",
                Map.of("sql_line_name", "奥哲"), Map.of(), started.task().stateVersion(), "turn-2"), owner);
        YunshuTaskEnvelope conflict = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "",
                Map.of("sql_line_name", "旧请求"), Map.of(), started.task().stateVersion(), "turn-3"), owner);
        assertEquals(YunshuProtocol.ErrorCode.STATE_VERSION_CONFLICT, conflict.error().code());
        assertEquals(filled.task().stateVersion(), conflict.task().stateVersion());

        YunshuTaskEnvelope confirmation = runtime.handle(request(YunshuProtocol.Operation.action, filled.task().id(), "", Map.of(),
                Map.of("type", "confirm"), filled.task().stateVersion(), "turn-4"), owner);
        assertEquals(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED, confirmation.error().code());
    }

    @Test
    void scopeIsolationDoesNotRevealTask() {
        PersistentFormTaskRuntime runtime = runtimeWithLeadSchema();
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增私海线索", Map.of(), Map.of(), null, "turn-1"), owner);
        YunshuTaskEnvelope denied = runtime.handle(request(YunshuProtocol.Operation.get, started.task().id(), "", Map.of(), Map.of(), 1L, ""),
                new YunshuHostContext("tenant-a", "other-user", "workbuddy", Map.of()));
        assertEquals(YunshuProtocol.ErrorCode.PERMISSION_DENIED, denied.error().code());
    }

    @Test
    void cancelledTaskRejectsConfirmationBeforeProviderGuard() {
        PersistentFormTaskRuntime runtime = runtimeWithLeadSchema();
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增私海线索", Map.of(), Map.of(), null, "turn-1"), owner);
        YunshuTaskEnvelope cancelled = runtime.handle(request(YunshuProtocol.Operation.action, started.task().id(), "", Map.of(),
                Map.of("type", "cancel"), started.task().stateVersion(), "turn-2"), owner);
        YunshuTaskEnvelope denied = runtime.handle(request(YunshuProtocol.Operation.action, cancelled.task().id(), "", Map.of(),
                Map.of("type", "confirm"), cancelled.task().stateVersion(), "turn-3"), owner);
        assertEquals(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, denied.error().code());
    }

    @Test
    void confirmationPlanHashMismatchIsRejected() {
        YunshuFormSchemaProvider schema = (goal, scope) -> new ProviderFormSchema(new FormReference("sales", "lead", "new"), "mock-v1", List.of(
                new FormFieldSlot("name", "名称", "text", null, "system", null, true, false, "pending", null, List.of())));
        PersistentFormTaskRuntime runtime = new PersistentFormTaskRuntime(new MemoryTasks(), provider(), schema);
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新建", Map.of(), Map.of(), null, "turn-1"), owner);
        YunshuTaskEnvelope ready = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "", Map.of("name", "A"), Map.of(), started.task().stateVersion(), "turn-2"), owner);
        YunshuTaskEnvelope denied = runtime.handle(request(YunshuProtocol.Operation.action, ready.task().id(), "", Map.of(),
                Map.of("type", "confirm", "planHash", "tampered"), ready.task().stateVersion(), "turn-3"), owner);
        assertEquals(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, denied.error().code());
    }

    @Test
    void providerSchemaDrivesQuestionsAndMultiFieldValidationBeforeConfirmationPlan() {
        YunshuFormSchemaProvider schema = (goal, scope) -> new ProviderFormSchema(new FormReference("zlcsstcrm", "sql_line", "sql_line"), "metadata-2026-09-01", List.of(
                new FormFieldSlot("sql_line_name", "线索名称", "text", null, "metadata", null, true, false, "pending", null, List.of()),
                new FormFieldSlot("Label_description", "标签说明", "textarea", null, "metadata", null, true, false, "pending", null, List.of())));
        PersistentFormTaskRuntime runtime = new PersistentFormTaskRuntime(new MemoryTasks(), provider(), schema);
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新建私海线索", Map.of(), Map.of(), null, "turn-1"), owner);
        assertEquals(List.of("sql_line_name"), started.interaction().questions().get(0).slotRefs());
        assertEquals(List.of("Label_description"), started.interaction().questions().get(1).slotRefs());

        YunshuTaskEnvelope invalid = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "",
                Map.of("sql_line_name", "奥哲"), Map.of(), started.task().stateVersion(), "turn-2"), owner);
        assertEquals(YunshuProtocol.TaskStatus.validation_error, invalid.task().status());
        assertEquals("REQUIRED", invalid.validation().issues().get(0).code());

        YunshuTaskEnvelope ready = runtime.handle(request(YunshuProtocol.Operation.action, invalid.task().id(), "", Map.of(),
                Map.of("type", "update_draft", "patch", Map.of("Label_description", "重点客户")), invalid.task().stateVersion(), "turn-3"), owner);
        assertEquals(YunshuProtocol.TaskStatus.confirmation_required, ready.task().status());
        assertEquals(YunshuProtocol.InteractionType.request_confirmation, ready.interaction().type());
        assertFalse(String.valueOf(ready.output().get("planHash")).isBlank());
    }

    @Test
    void unresolvedMetadataNeverCreatesGenericFormFields() {
        YunshuFormSchemaProvider unresolved = (goal, scope) -> new ProviderFormSchema(new FormReference("", "", ""), "metadata-unresolved", List.of());
        PersistentFormTaskRuntime runtime = new PersistentFormTaskRuntime(new MemoryTasks(), provider(), unresolved);

        YunshuTaskEnvelope result = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增一个未知业务对象", Map.of(), Map.of(), null, "turn-1"), owner);

        assertEquals(YunshuProtocol.ErrorCode.AMBIGUOUS_TARGET, result.error().code());
        assertEquals(0, result.draft().fields().size());
    }

    @Test
    void localMetadataProviderSelectsExecutableEntityAndRealFieldCodes() {
        MetadataSearchService search = mock(MetadataSearchService.class);
        CloudPivotEntityRepository entities = mock(CloudPivotEntityRepository.class);
        CloudPivotAppRepository apps = mock(CloudPivotAppRepository.class);
        CloudPivotDataItemRepository dataItems = mock(CloudPivotDataItemRepository.class);
        when(search.searchLocalMetadata("私海线索")).thenReturn(List.of(
                new MetadataSearchResult("entity", "menu-id", "私海线索", "menu_private_clues", "市场管理 / 私海线索", "low", "test"),
                new MetadataSearchResult("entity", "lead-id", "私海线索", "sql_line", "zlcsstcrm / 私海线索", "low", "test")));
        CloudPivotEntity entity = new CloudPivotEntity();
        entity.setId("lead-id"); entity.setAppId("crm-app"); entity.setEntityCode("sql_line"); entity.setName("私海线索");
        CloudPivotApp app = new CloudPivotApp(); app.setId("crm-app"); app.setAppCode("zlcsstcrm"); app.setName("CRM");
        CloudPivotDataItem name = new CloudPivotDataItem();
        name.setId("field-id"); name.setEntityId("lead-id"); name.setDataItemCode("sql_line_name"); name.setName("线索名称"); name.setDataType("text"); name.setFieldCategory("BUSINESS");
        when(entities.findByEntityCodeIgnoreCase("menu_private_clues")).thenReturn(List.of());
        when(entities.findByEntityCodeIgnoreCase("sql_line")).thenReturn(List.of(entity));
        when(apps.findById("crm-app")).thenReturn(java.util.Optional.of(app));
        when(dataItems.findByEntityId("lead-id")).thenReturn(List.of(name));

        ProviderFormSchema schema = new LocalMetadataFormSchemaProvider(search, entities, apps, dataItems).resolve("创建一个私海线索", new FormTaskScope("t", "u", "h"));

        assertEquals("zlcsstcrm", schema.formRef().applicationCode());
        assertEquals("sql_line", schema.formRef().schemaCode());
        assertEquals(List.of("sql_line_name"), schema.fields().stream().map(FormFieldSlot::fieldCode).toList());
        assertTrue(schema.fields().stream().allMatch(field -> !"form_details".equals(field.fieldCode())));
    }

    @Test
    void naturalLanguageNamePhrasePopulatesTheSingleNameLikeField() {
        MetadataFieldValueExtractor extractor = new MetadataFieldValueExtractor();
        List<FormFieldSlot> slots = List.of(
                new FormFieldSlot("sql_line_name", "线索名称", "text", null, "metadata", null, false, false, "pending", null, List.of()),
                new FormFieldSlot("company_size", "企业规模", "text", null, "metadata", null, false, false, "pending", null, List.of()));

        Map<String, Object> values = extractor.extract("我最近跟进了一个客户，叫奥哲", slots);

        assertEquals("奥哲", values.get("sql_line_name"));
    }

    @Test
    void naturalLanguageCustomerNameSelectsCompanyNameWhenMultipleNameFieldsExist() {
        MetadataFieldValueExtractor extractor = new MetadataFieldValueExtractor();
        List<FormFieldSlot> slots = List.of(
                new FormFieldSlot("sql_line_name", "线索名称", "text", null, "metadata", null, false, false, "pending", null, List.of()),
                new FormFieldSlot("company_name", "公司名称", "text", null, "metadata:primary-name", null, false, false, "pending", null, List.of()));

        Map<String, Object> values = extractor.extract("我最近跟进了一个客户，叫奥哲", slots);

        assertEquals("奥哲", values.get("company_name"));
    }

    @Test
    void verifiedProviderModeReturnsIssuedConfirmationTicketInEnvelope() {
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        when(tickets.issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(true)))
                .thenReturn(new ConfirmationTicketService.TicketIssueResult("ticket-1", Instant.now().plusSeconds(300), null, ""));
        PersistentFormTaskRuntime runtime = new PersistentFormTaskRuntime(new MemoryTasks(), provider(), realLeadSchema(), tickets, true, null, null);
        YunshuTaskEnvelope started = runtime.handle(request(YunshuProtocol.Operation.start, "", "新增私海线索", Map.of(), Map.of(), null, "ticket-start"), owner);
        YunshuTaskEnvelope ready = runtime.handle(request(YunshuProtocol.Operation.input, started.task().id(), "",
                Map.of("sql_line_name", "测试客户"), Map.of(), started.task().stateVersion(), "ticket-input"), owner);
        assertEquals(YunshuProtocol.TaskStatus.confirmation_required, ready.task().status());
        assertEquals("ticket-1", ready.output().get("confirmationTicket"));
        assertEquals("ticket-1", ready.hostAction().payload().get("confirmationTicket"));
    }

    private PersistentFormTaskRuntime runtimeWithLeadSchema() {
        return new PersistentFormTaskRuntime(new MemoryTasks(), provider(), realLeadSchema());
    }

    private YunshuFormSchemaProvider realLeadSchema() {
        return (goal, scope) -> new ProviderFormSchema(new FormReference("zlcsstcrm", "sql_line", "sql_line"), "metadata-2026-09-01", List.of(
                new FormFieldSlot("sql_line_name", "线索名称", "text", null, "metadata", null, true, false, "pending", null, List.of())));
    }

    private YunshuTaskRequest request(YunshuProtocol.Operation op, String taskId, String goal, Map<String, Object> input,
            Map<String, Object> action, Long version, String requestId) {
        return new YunshuTaskRequest(YunshuProtocol.VERSION, op, taskId, goal, input, action, requestId, version, "");
    }

    private YunshuProvider provider() { return new YunshuProvider() {
        @Override public com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot metadata(com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection c) { return null; }
        @Override public com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult query(com.cpclaw.mcp.McpInstallationService.BoundCloudPivotConnection c, String s, int p, boolean d, int m) { return null; }
    }; }

    /** In-memory repository replacement: verifies runtime semantics without an external CloudPivot or database. */
    private static final class MemoryTasks extends FormTaskPersistenceService {
        private final Map<String, FormTaskSnapshot> tasks = new HashMap<>();
        MemoryTasks() { super(null, null, new ObjectMapper()); }
        @Override public FormTaskSnapshot create(FormTaskScope scope, Map<String, Object> ref, String metadata, List<FormFieldSlot> slots, String key) {
            String id = "task-" + (tasks.size() + 1); FormTaskSnapshot value = new FormTaskSnapshot(id, scope, "needs_input", ref, metadata, 1, 1,
                    slots, new FormDraftValidator().validate(slots)); tasks.put(id, value); return value;
        }
        @Override public FormTaskSnapshot get(String id, FormTaskScope scope) { return scoped(id, scope); }
        @Override public FormTaskSnapshot findByIdempotencyKey(FormTaskScope scope, String key) { return null; }
        @Override public FormTaskSnapshot updateDraft(String id, FormTaskScope scope, long state, long draft, List<FormFieldSlot> slots, String source) {
            FormTaskSnapshot current = scoped(id, scope); if (current.stateVersion() != state || current.draftVersion() != draft) throw new FormTaskVersionConflictException("stale");
            List<FormValidationIssue> issues = new FormDraftValidator().validate(slots); FormTaskSnapshot updated = new FormTaskSnapshot(id, scope,
                    issues.isEmpty() ? "ready_to_submit" : "validation_error", current.formRef(), current.metadataVersion(), state + 1, draft + 1, slots, issues);
            tasks.put(id, updated); return updated;
        }
        @Override public FormTaskSnapshot requireConfirmation(String id, FormTaskScope scope, long state, String planHash) {
            FormTaskSnapshot current = scoped(id, scope); if (current.stateVersion() != state || !"ready_to_submit".equals(current.status())) throw new FormTaskVersionConflictException("stale");
            FormTaskSnapshot updated = new FormTaskSnapshot(id, scope, "confirmation_required", current.formRef(), current.metadataVersion(), state + 1,
                    current.draftVersion(), current.slots(), current.validationIssues()); tasks.put(id, updated); return updated;
        }
        @Override public FormTaskSnapshot cancel(String id, FormTaskScope scope, long state) {
            FormTaskSnapshot current = scoped(id, scope); if (current.stateVersion() != state) throw new FormTaskVersionConflictException("stale");
            FormTaskSnapshot updated = new FormTaskSnapshot(id, scope, "cancelled", current.formRef(), current.metadataVersion(), state + 1,
                    current.draftVersion(), current.slots(), current.validationIssues()); tasks.put(id, updated); return updated;
        }
        private FormTaskSnapshot scoped(String id, FormTaskScope scope) { FormTaskSnapshot value = tasks.get(id); if (value == null || !value.scope().equals(scope)) throw new FormTaskNotFoundException("not found"); return value; }
    }
}

