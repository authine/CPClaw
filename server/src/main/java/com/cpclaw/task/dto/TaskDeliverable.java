package com.cpclaw.task.dto;

import java.util.List;

/** A user-facing outcome that the delegated domain task must satisfy. */
public record TaskDeliverable(
    String id,
    boolean required,
    String description,
    List<String> acceptance
) {
    public TaskDeliverable {
        id = id == null ? "" : id.trim();
        description = description == null ? "" : description.trim();
        acceptance = acceptance == null ? List.of() : List.copyOf(acceptance.stream().filter(value -> value != null && !value.isBlank()).limit(20).toList());
    }
}
