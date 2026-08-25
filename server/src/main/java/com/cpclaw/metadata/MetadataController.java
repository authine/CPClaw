package com.cpclaw.metadata;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataModelResponse;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.dto.MetadataSyncLogOverviewResponse;
import com.cpclaw.metadata.dto.WorkflowContractProbeResponse;
import com.cpclaw.metadata.graph.MetadataGraphService;
import com.cpclaw.metadata.graph.dto.GraphifyExportResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphNeighborhoodResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphOverviewResponse;
import com.cpclaw.search.MetadataSearchService;
import com.cpclaw.workflow.WorkflowCenterService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final MetadataService metadataService;
    private final MetadataSearchService metadataSearchService;
    private final MetadataGraphService metadataGraphService;
    private final WorkflowCenterService workflowCenterService;
    private final MetadataSyncLogService metadataSyncLogService;

    public MetadataController(
        MetadataService metadataService,
        MetadataSearchService metadataSearchService,
        MetadataGraphService metadataGraphService,
        WorkflowCenterService workflowCenterService,
        MetadataSyncLogService metadataSyncLogService
    ) {
        this.metadataService = metadataService;
        this.metadataSearchService = metadataSearchService;
        this.metadataGraphService = metadataGraphService;
        this.workflowCenterService = workflowCenterService;
        this.metadataSyncLogService = metadataSyncLogService;
    }

    @GetMapping("/apps")
    public ApiResponse<List<MetadataAppSummary>> listApps() {
        return ApiResponse.ok(metadataService.listApps());
    }

    @GetMapping("/model")
    public ApiResponse<MetadataModelResponse> model() {
        return ApiResponse.ok(metadataService.metadataModel());
    }

    @GetMapping("/search")
    public ApiResponse<List<MetadataSearchResult>> search(@RequestParam String query) {
        return ApiResponse.ok(metadataSearchService.searchLocalMetadata(query));
    }

    @PostMapping("/sync")
    public ApiResponse<MetadataSyncResponse> sync() {
        String logId = metadataSyncLogService.start();
        try {
            MetadataSyncResponse result = metadataService.initializeCloudPivotMetadata();
            metadataSyncLogService.succeed(logId, result);
            return ApiResponse.ok(result);
        } catch (Exception exception) {
            metadataSyncLogService.fail(logId, exception);
            throw exception;
        }
    }

    @GetMapping("/sync-logs")
    public ApiResponse<MetadataSyncLogOverviewResponse> syncLogs() {
        return ApiResponse.ok(metadataSyncLogService.overview());
    }

    @PostMapping("/workflow/probe")
    public ApiResponse<WorkflowContractProbeResponse> probeWorkflowReadContracts() {
        var result = workflowCenterService.probeReadContracts();
        var contracts = result.contracts().stream()
            .map(item -> new WorkflowContractProbeResponse.Contract(item.apiCode(), item.verified(), item.method(), item.path(), item.responseShape(), item.error()))
            .toList();
        return ApiResponse.ok(new WorkflowContractProbeResponse(
            (int) contracts.stream().filter(WorkflowContractProbeResponse.Contract::verified).count(),
            contracts.size(), result.verifiedAt().toString(), contracts
        ));
    }

    @GetMapping("/graph/overview")
    public ApiResponse<MetadataGraphOverviewResponse> graphOverview() {
        return ApiResponse.ok(metadataGraphService.overview());
    }

    @GetMapping("/graph/neighborhood")
    public ApiResponse<MetadataGraphNeighborhoodResponse> graphNeighborhood(
        @RequestParam(required = false) String nodeId,
        @RequestParam(required = false) String objectType,
        @RequestParam(required = false) String objectId,
        @RequestParam(defaultValue = "1") int depth,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.ok(metadataGraphService.neighborhood(nodeId, objectType, objectId, depth, limit));
    }

    @GetMapping("/graph/export")
    public GraphifyExportResponse graphExport() {
        return metadataGraphService.graphifyExport();
    }

    @PostMapping("/graph/rebuild")
    public ApiResponse<MetadataGraphOverviewResponse> rebuildGraph() {
        metadataGraphService.rebuildCurrentMetadata();
        return ApiResponse.ok(metadataGraphService.overview());
    }
}
