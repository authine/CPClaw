package com.cpclaw.metadata;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String ADMIN_CLOUDPIVOT_PASSWORD = "admin_cloudpivot_password";

    private final CloudPivotAppRepository appRepository;
    private final CloudPivotEntityRepository entityRepository;
    private final MetadataSearchDocumentRepository searchDocumentRepository;
    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector cloudPivotConnector;

    public MetadataService(
        CloudPivotAppRepository appRepository,
        CloudPivotEntityRepository entityRepository,
        MetadataSearchDocumentRepository searchDocumentRepository,
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector
    ) {
        this.appRepository = appRepository;
        this.entityRepository = entityRepository;
        this.searchDocumentRepository = searchDocumentRepository;
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
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
    public MetadataSyncResponse initializeCloudPivotMetadata() {
        Instant now = Instant.now();
        String syncId = UUID.randomUUID().toString();
        SystemSettings settings = settingsRepository.findById(SETTINGS_ID)
            .orElseThrow(() -> new IllegalStateException("请先配置管理员云枢连接信息"));
        String password = credentialService.revealCredential(OWNER_SYSTEM, SETTINGS_ID, ADMIN_CLOUDPIVOT_PASSWORD)
            .orElseThrow(() -> new IllegalStateException("请先配置管理员云枢密码"));
        CloudPivotMetadataSnapshot snapshot = cloudPivotConnector.fetchMetadata(
            settings.getAdminCloudPivotBaseUrl(),
            settings.getAdminCloudPivotUsername(),
            password
        );

        searchDocumentRepository.deleteAllInBatch();
        entityRepository.deleteAllInBatch();
        appRepository.deleteAllInBatch();

        List<CloudPivotApp> apps = snapshot.apps().stream()
            .map(app -> createApp(app.code(), app.name(), app.description(), syncId, now))
            .toList();
        appRepository.saveAll(apps);

        List<CloudPivotEntity> entities = snapshot.entities().stream()
            .map(entity -> createEntity(appIdByCode(apps, entity.appCode()), entity.code(), entity.name(), entity.type(), now))
            .toList();
        entityRepository.saveAll(entities);

        for (CloudPivotApp app : apps) {
            searchDocumentRepository.save(createDocument("app", app.getId(), app.getId(), null, app.getName(), app.getAppCode(), app.getDescription(), app.getName(), "low", syncId, now));
        }
        for (CloudPivotEntity entity : entities) {
            CloudPivotApp app = apps.stream().filter(item -> item.getId().equals(entity.getAppId())).findFirst().orElseThrow();
            searchDocumentRepository.save(createDocument(
                "entity",
                entity.getId(),
                entity.getAppId(),
                entity.getId(),
                entity.getName(),
                entity.getEntityCode(),
                app.getName() + " " + entity.getName() + " 查询 新增 修改 删除 表单 数据 字段 附件 流程 操作",
                app.getName() + " / " + entity.getName(),
                riskLevelByCode(snapshot, entity.getEntityCode()),
                syncId,
                now
            ));
        }

        return new MetadataSyncResponse(syncId, "cloudpivot-metadata-initialized", apps.size(), entities.size(), (int) searchDocumentRepository.count(), now.toString());
    }

    private CloudPivotApp createApp(String code, String name, String description, String syncId, Instant now) {
        CloudPivotApp app = new CloudPivotApp();
        app.setId(UUID.randomUUID().toString());
        app.setAppCode(code);
        app.setName(name);
        app.setDescription(description);
        app.setRawJson("{\"source\":\"cloudpivot-connector\"}");
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
        entity.setRawJson("{\"source\":\"cloudpivot-connector\"}");
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

    private String appIdByCode(List<CloudPivotApp> apps, String appCode) {
        return apps.stream()
            .filter(app -> app.getAppCode().equals(appCode))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("云枢元数据实体未找到所属应用：" + appCode))
            .getId();
    }

    private String riskLevelByCode(CloudPivotMetadataSnapshot snapshot, String entityCode) {
        return snapshot.entities().stream()
            .filter(entity -> entity.code().equals(entityCode))
            .findFirst()
            .map(CloudPivotMetadataSnapshot.EntityMetadata::riskLevel)
            .orElse("low");
    }
}
