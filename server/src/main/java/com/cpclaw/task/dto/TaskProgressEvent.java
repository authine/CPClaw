package com.cpclaw.task.dto;

/** A safe, user-visible task event. It deliberately excludes model reasoning and transport details. */
public record TaskProgressEvent(
    int progress,
    String phase,
    String title,
    String message,
    String state
) {
    public TaskProgressEvent {
        progress = Math.max(0, Math.min(100, progress));
        phase = phase == null ? "execution" : phase;
        title = title == null ? "处理任务" : title;
        message = message == null ? "" : message;
        state = state == null ? "running" : state;
    }
}
