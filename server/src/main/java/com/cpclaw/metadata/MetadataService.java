package com.cpclaw.metadata;

import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {

    private final CloudPivotAppRepository appRepository;
    private final CloudPivotEntityRepository entityRepository;
    private final MetadataSearchDocumentRepository searchDocumentRepository;

    public MetadataService(
        CloudPivotAppRepository appRepository,
        CloudPivotEntityRepository entityRepository,
        MetadataSearchDocumentRepository searchDocumentRepository
    ) {
        this.appRepository = appRepository;
        this.entityRepository = entityRepository;
        this.searchDocumentRepository = searchDocumentRepository;
    }

    public List<MetadataAppSummary> listApps() {
        return appRepository.findAllByOrderBySyncedAtDesc().stream()
            .map(app -> new MetadataAppSummary(
                app.getId(),
                app.getAppCode(),
                app.getName(),
                entityRepository.findByAppId(app.getId()).size(),
                app.getSyncedAt() == null ? null : app.getSyncedAt().toString()
            ))
            .toList();
    }

    @Transactional
    public MetadataSyncResponse syncSampleMetadata() {
        Instant now = Instant.now();
        String syncId = UUID.randomUUID().toString();
        searchDocumentRepository.deleteAll();
        entityRepository.deleteAll();
        appRepository.deleteAll();

        CloudPivotApp crm = createApp("crm", "CRM", "客户、商机、销售订单等销售业务应用", syncId, now);
        CloudPivotApp finance = createApp("finance", "报销管理", "报销单、发票和费用明细管理", syncId, now);
        appRepository.saveAll(List.of(crm, finance));

        List<CloudPivotEntity> entities = List.of(
            createEntity(crm.getId(), "sales_order", "销售订单", "data", now),
            createEntity(crm.getId(), "customer", "客户", "data", now),
            createEntity(crm.getId(), "opportunity", "商机", "data", now),
            createEntity(finance.getId(), "expense_report", "报销单", "data", now),
            createEntity(finance.getId(), "invoice", "发票", "attachment", now)
        );
        entityRepository.saveAll(entities);

        for (CloudPivotApp app : List.of(crm, finance)) {
            searchDocumentRepository.save(createDocument("app", app.getId(), app.getId(), null, app.getName(), app.getAppCode(), app.getDescription(), app.getName(), "low", syncId, now));
        }
        for (CloudPivotEntity entity : entities) {
            String appName = entity.getAppId().equals(crm.getId()) ? crm.getName() : finance.getName();
            searchDocumentRepository.save(createDocument(
                "entity",
                entity.getId(),
                entity.getAppId(),
                entity.getId(),
                entity.getName(),
                entity.getEntityCode(),
                appName + " " + entity.getName() + " 查询 新增 修改 删除 表单 数据 字段",
                appName + " / " + entity.getName(),
                isWriteSensitiveEntity(entity.getName()) ? "medium" : "low",
                syncId,
                now
            ));
        }

        return new MetadataSyncResponse(syncId, "sample-metadata-synced", 2, entities.size(), (int) searchDocumentRepository.count(), now.toString());
    }

    private CloudPivotApp createApp(String code, String name, String description, String syncId, Instant now) {
        CloudPivotApp app = new CloudPivotApp();
        app.setId(UUID.randomUUID().toString());
        app.setAppCode(code);
        app.setName(name);
        app.setDescription(description);
        app.setRawJson("{\"source\":\"mvp-sample\"}");
        app.setSyncBatchId(syncId);
        app.setSyncedAt(now);
        return app;
    }

    private CloudPivotEntity createEntity(String appId, String code, String name, String type, Instant now) {
        CloudPivotEntity entity = new CloudPivotEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setAppId(appId);
        entity.setEntityCode(code);
        entity.setName(name);
        entity.setEntityType(type);
        entity.setRawJson("{\"source\":\"mvp-sample\"}");
        entity.setSyncedAt(now);
        return entity;
    }

    private MetadataSearchDocument createDocument(String objectType, String objectId, String appId, String entityId, String name, String code, String searchText, String graphPath, String riskLevel, String syncId, Instant now) {
        MetadataSearchDocument document = new MetadataSearchDocument();
        document.setId(UUID.randomUUID().toString());
        document.setObjectType(objectType);
        document.setObjectId(objectId);
        document.setAppId(appId);
        document.setEntityId(entityId);
        document.setName(name);
        document.setCode(code);
        document.setSearchText(searchText + " " + name + " " + code);
        document.setEmbeddingText(searchText);
        document.setGraphPath(graphPath);
        document.setRiskLevel(riskLevel);
        document.setSyncBatchId(syncId);
        document.setIndexedAt(now);
        document.setCreatedAt(now);
        return document;
    }

    private boolean isWriteSensitiveEntity(String name) {
        return name.contains("报销") || name.contains("发票");
    }
}
