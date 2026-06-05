package com.cpclaw.metadata;

import com.cpclaw.common.api.ApiResponse;
import com.cpclaw.metadata.dto.MetadataAppSummary;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.dto.MetadataSyncResponse;
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

    public MetadataController(MetadataService metadataService, MetadataSearchService metadataSearchService) {
        this.metadataService = metadataService;
        this.metadataSearchService = metadataSearchService;
    }

    @GetMapping("/apps")
    public ApiResponse<List<MetadataAppSummary>> listApps() {
        return ApiResponse.ok(metadataService.listApps());
    }

    @GetMapping("/search")
    public ApiResponse<List<MetadataSearchResult>> search(@RequestParam String query) {
        return ApiResponse.ok(metadataSearchService.searchLocalMetadata(query));
    }

    @PostMapping("/sync")
    public ApiResponse<MetadataSyncResponse> sync() {
        return ApiResponse.ok(metadataService.syncSampleMetadata());
    }
}
