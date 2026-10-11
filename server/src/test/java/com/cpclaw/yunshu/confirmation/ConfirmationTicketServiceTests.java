package com.cpclaw.yunshu.confirmation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cpclaw.yunshu.confirmation.entity.YunshuConfirmationTicket;
import com.cpclaw.yunshu.confirmation.repository.YunshuConfirmationTicketRepository;
import com.cpclaw.yunshu.protocol.YunshuHostContext;
import com.cpclaw.yunshu.protocol.YunshuProtocol;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConfirmationTicketServiceTests {
    @Test
    void ticketBindsPlanAndCanOnlyBeConsumedOnce() {
        AtomicReference<YunshuConfirmationTicket> stored = new AtomicReference<>();
        YunshuConfirmationTicketRepository repository = repository(stored);
        ConfirmationTicketService service = new ConfirmationTicketService(repository,
            new ObjectMapper().findAndRegisterModules(), "01234567890123456789012345678901");
        ConfirmationPlan plan = plan(1, 2, "hash-a");

        ConfirmationTicketService.TicketIssueResult issue = service.issue(plan, true);
        assertTrue(issue.issued());

        ConfirmationTicketService.TicketAuthorization first = service.authorizeExecution(issue.ticket(), context(), plan, true);
        assertTrue(first.granted());
        assertEquals("RESERVED", stored.get().getStatus());
        service.consumeExecution(first.ticketId(), context(), plan);
        ConfirmationTicketService.TicketAuthorization replay = service.authorizeExecution(issue.ticket(), context(), plan, true);
        assertFalse(replay.granted());
        assertEquals(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, replay.errorCode());
        assertEquals("CONSUMED", stored.get().getStatus());
    }

    @Test
    void providerVerificationAndAllScopeBindingsAreMandatory() {
        AtomicReference<YunshuConfirmationTicket> stored = new AtomicReference<>();
        ConfirmationTicketService service = new ConfirmationTicketService(repository(stored),
            new ObjectMapper().findAndRegisterModules(), "01234567890123456789012345678901");
        ConfirmationPlan plan = plan(3, 4, "hash-b");

        assertEquals(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED, service.issue(plan, false).errorCode());
        ConfirmationTicketService.TicketIssueResult issue = service.issue(plan, true);
        ConfirmationTicketService.TicketAuthorization unverified = service.authorizeExecution(issue.ticket(), context(), plan, false);
        ConfirmationTicketService.TicketAuthorization stale = service.authorizeExecution(issue.ticket(), context(), plan(3, 5, "hash-b"), true);
        ConfirmationTicketService.TicketAuthorization crossHost = service.authorizeExecution(issue.ticket(),
            new YunshuHostContext("tenant-a", "user-a", "workbuddy", Map.of()), plan, true);

        assertEquals(YunshuProtocol.ErrorCode.CONTRACT_UNVERIFIED, unverified.errorCode());
        assertEquals(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, stale.errorCode());
        assertEquals(YunshuProtocol.ErrorCode.CONFIRMATION_INVALID, crossHost.errorCode());
        assertEquals("PENDING", stored.get().getStatus());
    }

    private YunshuConfirmationTicketRepository repository(AtomicReference<YunshuConfirmationTicket> stored) {
        YunshuConfirmationTicketRepository repository = mock(YunshuConfirmationTicketRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> { stored.set(invocation.getArgument(0)); return stored.get(); });
        when(repository.save(any())).thenAnswer(invocation -> { stored.set(invocation.getArgument(0)); return stored.get(); });
        when(repository.findByIdForConsumption(any())).thenAnswer(invocation -> Optional.ofNullable(stored.get())
            .filter(ticket -> ticket.getId().equals(invocation.getArgument(0))));
        return repository;
    }

    private ConfirmationPlan plan(long stateVersion, long draftVersion, String planHash) {
        return new ConfirmationPlan("task-1", "tenant-a", "user-a", "cpclaw-web", "1.0", "lead-form", "metadata-1",
            stateVersion, draftVersion, planHash, "idem-1", Instant.now().plusSeconds(300));
    }

    private YunshuHostContext context() {
        return new YunshuHostContext("tenant-a", "user-a", "cpclaw-web", Map.of());
    }
}
