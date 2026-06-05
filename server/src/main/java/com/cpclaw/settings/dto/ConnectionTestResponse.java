package com.cpclaw.settings.dto;

public record ConnectionTestResponse(
    boolean success,
    String message
) {
}
