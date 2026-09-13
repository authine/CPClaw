package com.cpclaw.skill.yunshu.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.cpclaw.yunshu.confirmation.ConfirmationTicketService;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.cpclaw.yunshu.protocol.YunshuSkillGateway;
import com.cpclaw.yunshu.protocol.YunshuTaskEnvelope;
import com.cpclaw.yunshu.protocol.YunshuTaskRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfirmingFormTaskRuntimeDecoratorTests {
    private final YunshuHostContext context = new YunshuHostContext("tenant", "subject", "workbuddy", Map.of());

    @Test
    void decoratorPassesThroughAndNeverConsumesTicket() {
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        FormTaskSnapshot snapshot = new FormTaskSnapshot("task-1", new FormTaskScope("tenant", "subject", "workbuddy"),
                "confirmation_required", Map.of("schemaCode", "lead"), "v1", 3, 2,
                List.of(new FormFieldSlot("name", "名称", "text", "A", "user", 1d, true, false, "valid", null, List.of())), List.of());
        when(tasks.get(eq("task-1"), any())).thenReturn(snapshot);

        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        when(tickets.authorizeExecution(any(), eq(context), any(), eq(true)))
                .thenReturn(ConfirmationTicketService.TicketAuthorization.granted("ticket-1", "idem"),
                        ConfirmationTicketService.TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票已被消费或已失效。"));
        YunshuTaskEnvelope delegated = new YunshuTaskEnvelope(YunshuProtocol.VERSION,
                new YunshuTaskEnvelope.Task("task-1", YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.submitted, 4, false, null),
                null, null, null, null, new YunshuTaskEnvelope.Completion("submitted", List.of()), Map.of(), Map.of(), null, Map.of(), null);
        YunshuSkillGateway delegate = mock(YunshuSkillGateway.class);
        when(tickets.authorizeExecution(any(), eq(context), any(), eq(true)))
                .thenReturn(ConfirmationTicketService.TicketAuthorization.denied(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, "确认票已被消费或已失效。"));
        when(delegate.handle(any(), eq(context))).thenReturn(delegated);
        ConfirmingFormTaskRuntimeDecorator decorator = new ConfirmingFormTaskRuntimeDecorator(delegate, tasks, tickets, true);
        YunshuTaskRequest request = new YunshuTaskRequest(YunshuProtocol.VERSION, YunshuProtocol.Operation.action, "task-1", "", Map.of(),
                Map.of("type", "confirm", "confirmationTicket", "signed"), "r1", 3L, "");

        assertEquals(YunshuProtocol.TaskStatus.submitted, decorator.handle(request, context).task().status());
        assertEquals(YunshuProtocol.TaskStatus.submitted, decorator.handle(request, context).task().status());
        verifyNoInteractions(tickets);
        verify(delegate, times(2)).handle(any(), eq(context));
    }

    @Test
    void missingTicketIsPassedToRuntimeForCanonicalValidation() {
        FormTaskPersistenceService tasks = mock(FormTaskPersistenceService.class);
        ConfirmationTicketService tickets = mock(ConfirmationTicketService.class);
        YunshuSkillGateway delegate = mock(YunshuSkillGateway.class);
        ConfirmingFormTaskRuntimeDecorator decorator = new ConfirmingFormTaskRuntimeDecorator(delegate, tasks, tickets, false);
        YunshuTaskRequest missing = new YunshuTaskRequest(YunshuProtocol.VERSION, YunshuProtocol.Operation.action, "task-1", "", Map.of(),
                Map.of("type", "confirm"), "r1", 1L, "");
        when(delegate.handle(any(), eq(context))).thenReturn(new YunshuTaskEnvelope(YunshuProtocol.VERSION,
                new YunshuTaskEnvelope.Task("task-1", YunshuProtocol.TaskKind.form, YunshuProtocol.TaskStatus.failed, 0, false, null),
                null, null, null, null, null, Map.of(), Map.of(), null, Map.of(), null));
        decorator.handle(missing, context);
        verify(delegate).handle(any(), eq(context));
        verifyNoInteractions(tickets, tasks);
    }
}
