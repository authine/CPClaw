package com.cpclaw.task.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "semantic_task_events")
public class SemanticTaskEvent {
    @Id private String id;
    @Column(name = "task_id", nullable = false) private String taskId;
    @Column(name = "event_sequence", nullable = false) private long eventSequence;
    @Lob @Column(name = "event_json", nullable = false) private String eventJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public String getId() { return id; } public void setId(String value) { id = value; }
    public String getTaskId() { return taskId; } public void setTaskId(String value) { taskId = value; }
    public long getEventSequence() { return eventSequence; } public void setEventSequence(long value) { eventSequence = value; }
    public String getEventJson() { return eventJson; } public void setEventJson(String value) { eventJson = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
