package com.cpclaw.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.cpclaw.identity.PrincipalContext;
import com.cpclaw.identity.PrincipalContextService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserFeedbackControllerTests {
    @Test
    void nonAdminCannotReadFeedbackList() {
        UserFeedbackRepository repository = org.mockito.Mockito.mock(UserFeedbackRepository.class);
        PrincipalContextService principals = org.mockito.Mockito.mock(PrincipalContextService.class);
        when(principals.current()).thenReturn(new PrincipalContext("user", "user", "User", "", "tenant-a", true, false));
        UserFeedbackController controller = new UserFeedbackController(repository, principals);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, controller::list);
    }

    @Test
    void adminUpdatesOnlyFeedbackInCurrentTenantAndPreservesExistingNoteWhenOmitted() {
        UserFeedbackRepository repository = org.mockito.Mockito.mock(UserFeedbackRepository.class);
        PrincipalContextService principals = org.mockito.Mockito.mock(PrincipalContextService.class);
        when(principals.current()).thenReturn(new PrincipalContext("admin", "admin", "Admin", "", "tenant-a", true, true));
        UserFeedback item = new UserFeedback(); item.setId("feedback-1"); item.setTenantId("tenant-a"); item.setAdminNote("原备注"); item.setStatus("OPEN");
        when(repository.findByIdAndTenantId("feedback-1", "tenant-a")).thenReturn(Optional.of(item));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserFeedbackController controller = new UserFeedbackController(repository, principals);
        var response = controller.update("feedback-1", new UserFeedbackController.UpdateFeedback("RESOLVED", null));
        assertEquals("RESOLVED", response.data().getStatus());
        assertEquals("原备注", response.data().getAdminNote());
    }

    @Test
    void invalidFeedbackPayloadIsRejected() {
        UserFeedbackController controller = new UserFeedbackController(
                org.mockito.Mockito.mock(UserFeedbackRepository.class), org.mockito.Mockito.mock(PrincipalContextService.class));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.create(new UserFeedbackController.CreateFeedback("app", "UNKNOWN", "", "")));
    }
}
