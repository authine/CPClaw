package com.cpclaw.metadata;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataModelResponse;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
import com.cpclaw.metadata.graph.MetadataGraphService;
import com.cpclaw.metadata.graph.dto.GraphifyExportResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphNeighborhoodResponse;
import com.cpclaw.metadata.graph.dto.MetadataGraphOverviewResponse;
import com.cpclaw.search.MetadataSearchService;
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

    public MetadataController(
        MetadataService metadataService,
        MetadataSearchService metadataSearchService,
        MetadataGraphService metadataGraphService
    ) {
        this.metadataService = metadataService;
        this.metadataSearchService = metadataSearchService;
        this.metadataGraphService = metadataGraphService;
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
        return ApiResponse.ok(metadataService.initializeCloudPivotMetadata());
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
