package com.cpclaw.metadata;

import com.cpclaw.common.security.SensitiveDataMasker;
import com.cpclaw.metadata.dto.MetadataSyncLogOverviewResponse;
import com.cpclaw.metadata.dto.MetadataSyncLogResponse;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.entity.MetadataSyncLog;
import com.cpclaw.metadata.repository.MetadataSyncLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataSyncLogService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final MetadataSyncLogRepository repository;
    private final SensitiveDataMasker masker;

    public MetadataSyncLogService(MetadataSyncLogRepository repository, SensitiveDataMasker masker) {
        this.repository = repository;
        this.masker = masker;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String start() {
        MetadataSyncLog log = new MetadataSyncLog();
        log.setId(UUID.randomUUID().toString());
        log.setSyncId(UUID.randomUUID().toString());
        log.setStatus("running");
        log.setStartedAt(Instant.now());
        repository.save(log);
        return log.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(String id, MetadataSyncResponse result) {
        MetadataSyncLog log = repository.findById(id).orElseThrow();
        Instant completedAt = Instant.now();
        log.setStatus("succeeded");
        log.setCompletedAt(completedAt);
        log.setDurationMs(Duration.between(log.getStartedAt(), completedAt).toMillis());
        log.setSyncId(result.syncId());
        log.setAppCount(result.appCount());
        log.setEntityCount(result.entityCount());
        log.setDataItemCount(result.dataItemCount());
        log.setRelationCount(result.relationCount());
        log.setSearchDocumentCount(result.searchDocumentCount());
        log.setGraphNodeCount(result.graphNodeCount());
        log.setGraphEdgeCount(result.graphEdgeCount());
        log.setErrorMessage(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String id, Exception exception) {
        repository.findById(id).ifPresent(log -> {
            Instant completedAt = Instant.now();
            log.setStatus("failed");
            log.setCompletedAt(completedAt);
            log.setDurationMs(Duration.between(log.getStartedAt(), completedAt).toMillis());
            log.setErrorMessage(safeError(exception));
        });
    }

    @Transactional(readOnly = true)
    public MetadataSyncLogOverviewResponse overview() {
        List<MetadataSyncLogResponse> items = repository.findTop20ByOrderByStartedAtDesc().stream().map(this::toResponse).toList();
        String latestSuccessfulAt = repository.findTop1ByStatusOrderByCompletedAtDesc("succeeded").stream()
            .map(MetadataSyncLog::getCompletedAt)
            .filter(value -> value != null)
            .findFirst()
            .map(Instant::toString)
            .orElse(null);
        return new MetadataSyncLogOverviewResponse(latestSuccessfulAt, items);
    }

    private MetadataSyncLogResponse toResponse(MetadataSyncLog log) {
        return new MetadataSyncLogResponse(
            log.getId(), log.getSyncId(), log.getStatus(), log.getStartedAt().toString(),
            log.getCompletedAt() == null ? null : log.getCompletedAt().toString(), log.getDurationMs(),
            log.getAppCount(), log.getEntityCount(), log.getDataItemCount(), log.getRelationCount(), hasDetailedCounts(log),
            log.getSearchDocumentCount(), log.getGraphNodeCount(), log.getGraphEdgeCount(), log.getErrorMessage()
        );
    }

    private boolean hasDetailedCounts(MetadataSyncLog log) {
        return log.getEntityCount() > 0 || log.getDataItemCount() > 0 || log.getRelationCount() > 0 || log.getSearchDocumentCount() > 0;
    }

    private String safeError(Exception exception) {
        String value = exception.getMessage() == null ? "同步失败，请检查管理员云枢连接与权限" : masker.mask(exception.getMessage());
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH) + "...";
    }
}
