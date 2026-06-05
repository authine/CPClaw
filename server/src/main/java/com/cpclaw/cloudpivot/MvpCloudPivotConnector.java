package com.cpclaw.cloudpivot;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MvpCloudPivotConnector implements CloudPivotConnector {

    @Override
    public boolean testConnection(String baseUrl, String username, String password) {
        return hasText(baseUrl) && hasText(username) && hasText(password);
    }

    @Override
    public CloudPivotMetadataSnapshot fetchMetadata(String baseUrl, String username, String password) {
        if (!testConnection(baseUrl, username, password)) {
            throw new IllegalArgumentException("管理员云枢连接配置不完整，无法初始化元数据");
        }
        return new CloudPivotMetadataSnapshot(
            List.of(
                new CloudPivotMetadataSnapshot.AppMetadata("metadata_app", "元数据应用", "云枢元数据初始化占位应用"),
                new CloudPivotMetadataSnapshot.AppMetadata("workflow_metadata_app", "流程元数据应用", "云枢流程能力初始化占位应用")
            ),
            List.of(
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_object", "元数据对象", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_detail", "元数据明细", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("metadata_app", "metadata_relation", "元数据关联", "data", "low"),
                new CloudPivotMetadataSnapshot.EntityMetadata("workflow_metadata_app", "metadata_form", "元数据表单", "data", "medium"),
                new CloudPivotMetadataSnapshot.EntityMetadata("workflow_metadata_app", "metadata_attachment", "元数据附件", "attachment", "medium")
            )
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
