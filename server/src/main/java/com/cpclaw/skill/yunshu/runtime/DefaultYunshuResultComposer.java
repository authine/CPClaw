package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class DefaultYunshuResultComposer implements YunshuResultComposer {
    @Override
    public Map<String, Object> composeQuery(MetadataSearchResult match,
            CloudPivotRuntimeQueryResult result,
            CloudPivotRecordDisplayPolicy displayPolicy,
            boolean analysisMode) {
        CloudPivotRecordDisplayPolicy.DisplayContext context = displayPolicy.context(result.schemaCode());
        List<Map<String, Object>> records = result.records().stream()
            .map(record -> Map.<String, Object>of("summary", displayPolicy.summarize(context, record)))
            .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cardType", analysisMode ? "analysis-data" : "data-table");
        data.put("metadata", YunshuResultComposer.safeMetadata(match));
        data.put("entityName", match == null || match.name() == null ? "" : match.name());
        data.put("total", result.total());
        data.put("returned", records.size());
        data.put("records", records);
        return data;
    }
}
