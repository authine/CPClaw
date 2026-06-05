package com.cpclaw.settings;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.settings.dto.ConnectionTestResponse;
import com.cpclaw.settings.dto.ModelConfigResponse;
import com.cpclaw.settings.dto.SaveAdminSettingsRequest;
import com.cpclaw.settings.dto.SaveUserSettingsRequest;
import com.cpclaw.settings.dto.SettingsResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public ApiResponse<SettingsResponse> getSettings() {
        return ApiResponse.ok(settingsService.getSettings());
    }

    @GetMapping("/models")
    public ApiResponse<List<ModelConfigResponse>> listModels() {
        return ApiResponse.ok(settingsService.listModelSummaries());
    }

    @PostMapping("/user")
    public ApiResponse<SettingsResponse> saveUserSettings(@RequestBody SaveUserSettingsRequest request) {
        return ApiResponse.ok(settingsService.saveUserSettings(request));
    }

    @PostMapping("/admin")
    public ApiResponse<SettingsResponse> saveAdminSettings(@RequestBody SaveAdminSettingsRequest request) {
        return ApiResponse.ok(settingsService.saveAdminSettings(request));
    }

    @PostMapping("/cloudpivot/test")
    public ApiResponse<ConnectionTestResponse> testUserCloudPivotConnection() {
        return ApiResponse.ok(settingsService.testUserCloudPivotConnection());
    }

    @PostMapping("/metadata-cloudpivot/test")
    public ApiResponse<ConnectionTestResponse> testAdminCloudPivotConnection() {
        return ApiResponse.ok(settingsService.testAdminCloudPivotConnection());
    }
}
