package com.cpclaw.task;

import com.cpclaw.task.entity.SemanticTaskRun;
import com.cpclaw.task.repository.SemanticTaskEventRepository;
import com.cpclaw.task.repository.SemanticTaskRunRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected-by-deployment lifecycle read surface for Remote API/CLI adapters. */
@RestController
@RequestMapping("/api/tasks")
public class TaskLifecycleController {
    private final SemanticTaskRunRepository runRepository;
    private final SemanticTaskEventRepository eventRepository;

    public TaskLifecycleController(SemanticTaskRunRepository runRepository, SemanticTaskEventRepository eventRepository) {
        this.runRepository = runRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String taskId) {
        return runRepository.findById(taskId).map(this::response).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{taskId}/events")
    public Map<String, Object> events(@PathVariable String taskId) {
        return Map.of("taskId", taskId, "events", eventRepository.findByTaskIdOrderByEventSequenceAsc(taskId).stream().map(event -> Map.of("sequence", event.getEventSequence(), "event", event.getEventJson(), "createdAt", event.getCreatedAt())).toList());
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String taskId) {
        return runRepository.findById(taskId).map(run -> {
            if ("running".equals(run.getStatus())) {
                run.setStatus("cancelled");
                run.setUpdatedAt(java.time.Instant.now());
                run.setCompletedAt(run.getUpdatedAt());
                runRepository.save(run);
            }
            return ResponseEntity.ok(response(run));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Map<String, Object> response(SemanticTaskRun run) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("taskId", run.getId());
        value.put("status", run.getStatus());
        value.put("parentTaskId", run.getParentTaskId());
        value.put("channel", run.getChannel());
        value.put("createdAt", run.getCreatedAt());
        value.put("updatedAt", run.getUpdatedAt());
        value.put("completedAt", run.getCompletedAt());
        value.put("completion", run.getCompletionJson());
        value.put("evidence", run.getEvidenceJson());
        value.put("result", run.getResultJson());
        return value;
    }
}
