package com.cpclaw.memory.dto;

import java.time.Instant;

public record MemoryEntryDto(
    String id,
    String scope,
    String memoryType,
    String content,
    int priority,
    Instant expiresAt,
    Instant updatedAt,
    String ownerPrincipal
) { }
