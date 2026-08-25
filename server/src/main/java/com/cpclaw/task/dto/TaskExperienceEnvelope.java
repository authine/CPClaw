package com.cpclaw.task.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Versioned result contract shared by host adapters. */
public record TaskExperienceEnvelope(
    String experienceVersion,
    Task task,
    Map<String, Object> summary,
    List<TaskProgressEvent> visibleTrace,
    Map<String, Object> output,
    Map<String, Object> interaction,
    Map<String, Object> hostAction,
    Map<String, Object> completion,
    Map<String, Object> evidence,
    Map<String, Object> continuation
) {
    public TaskExperienceEnvelope(
        String experienceVersion,
        Task task,
        Map<String, Object> summary,
        List<TaskProgressEvent> visibleTrace,
        Map<String, Object> output,
        Map<String, Object> interaction,
        Map<String, Object> hostAction
    ) {
        this(experienceVersion, task, summary, visibleTrace, output, interaction, hostAction, Map.of(), Map.of(), Map.of());
    }

    public TaskExperienceEnvelope {
        experienceVersion = experienceVersion == null ? "1.0" : experienceVersion;
        summary = summary == null ? Map.of() : Map.copyOf(summary);
        visibleTrace = visibleTrace == null ? List.of() : List.copyOf(visibleTrace);
        output = output == null ? Map.of() : Map.copyOf(output);
        interaction = interaction == null ? Map.of("type", "none") : Map.copyOf(interaction);
        hostAction = hostAction == null ? Map.of("type", "report_failure") : Map.copyOf(hostAction);
        completion = completion == null ? Map.of() : Map.copyOf(completion);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        continuation = continuation == null ? Map.of() : Map.copyOf(continuation);
    }

    public record Task(String id, String status, Instant updatedAt, boolean retryable) { }
}
