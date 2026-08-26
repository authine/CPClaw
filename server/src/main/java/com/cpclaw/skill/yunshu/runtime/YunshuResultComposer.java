package com.cpclaw.skill.yunshu.runtime;

import com.cpclaw.cloudpivot.CloudPivotRecordDisplayPolicy;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Channel-neutral result shaping for ordinary Yunshu reads. It only uses
 * metadata and provider output; domain-specific labels and report sections are
 * supplied by template plugins.
 */
public interface YunshuResultComposer {
    Map<String, Object> composeQuery(MetadataSearchResult match,
            CloudPivotRuntimeQueryResult result,
            CloudPivotRecordDisplayPolicy displayPolicy,
            boolean analysisMode);

    default String answer(MetadataSearchResult match, CloudPivotRuntimeQueryResult result, int returned) {
        String name = match == null || match.name() == null || match.name().isBlank() ? "数据对象" : match.name();
        return "已查询“" + name + "”，共 " + result.total() + " 条记录，本次返回 " + returned + " 条。";
    }

    static Map<String, Object> safeMetadata(MetadataSearchResult match) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("objectType", match == null || match.objectType() == null ? "" : match.objectType());
        metadata.put("objectName", match == null || match.name() == null ? "" : match.name());
        metadata.put("objectCode", match == null || match.code() == null ? "" : match.code());
        return metadata;
    }
}
