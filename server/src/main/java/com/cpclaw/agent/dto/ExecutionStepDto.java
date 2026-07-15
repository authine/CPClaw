package com.cpclaw.agent.dto;

public record ExecutionStepDto(
    String id,
    String title,
    String status,
    String process,
    String conclusion
) {
}
