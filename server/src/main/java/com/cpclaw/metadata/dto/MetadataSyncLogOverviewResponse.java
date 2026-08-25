package com.cpclaw.metadata.dto;

import java.util.List;

public record MetadataSyncLogOverviewResponse(String latestSuccessfulAt, List<MetadataSyncLogResponse> items) {
}
