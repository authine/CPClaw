package com.cpclaw.task.repository;

import com.cpclaw.task.entity.SemanticTaskEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemanticTaskEventRepository extends JpaRepository<SemanticTaskEvent, String> {
    List<SemanticTaskEvent> findByTaskIdOrderByEventSequenceAsc(String taskId);
}
