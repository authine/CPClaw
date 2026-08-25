package com.cpclaw.cloudpivot;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.skill.yunshu.YunshuRuntimeService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compatibility boundary for existing callers. CloudPivot business semantics
 * are implemented by the Yunshu Skill, not by the framework connector package.
 */
@Service
public class CloudPivotRuntimeService {
    private final YunshuRuntimeService delegate;

    public CloudPivotRuntimeService(YunshuRuntimeService delegate) {
        this.delegate = delegate;
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled) {
        return delegate.query(match, userQuestion, modelConfigId, thinkingEnabled);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters) {
        return delegate.query(match, userQuestion, modelConfigId, thinkingEnabled, filters);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes) {
        return delegate.query(match, userQuestion, modelConfigId, thinkingEnabled, filters, metricFieldCodes);
    }

    public CloudPivotQueryAnswer query(MetadataSearchResult match, String userQuestion, String modelConfigId, boolean thinkingEnabled, List<RuntimeQueryFilter> filters, List<String> metricFieldCodes, Map<String, Object> reasoningContext, AgentProgressListener progressListener) {
        return delegate.query(match, userQuestion, modelConfigId, thinkingEnabled, filters, metricFieldCodes, reasoningContext, progressListener);
    }

    public record RuntimeRecordTarget(String schemaCode, String bizObjectId) { }
}
