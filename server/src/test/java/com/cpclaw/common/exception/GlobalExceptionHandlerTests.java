package com.cpclaw.common.exception;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cpclaw.common.security.SensitiveDataMasker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GlobalExceptionHandlerTests {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new StatusController())
        .setControllerAdvice(new GlobalExceptionHandler(new SensitiveDataMasker()))
        .build();

    @Test
    void preservesForbiddenStatusForBusinessAuthorizationFailures() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success", equalTo(false)))
            .andExpect(jsonPath("$.message", equalTo("没有权限")));
    }

    @RestController
    static class StatusController {
        @GetMapping("/test/forbidden")
        String forbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有权限");
        }
    }
}
