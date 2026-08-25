package com.cpclaw.memory.dto;

public record SaveMemoryEntryRequest(
    String memoryType,
    String content,
    Integer priority,
    Long ttlDays
) { }
