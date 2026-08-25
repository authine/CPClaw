package com.cpclaw.memory.dto;

import java.util.List;

public record MemorySettingsResponse(
    String principalId,
    String displayName,
    boolean superAdmin,
    List<MemoryEntryDto> personal,
    List<MemoryEntryDto> global,
    boolean globalVisible
) { }
