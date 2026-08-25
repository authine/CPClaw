package com.cpclaw.settings;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.settings.dto.ConnectionTestResponse;
import com.cpclaw.settings.dto.ModelConfigResponse;
import com.cpclaw.settings.dto.ModelConnectionTestResponse;
import com.cpclaw.settings.dto.SaveAdminSettingsRequest;
import com.cpclaw.settings.dto.SaveModelConfigRequest;
import com.cpclaw.settings.dto.SaveUserSettingsRequest;
import com.cpclaw.settings.dto.SettingsResponse;
import com.cpclaw.memory.MemoryService;
import com.cpclaw.memory.dto.MemoryEntryDto;
import com.cpclaw.memory.dto.MemorySettingsResponse;
import com.cpclaw.memory.dto.SaveMemoryEntryRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settingsService;
    private final MemoryService memoryService;

    public SettingsController(SettingsService settingsService, MemoryService memoryService) {
        this.settingsService = settingsService;
        this.memoryService = memoryService;
    }

    @GetMapping
    public ApiResponse<SettingsResponse> getSettings() {
        return ApiResponse.ok(settingsService.getSettings());
    }

    @GetMapping("/memory")
    public ApiResponse<MemorySettingsResponse> getMemorySettings() {
        return ApiResponse.ok(memoryService.settingsForCurrentPrincipal());
    }

    @PostMapping("/memory/personal")
    public ApiResponse<MemoryEntryDto> savePersonalMemory(@RequestBody SaveMemoryEntryRequest request) {
        return ApiResponse.ok(memoryService.savePersonal(request));
    }

    @DeleteMapping("/memory/personal/{id}")
    public ApiResponse<Void> deletePersonalMemory(@PathVariable String id) {
        memoryService.deletePersonal(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/memory/global")
    public ApiResponse<MemoryEntryDto> saveGlobalMemory(@RequestBody SaveMemoryEntryRequest request) {
        return ApiResponse.ok(memoryService.saveGlobal(request));
    }

    @DeleteMapping("/memory/global/{id}")
    public ApiResponse<Void> deleteGlobalMemory(@PathVariable String id) {
        memoryService.deleteGlobal(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/models")
    public ApiResponse<List<ModelConfigResponse>> listModels() {
        return ApiResponse.ok(settingsService.listModelSummaries());
    }

    @PostMapping("/models/{modelConfigId}/test")
    public ApiResponse<ModelConnectionTestResponse> testModel(@PathVariable("modelConfigId") String modelConfigId) {
        return ApiResponse.ok(settingsService.testModel(modelConfigId));
    }

    @PostMapping("/models/test")
    public ApiResponse<ModelConnectionTestResponse> testUnsavedModel(@RequestBody SaveModelConfigRequest request) {
        return ApiResponse.ok(settingsService.testUnsavedModel(request));
    }

    @PostMapping("/models")
    public ApiResponse<ModelConfigResponse> saveModel(@RequestBody SaveModelConfigRequest request) {
        return ApiResponse.ok(settingsService.saveModel(request));
    }

    @PutMapping("/models/{modelConfigId}")
    public ApiResponse<ModelConfigResponse> updateModel(
        @PathVariable("modelConfigId") String modelConfigId,
        @RequestBody SaveModelConfigRequest request
    ) {
        return ApiResponse.ok(settingsService.updateModel(modelConfigId, request));
    }

    @DeleteMapping("/models/{modelConfigId}")
    public ApiResponse<Void> deleteModel(@PathVariable("modelConfigId") String modelConfigId) {
        settingsService.deleteModel(modelConfigId);
        return ApiResponse.ok(null);
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
    public ApiResponse<ConnectionTestResponse> testUserCloudPivotConnection(@RequestBody(required = false) SaveUserSettingsRequest request) {
        return ApiResponse.ok(settingsService.testUserCloudPivotConnection(request));
    }

    @PostMapping("/metadata-cloudpivot/test")
    public ApiResponse<ConnectionTestResponse> testAdminCloudPivotConnection(@RequestBody(required = false) SaveAdminSettingsRequest request) {
        return ApiResponse.ok(settingsService.testAdminCloudPivotConnection(request));
    }
}
