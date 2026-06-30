package com.cpclaw.metadata;

import com.cpclaw.cloudpivot.CloudPivotConnector;
import com.cpclaw.cloudpivot.CloudPivotMetadataSnapshot;
import com.cpclaw.credential.CredentialService;
import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.entity.CloudPivotApp;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.entity.MetadataSearchDocument;
import com.cpclaw.metadata.repository.CloudPivotAppRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.MetadataSearchDocumentRepository;
import com.cpclaw.settings.entity.SystemSettings;
import com.cpclaw.settings.repository.SystemSettingsRepository;
import com.cpclaw.vector.MetadataVectorSearch;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataService {

    private static final Logger log = LoggerFactory.getLogger(MetadataService.class);

    private static final String SETTINGS_ID = "default";
    private static final String OWNER_SYSTEM = "system";
    private static final String ADMIN_CLOUDPIVOT_PASSWORD = "admin_cloudpivot_password";

    private final CloudPivotAppRepository appRepository;
    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotDataItemRepository dataItemRepository;
    private final CloudPivotEntityRelationRepository relationRepository;
    private final MetadataSearchDocumentRepository searchDocumentRepository;
    private final SystemSettingsRepository settingsRepository;
    private final CredentialService credentialService;
    private final CloudPivotConnector cloudPivotConnector;
    private final MetadataVectorSearch metadataVectorSearch;

    public MetadataService(
        CloudPivotAppRepository appRepository,
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        MetadataSearchDocumentRepository searchDocumentRepository,
        SystemSettingsRepository settingsRepository,
        CredentialService credentialService,
        CloudPivotConnector cloudPivotConnector,
        MetadataVectorSearch metadataVectorSearch
    ) {
        this.appRepository = appRepository;
        this.entityRepository = entityRepository;
        this.dataItemRepository = dataItemRepository;
        this.relationRepository = relationRepository;
        this.searchDocumentRepository = searchDocumentRepository;
        this.settingsRepository = settingsRepository;
        this.credentialService = credentialService;
        this.cloudPivotConnector = cloudPivotConnector;
        this.metadataVectorSearch = metadataVectorSearch;
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
        relationRepository.deleteAllInBatch();
        dataItemRepository.deleteAllInBatch();
        entityRepository.deleteAllInBatch();
        appRepository.deleteAllInBatch();

        List<CloudPivotApp> apps = snapshot.apps().stream()
            .map(app -> createApp(app.code(), app.name(), app.description(), syncId, now))
            .toList();
        appRepository.saveAll(apps);
        Map<String, CloudPivotApp> appsByCode = indexAppsByCode(apps);

        List<CloudPivotEntity> entities = snapshot.entities().stream()
            .map(entity -> createEntity(appIdByCode(apps, entity.appCode()), entity.code(), entity.name(), entity.type(), now))
            .toList();
        entityRepository.saveAll(entities);
        Map<String, CloudPivotEntity> entitiesByKey = indexEntitiesByKey(apps, entities);

        List<CloudPivotDataItem> dataItems = snapshot.dataItems().stream()
            .map(dataItem -> createDataItem(dataItem, entitiesByKey.get(entityKey(dataItem.appCode(), dataItem.entityCode())), entitiesByKey, now))
            .filter(item -> item != null)
            .toList();
        dataItemRepository.saveAll(dataItems);
        Map<String, CloudPivotDataItem> dataItemsByKey = indexDataItemsByKey(apps, entities, dataItems);

        List<CloudPivotEntityRelation> relations = snapshot.relations().stream()
            .map(relation -> createRelation(relation, appsByCode, entitiesByKey, dataItemsByKey, now))
            .filter(item -> item != null)
            .toList();
        relationRepository.saveAll(relations);

        List<MetadataSearchDocument> searchDocuments = new ArrayList<>();
        for (CloudPivotApp app : apps) {
            searchDocuments.add(createDocument("app", app.getId(), app.getId(), null, app.getName(), app.getAppCode(), app.getDescription(), app.getName(), "low", syncId, now));
        }
        for (CloudPivotEntity entity : entities) {
            CloudPivotApp app = apps.stream().filter(item -> item.getId().equals(entity.getAppId())).findFirst().orElseThrow();
            searchDocuments.add(createDocument(
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
        for (CloudPivotDataItem dataItem : dataItems) {
            CloudPivotEntity entity = entities.stream().filter(item -> item.getId().equals(dataItem.getEntityId())).findFirst().orElse(null);
            if (entity == null) {
                continue;
            }
            CloudPivotApp app = apps.stream().filter(item -> item.getId().equals(entity.getAppId())).findFirst().orElseThrow();
            String relationText = dataItem.isReference() ? " 关联表单 关联关系 引用对象" : " 数据项 字段 属性 过滤 统计 分析";
            searchDocuments.add(createDocument(
                "data_item",
                dataItem.getId(),
                entity.getAppId(),
                entity.getId(),
                dataItem.getName(),
                dataItem.getDataItemCode(),
                app.getName() + " " + entity.getName() + " " + dataItem.getName() + " " + dataItem.getDataItemCode() + " " + dataItem.getDataType() + relationText,
                app.getName() + " / " + entity.getName() + " / " + dataItem.getName(),
                riskLevelByCode(snapshot, entity.getEntityCode()),
                syncId,
                now
            ));
        }
        for (CloudPivotEntityRelation relation : relations) {
            CloudPivotEntity sourceEntity = entities.stream().filter(item -> item.getId().equals(relation.getSourceEntityId())).findFirst().orElse(null);
            CloudPivotEntity targetEntity = entities.stream().filter(item -> item.getId().equals(relation.getTargetEntityId())).findFirst().orElse(null);
            CloudPivotApp app = apps.stream().filter(item -> item.getId().equals(relation.getAppId())).findFirst().orElse(null);
            if (sourceEntity == null || targetEntity == null || app == null) {
                continue;
            }
            String name = hasText(relation.getRelationName()) ? relation.getRelationName() : sourceEntity.getName() + "关联" + targetEntity.getName();
            searchDocuments.add(createDocument(
                "relation",
                relation.getId(),
                relation.getAppId(),
                sourceEntity.getId(),
                name,
                relation.getRelationType(),
                app.getName() + " " + sourceEntity.getName() + " " + targetEntity.getName() + " " + name + " 关联表单 关联关系 跨实体 查询 分析",
                app.getName() + " / " + sourceEntity.getName() + " / " + name + " -> " + targetEntity.getName(),
                riskLevelByCode(snapshot, sourceEntity.getEntityCode()),
                syncId,
                now
            ));
        }
        searchDocumentRepository.saveAll(searchDocuments);
        try {
            metadataVectorSearch.indexDocuments(searchDocuments);
        } catch (RuntimeException exception) {
            log.info("metadata vector index skipped: {}", exception.getMessage());
        }

        return new MetadataSyncResponse(syncId, "cloudpivot-metadata-initialized", apps.size(), entities.size(), dataItems.size(), relations.size(), (int) searchDocumentRepository.count(), now.toString());
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

    private CloudPivotDataItem createDataItem(
        CloudPivotMetadataSnapshot.DataItemMetadata dataItem,
        CloudPivotEntity entity,
        Map<String, CloudPivotEntity> entitiesByKey,
        Instant now
    ) {
        if (entity == null || !hasText(dataItem.code())) {
            log.info("skip CloudPivot data item because entity or code is missing: appCode={}, entityCode={}, dataItemCode={}", dataItem.appCode(), dataItem.entityCode(), dataItem.code());
            return null;
        }
        CloudPivotEntity referenceEntity = hasText(dataItem.referenceEntityCode())
            ? findEntity(dataItem.appCode(), dataItem.referenceEntityCode(), entitiesByKey)
            : null;
        CloudPivotDataItem item = new CloudPivotDataItem();
        item.setId(UUID.randomUUID().toString());
        item.setEntityId(entity.getId());
        item.setDataItemCode(dataItem.code());
        item.setName(hasText(dataItem.name()) ? dataItem.name() : dataItem.code());
        item.setDataType(dataItem.dataType());
        item.setRequired(dataItem.required());
        item.setReference(dataItem.reference());
        item.setReferenceEntityId(referenceEntity == null ? null : referenceEntity.getId());
        item.setDescription(dataItem.description());
        item.setRawJson(hasText(dataItem.rawJson()) ? dataItem.rawJson() : "{\"source\":\"cloudpivot-data-item\"}");
        item.setSyncedAt(now);
        return item;
    }

    private CloudPivotEntityRelation createRelation(
        CloudPivotMetadataSnapshot.EntityRelationMetadata relationMetadata,
        Map<String, CloudPivotApp> appsByCode,
        Map<String, CloudPivotEntity> entitiesByKey,
        Map<String, CloudPivotDataItem> dataItemsByKey,
        Instant now
    ) {
        CloudPivotApp app = appsByCode.get(relationMetadata.appCode());
        CloudPivotEntity sourceEntity = findEntity(relationMetadata.appCode(), relationMetadata.sourceEntityCode(), entitiesByKey);
        CloudPivotEntity targetEntity = findEntity(relationMetadata.appCode(), relationMetadata.targetEntityCode(), entitiesByKey);
        if (app == null || sourceEntity == null || targetEntity == null) {
            log.info(
                "skip CloudPivot relation because app/source/target is unresolved: appCode={}, sourceEntityCode={}, targetEntityCode={}",
                relationMetadata.appCode(),
                relationMetadata.sourceEntityCode(),
                relationMetadata.targetEntityCode()
            );
            return null;
        }
        CloudPivotDataItem sourceDataItem = dataItemsByKey.get(dataItemKey(relationMetadata.appCode(), relationMetadata.sourceEntityCode(), relationMetadata.sourceDataItemCode()));
        if (sourceDataItem == null) {
            log.info(
                "skip CloudPivot relation because source data item is unresolved: appCode={}, sourceEntityCode={}, sourceDataItemCode={}",
                relationMetadata.appCode(),
                relationMetadata.sourceEntityCode(),
                relationMetadata.sourceDataItemCode()
            );
            return null;
        }
        CloudPivotEntityRelation relation = new CloudPivotEntityRelation();
        relation.setId(UUID.randomUUID().toString());
        relation.setAppId(app.getId());
        relation.setSourceEntityId(sourceEntity.getId());
        relation.setSourceDataItemId(sourceDataItem.getId());
        relation.setTargetEntityId(targetEntity.getId());
        relation.setRelationType(hasText(relationMetadata.relationType()) ? relationMetadata.relationType() : "relevance_form");
        relation.setRelationName(relationMetadata.relationName());
        relation.setRawJson(hasText(relationMetadata.rawJson()) ? relationMetadata.rawJson() : "{\"source\":\"cloudpivot-relation\"}");
        relation.setSyncedAt(now);
        return relation;
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

    private Map<String, CloudPivotApp> indexAppsByCode(List<CloudPivotApp> apps) {
        Map<String, CloudPivotApp> result = new LinkedHashMap<>();
        for (CloudPivotApp app : apps) {
            result.put(app.getAppCode(), app);
        }
        return result;
    }

    private Map<String, CloudPivotEntity> indexEntitiesByKey(List<CloudPivotApp> apps, List<CloudPivotEntity> entities) {
        Map<String, String> appCodesById = new LinkedHashMap<>();
        for (CloudPivotApp app : apps) {
            appCodesById.put(app.getId(), app.getAppCode());
        }
        Map<String, CloudPivotEntity> result = new LinkedHashMap<>();
        for (CloudPivotEntity entity : entities) {
            String appCode = appCodesById.get(entity.getAppId());
            if (hasText(appCode)) {
                result.put(entityKey(appCode, entity.getEntityCode()), entity);
            }
        }
        return result;
    }

    private Map<String, CloudPivotDataItem> indexDataItemsByKey(List<CloudPivotApp> apps, List<CloudPivotEntity> entities, List<CloudPivotDataItem> dataItems) {
        Map<String, String> appCodesById = new LinkedHashMap<>();
        for (CloudPivotApp app : apps) {
            appCodesById.put(app.getId(), app.getAppCode());
        }
        Map<String, CloudPivotEntity> entitiesById = new LinkedHashMap<>();
        for (CloudPivotEntity entity : entities) {
            entitiesById.put(entity.getId(), entity);
        }
        Map<String, CloudPivotDataItem> result = new LinkedHashMap<>();
        for (CloudPivotDataItem dataItem : dataItems) {
            CloudPivotEntity entity = entitiesById.get(dataItem.getEntityId());
            if (entity == null) {
                continue;
            }
            String appCode = appCodesById.get(entity.getAppId());
            if (hasText(appCode)) {
                result.put(dataItemKey(appCode, entity.getEntityCode(), dataItem.getDataItemCode()), dataItem);
            }
        }
        return result;
    }

    private CloudPivotEntity findEntity(String appCode, String entityCode, Map<String, CloudPivotEntity> entitiesByKey) {
        if (!hasText(entityCode)) {
            return null;
        }
        CloudPivotEntity exact = entitiesByKey.get(entityKey(appCode, entityCode));
        if (exact != null) {
            return exact;
        }
        List<CloudPivotEntity> sameCode = entitiesByKey.values().stream()
            .filter(entity -> entityCode.equals(entity.getEntityCode()))
            .toList();
        return sameCode.size() == 1 ? sameCode.getFirst() : null;
    }

    private String entityKey(String appCode, String entityCode) {
        return (appCode == null ? "" : appCode) + ":" + (entityCode == null ? "" : entityCode);
    }

    private String dataItemKey(String appCode, String entityCode, String dataItemCode) {
        return entityKey(appCode, entityCode) + ":" + (dataItemCode == null ? "" : dataItemCode);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
