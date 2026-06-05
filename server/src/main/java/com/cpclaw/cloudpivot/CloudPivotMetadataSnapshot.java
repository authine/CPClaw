package com.cpclaw.cloudpivot;

import java.util.List;

public record CloudPivotMetadataSnapshot(
    List<AppMetadata> apps,
    List<EntityMetadata> entities
) {

    public record AppMetadata(String code, String name, String description) {
    }

    public record EntityMetadata(String appCode, String code, String name, String type, String riskLevel) {
    }
}
