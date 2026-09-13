package com.cpclaw.skill.yunshu.form;

import com.cpclaw.skill.yunshu.runtime.YunshuProvider;
import com.cpclaw.skill.yunshu.runtime.FormReference;
import com.cpclaw.skill.yunshu.runtime.ProviderFormSchema;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.cpclaw.yunshu.protocol.YunshuSkillGateway;
import com.cpclaw.yunshu.protocol.YunshuTaskEnvelope;
import com.cpclaw.yunshu.protocol.YunshuTaskRequest;
import com.cpclaw.yunshu.confirmation.ConfirmationPlan;
import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/**
 * Persistent, host-neutral FormTask runtime. Until a verified Provider form
 * contract exists it gathers an explicit form description and never submits a
 * CloudPivot write.
 */
@Service
public class PersistentFormTaskRuntime implements YunshuSkillGateway {
    private final FormTaskPersistenceService tasks;
    @SuppressWarnings("unused")
    private final YunshuProvider provider;
    private final YunshuFormSchemaProvider schemaProvider;
    private final ConfirmationTicketService confirmationTickets;
    private final boolean providerWriteVerified;

    public PersistentFormTaskRuntime(FormTaskPersistenceService tasks, YunshuProvider provider) {
        this(tasks, provider, (goal, scope) -> new ProviderFormSchema(new FormReference("", "", ""), "metadata-unresolved", List.of()), null, false);
    }

    public PersistentFormTaskRuntime(FormTaskPersistenceService tasks, YunshuProvider provider,
            YunshuFormSchemaProvider schemaProvider) {
        this(tasks, provider, schemaProvider, null, false);
    }

    @Autowired
    public PersistentFormTaskRuntime(FormTaskPersistenceService tasks, YunshuProvider provider,
            YunshuFormSchemaProvider schemaProvider, ConfirmationTicketService confirmationTickets,
            @Value("${cpclaw.yunshu-skill.provider-write-verified:false}") boolean providerWriteVerified) {
        this.tasks = tasks;
        this.provider = provider;
        this.schemaProvider = schemaProvider;
        this.confirmationTickets = confirmationTickets;
        this.providerWriteVerified = providerWriteVerified;
    }

    @Override
    public YunshuTaskEnvelope handle(YunshuTaskRequest request, YunshuHostContext context) {
        String requestError = request.validationError();
        if (!requestError.isBlank()) return failure(request.taskId(), YunshuProtocol.ErrorCode.VALIDATION_ERROR, requestError);
        FormTaskScope scope = new FormTaskScope(context.tenantId(), context.subjectId(), context.hostId());
        if (scope.tenantId().isBlank() || scope.subjectId().isBlank() || scope.hostId().isBlank()) {
            return failure(request.taskId(), YunshuProtocol.ErrorCode.PERMISSION_DENIED, "缺少可信的租户、主体或宿主上下文。");
        }
        try {
            return switch (request.operation()) {
                case start -> start(request, scope);
                case input -> input(request, scope);
                case action -> action(request, scope, context);
                case get -> envelope(tasks.get(request.taskId(), scope), null);
                case events -> failure(request.taskId(), YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED,
                        "持久化任务事件流尚未开放；请使用 get 恢复任务状态。");
            };
        } catch (FormTaskVersionConflictException ex) {
            return versionConflict(request.taskId(), scope, ex.getMessage());
        } catch (FormTaskNotFoundException ex) {
            return failure(request.taskId(), YunshuProtocol.ErrorCode.PERMISSION_DENIED, "任务不存在或无权访问。");
        } catch (AmbiguousFormTargetException ex) {
            YunshuTaskEnvelope failed = failure(request.taskId(), YunshuProtocol.ErrorCode.AMBIGUOUS_TARGET, ex.getMessage());
            return new YunshuTaskEnvelope(failed.protocolVersion(), failed.task(),
                    new YunshuTaskEnvelope.Interaction(YunshuProtocol.InteractionType.ask_user, List.of(
                            new YunshuTaskEnvelope.Question("target", List.of("target"), "select", ex.getMessage(), true,
                                    ex.candidates(), Map.of()))), failed.draft(), failed.validation(), failed.next(), failed.completion(),
                    Map.of("targetCandidates", ex.candidates()), failed.evidence(),
                    new YunshuTaskEnvelope.HostAction(YunshuProtocol.HostActionType.ask_user, true, Map.of()), failed.auditRef(), failed.error());
        }
    }

    private YunshuTaskEnvelope start(YunshuTaskRequest request, FormTaskScope scope) {
        FormTaskSnapshot previous = tasks.findByIdempotencyKey(scope, request.clientRequestId());
        if (previous != null) return envelope(previous, null);
        ProviderFormSchema schema = schemaProvider.resolve(request.goal(), scope);
        if (schema.fields().isEmpty() || schema.formRef() == null || schema.formRef().schemaCode().isBlank()) {
            return failure(request.taskId(), YunshuProtocol.ErrorCode.AMBIGUOUS_TARGET,
                    "暂未从已同步的云枢元数据唯一定位可填写的业务表单，请补充应用或业务对象名称。");
        }
        Map<String, Object> values = new LinkedHashMap<>(new MetadataFieldValueExtractor().extract(request.goal(), schema.fields()));
        values.putAll(normalizeInput(request.input()));
        List<FormFieldSlot> slots = new FormDraftService().patchAndValidate(schema.fields(), values).slots();
        FormTaskSnapshot created = tasks.create(scope, formRef(schema.formRef(), request.goal()), schema.metadataVersion(), slots,
                request.clientRequestId());
        if (created.validationIssues().isEmpty()) {
            created = tasks.updateDraft(created.taskId(), scope, created.stateVersion(), created.draftVersion(), slots, "user");
            created = tasks.requireConfirmation(created.taskId(), scope, created.stateVersion(), planHash(created));
        }
        return envelope(created, null);
    }

    private YunshuTaskEnvelope input(YunshuTaskRequest request, FormTaskScope scope) {
        FormTaskSnapshot current = tasks.get(request.taskId(), scope);
        Map<String, Object> values = new LinkedHashMap<>();
        Object text = request.input().get("text");
        if (text instanceof String message) {
            values.putAll(new MetadataFieldValueExtractor().extract(message, current.slots()));
            // Unlabelled replies are safe only when exactly one blocking slot remains.
            List<FormFieldSlot> blocking = blockingSlots(current.slots());
            if (values.isEmpty() && blocking.size() == 1 && !message.isBlank()
                    && !message.matches("(?s).*[：:=，,；;\\r\\n].*")
                    && !message.matches(".*(?:取消|确认|提交|不要|不用|为什么|怎么|什么|[？?]).*")) {
                values.put(blocking.getFirst().fieldCode(), message.trim());
            }
        }
        values.putAll(normalizeInput(request.input()));
        return patch(request, scope, values, "user");
    }

    private YunshuTaskEnvelope action(YunshuTaskRequest request, FormTaskScope scope, YunshuHostContext context) {
        String type = String.valueOf(request.action().getOrDefault("type", ""));
        if ("update_draft".equals(type)) {
            Object patch = request.action().get("patch");
            return patch(request, scope, asPatch(patch), "user");
        }
        if ("cancel".equals(type)) {
            FormTaskSnapshot snapshot = tasks.get(request.taskId(), scope);
            if (snapshot.stateVersion() != request.expectedStateVersion()) {
                throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
            }
            // Cancellation is recorded as a revision so resumptions are never interpreted as a write attempt.
            return envelope(tasks.cancel(snapshot.taskId(), scope, snapshot.stateVersion()), null);
        }
        if ("confirm".equals(type)) {
            FormTaskSnapshot current = tasks.get(request.taskId(), scope);
            if (current.status().equals("cancelled")) {
                return failure(request.taskId(), YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "任务已取消，确认票不可使用。");
            }
            String expectedPlanHash = planHash(current);
            Object suppliedPlanHash = request.action().get("planHash");
            if (suppliedPlanHash != null && !expectedPlanHash.equals(String.valueOf(suppliedPlanHash))) {
                return failure(request.taskId(), YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认计划已变化，确认票失效。");
            }
            if (!"confirmation_required".equals(current.status())) {
                return failure(request.taskId(), YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "任务当前不处于待确认状态。");
            }
            if (confirmationTickets == null) {
                return failure(request.taskId(), YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED, "确认票服务未配置，不能执行云枢写入。");
            }
            String token = String.valueOf(request.action().getOrDefault("confirmationTicket", "")).trim();
            if (token.isBlank()) {
                return failure(request.taskId(), YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "缺少确认票据。");
            }
            ConfirmationPlan plan = new ConfirmationPlan(current.taskId(), scope.tenantId(), scope.subjectId(), scope.hostId(),
                    "yunshu-form-runtime/1.0", String.valueOf(current.formRef()), current.metadataVersion(), current.stateVersion(),
                    current.draftVersion(), expectedPlanHash, current.taskId() + ":create:" + current.draftVersion(), Instant.now().plusSeconds(1800));
            ConfirmationTicketService.TicketAuthorization authorization = confirmationTickets.authorizeExecution(
                    token, new YunshuHostContext(scope.tenantId(), scope.subjectId(), scope.hostId(), context.capabilities()), plan,
                    providerWriteVerified);
            if (!authorization.granted()) {
                return failure(request.taskId(), authorization.errorCode(), authorization.message());
            }
            return failure(request.taskId(), YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED,
                    "当前暂时无法完成云枢创建，业务信息已保留。请稍后重试，或联系管理员核对云枢连接能力。");
        }
        return failure(request.taskId(), YunshuProtocol.ErrorCode.VALIDATION_ERROR, "不支持的任务动作。");
    }

    private YunshuTaskEnvelope patch(YunshuTaskRequest request, FormTaskScope scope, Map<String, Object> patch, String source) {
        FormTaskSnapshot current = tasks.get(request.taskId(), scope);
        if (current.stateVersion() != request.expectedStateVersion()) {
            throw new FormTaskVersionConflictException("Form task state has changed; reload the latest envelope");
        }
        if (!List.of("needs_input", "validation_error", "draft_ready", "ready_to_submit", "confirmation_required").contains(current.status())) {
            return envelope(current, new YunshuTaskEnvelope.Error(YunshuProtocol.ErrorCode.VALIDATION_ERROR,
                    "当前任务状态不允许修改草稿。", false));
        }
        FormDraftService.DraftValidationResult result = new FormDraftService().patchAndValidate(current.slots(), patch);
        FormTaskSnapshot updated = tasks.updateDraft(current.taskId(), scope, current.stateVersion(), current.draftVersion(), result.slots(), source);
        if ("ready_to_submit".equals(updated.status())) {
            updated = tasks.requireConfirmation(updated.taskId(), scope, updated.stateVersion(), planHash(updated));
        }
        return envelope(updated, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asPatch(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> patch = new LinkedHashMap<>();
        raw.forEach((key, item) -> patch.put(String.valueOf(key), item));
        return patch;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Object answers = input.get("answers");
        if (!(answers instanceof List<?> list)) return input;
        Map<String, Object> patch = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> answer)) continue;
            Object questionId = answer.get("questionId");
            Object value = answer.get("value");
            if (questionId == null) continue;
            String id = String.valueOf(questionId);
            if (id.startsWith("field-")) id = id.substring("field-".length());
            patch.put(id, value);
        }
        return patch.isEmpty() ? input : patch;
    }

    private YunshuTaskEnvelope versionConflict(String taskId, FormTaskScope scope, String message) {
        try { return envelope(tasks.get(taskId, scope), new YunshuTaskEnvelope.Error(YunshuProtocol.ErrorCode.STATE_VERSION_CONFLICT, message, false)); }
        catch (FormTaskNotFoundException ignored) { return failure(taskId, YunshuProtocol.ErrorCode.STATE_VERSION_CONFLICT, message); }
    }

    private YunshuTaskEnvelope envelope(FormTaskSnapshot snapshot, YunshuTaskEnvelope.Error error) {
        YunshuProtocol.TaskStatus status = parseStatus(snapshot.status());
        String completion = status == YunshuProtocol.TaskStatus.confirmation_required ? "ready"
                : status == YunshuProtocol.TaskStatus.cancelled ? "cancelled" : "incomplete";
        return envelopeWithStatus(snapshot, status, completion, error);
    }

    private YunshuTaskEnvelope envelopeWithStatus(FormTaskSnapshot snapshot, YunshuProtocol.TaskStatus status, String completionState,
            YunshuTaskEnvelope.Error error) {
        List<YunshuTaskEnvelope.Field> fields = snapshot.slots().stream().map(slot -> new YunshuTaskEnvelope.Field(
                slot.fieldCode(), slot.displayName(), slot.fieldType(), slot.value(), slot.source(), slot.confidence(),
                slot.required(), slot.confirmed(), slot.validationState(), slot.errorMessage())).toList();
        List<YunshuTaskEnvelope.Issue> issues = snapshot.validationIssues().stream()
                .map(issue -> new YunshuTaskEnvelope.Issue(issue.code(), issue.message(), issue.fieldCode(), true)).toList();
        List<String> missing = snapshot.slots().stream().filter(slot -> slot.required() && (slot.value() == null || String.valueOf(slot.value()).isBlank()))
                .map(FormFieldSlot::fieldCode).toList();
        boolean needsInput = status == YunshuProtocol.TaskStatus.needs_input || status == YunshuProtocol.TaskStatus.validation_error;
        boolean confirmation = status == YunshuProtocol.TaskStatus.confirmation_required;
        List<YunshuTaskEnvelope.Question> questions = needsInput ? questions(snapshot.slots()) : List.of();
        YunshuProtocol.InteractionType interaction = confirmation ? YunshuProtocol.InteractionType.request_confirmation
                : needsInput ? YunshuProtocol.InteractionType.ask_user : YunshuProtocol.InteractionType.edit_draft;
        YunshuProtocol.HostActionType hostAction = confirmation ? YunshuProtocol.HostActionType.request_confirmation
                : needsInput ? YunshuProtocol.HostActionType.ask_user : YunshuProtocol.HostActionType.edit_draft;
        return new YunshuTaskEnvelope(YunshuProtocol.VERSION,
                new YunshuTaskEnvelope.Task(snapshot.taskId(), YunshuProtocol.TaskKind.form, status, snapshot.stateVersion(), false, Instant.now()),
                new YunshuTaskEnvelope.Interaction(interaction, questions),
                new YunshuTaskEnvelope.Draft(snapshot.formRef(), snapshot.metadataVersion(), snapshot.draftVersion(), fields),
                new YunshuTaskEnvelope.Validation(issues),
                new YunshuTaskEnvelope.Next(Map.of("fieldCodes", missing), "", null),
                new YunshuTaskEnvelope.Completion(completionState, missing),
                confirmation ? Map.of("planHash", planHash(snapshot), "providerContract", "unverified") : Map.of("providerContract", "unverified"),
                Map.of(), new YunshuTaskEnvelope.HostAction(hostAction, needsInput,
                confirmation ? Map.of("planHash", planHash(snapshot)) : Map.of()),
                Map.of("taskId", snapshot.taskId()), error);
    }

    private YunshuProtocol.TaskStatus parseStatus(String status) {
        try { return YunshuProtocol.TaskStatus.valueOf(status); }
        catch (RuntimeException ignored) { return YunshuProtocol.TaskStatus.failed; }
    }

    private List<YunshuTaskEnvelope.Question> questions(List<FormFieldSlot> slots) {
        return blockingSlots(slots).stream()
                .map(slot -> new YunshuTaskEnvelope.Question("field-" + slot.fieldCode(), List.of(slot.fieldCode()),
                        slot.fieldType(), "请填写：" + slot.displayName()
                                + (slot.errorMessage() == null ? "" : "（" + slot.errorMessage() + "）"), true,
                        slot.allowedValues().stream().map(value -> Map.<String, Object>of("value", value)).toList(), Map.of()))
                .toList();
    }

    private List<FormFieldSlot> blockingSlots(List<FormFieldSlot> slots) {
        return slots.stream().filter(slot -> "invalid".equals(slot.validationState())
                || (slot.required() && (slot.value() == null || String.valueOf(slot.value()).isBlank()))).toList();
    }

    private Map<String, Object> formRef(FormReference ref, String goal) {
        if (ref == null) return Map.of("resolution", "unverified", "goal", goal);
        return Map.of("applicationCode", ref.applicationCode(), "schemaCode", ref.schemaCode(),
                "formCode", ref.formCode(), "goal", goal);
    }

    private String planHash(FormTaskSnapshot snapshot) {
        String raw = snapshot.formRef() + "|" + snapshot.metadataVersion() + "|" + snapshot.slots();
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    private YunshuTaskEnvelope failure(String taskId, YunshuProtocol.ErrorCode code, String message) {
        return new YunshuTaskEnvelope(YunshuProtocol.VERSION,
                new YunshuTaskEnvelope.Task(taskId, YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.failed, 0, false, Instant.now()),
                new YunshuTaskEnvelope.Interaction(YunshuProtocol.InteractionType.none, List.of()),
                new YunshuTaskEnvelope.Draft(Map.of(), "", 0, List.of()), new YunshuTaskEnvelope.Validation(List.of()),
                new YunshuTaskEnvelope.Next(Map.of(), "", null), new YunshuTaskEnvelope.Completion("failed", List.of()),
                Map.of(), Map.of(), new YunshuTaskEnvelope.HostAction(YunshuProtocol.HostActionType.report_failure, false, Map.of()),
                Map.of(), new YunshuTaskEnvelope.Error(code, message, false));
    }
}

