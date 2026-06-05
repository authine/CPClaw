package com.cpclaw.settings.dto;

import java.util.List;

public record SettingsResponse(
    UserCloudPivotSettings userCloudPivot,
    AdminMetadataSettings adminMetadata,
    List<ModelConfigResponse> models
) {
}
