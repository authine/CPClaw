package com.cpclaw.mcp;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.mcp.dto.McpCloudPivotBindingRequest;
import com.cpclaw.mcp.dto.McpCloudPivotInstallationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/mcp/cloudpivot")
public class McpSettingsController {
    private final McpInstallationService installationService;
    public McpSettingsController(McpInstallationService installationService) { this.installationService = installationService; }

    @GetMapping
    public ApiResponse<McpCloudPivotInstallationResponse> get(@RequestParam(value = "installationId", required = false) String installationId) {
        return ApiResponse.ok(installationService.getOrCreate(installationId));
    }

    @PostMapping("/enable")
    public ApiResponse<McpCloudPivotInstallationResponse> enable(@RequestBody McpCloudPivotBindingRequest request) {
        return ApiResponse.ok(installationService.enable(request));
    }

    @PostMapping("/disable")
    public ApiResponse<McpCloudPivotInstallationResponse> disable(@RequestBody McpCloudPivotBindingRequest request) {
        return ApiResponse.ok(installationService.disable(request == null ? null : request.installationId()));
    }
}
