package com.cpclaw.settings.dto;

public record ModelConnectionTestResponse(
    boolean success,
    String message,
    long latencyMs
) {}
