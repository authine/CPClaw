package com.cpclaw.skill.yunshu;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.insight.InsightDataAccess;
import com.cpclaw.insight.InsightExecutionResult;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.insight.template.ReportSkillCatalog;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.insight.CloudPivotInsightDataReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;
import com.cpclaw.skill.yunshu.template.YunshuScenarioInsightReportTemplate;

/**
 * Generic Skill report entry point. Scenario report layouts and KPI rules are
 * supplied by published template plugins; the framework has no default report
 * vocabulary. When no template claims a request, the runtime model path is
 * used instead of guessing a business report.
 */
@Service
public class YunshuInsightReportService {
    private YunshuScenarioInsightReportTemplate template;
    @Autowired
    public YunshuInsightReportService() { }

    public YunshuInsightReportService(
        CloudPivotEntityRepository ignoredEntityRepository,
        CloudPivotDataItemRepository ignoredDataItemRepository,
        CloudPivotEntityRelationRepository ignoredRelationRepository,
        CloudPivotInsightDataReader ignoredDataReader,
        ModelGateway ignoredModelGateway,
        ObjectMapper ignoredObjectMapper,
        Object ignoredVisualizationPlanner,
        ReportSkillCatalog ignoredReportSkillCatalog
    ) { }

    public YunshuInsightReportService(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotInsightDataReader dataReader,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        Object visualizationPlanner
    ) {
        this(entityRepository, dataItemRepository, relationRepository, dataReader, modelGateway, objectMapper, visualizationPlanner, null);
    }

    @Autowired(required = false)
    public void setTemplate(YunshuScenarioInsightReportTemplate template) {
        this.template = template;
    }

    public boolean supports(String question, String intent) {
        return template != null && template.supports(question, intent);
    }

    public InsightExecutionResult execute(
        MetadataSearchResult primaryMatch,
        String question,
        String modelConfigId,
        boolean thinkingEnabled,
        AgentProgressListener progressListener
    ) {
        if (template != null) return template.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener);
        String name = primaryMatch == null || primaryMatch.name() == null ? "数据对象" : primaryMatch.name();
        InsightReportDto report = new InsightReportDto(
            "通用数据分析", name, "", "当前授权范围", "medium",
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of("未安装专项分析模板，仅返回通用结果"));
        return new InsightExecutionResult(
            "当前未安装适用于该对象的专项分析模板，请选择或发布模板后重试。",
            report, name, primaryMatch == null ? "" : primaryMatch.code(), 0L, List.of(), "通用分析模板未匹配", Map.of("templateMatched", false));
    }

    public InsightExecutionResult execute(
        MetadataSearchResult primaryMatch,
        String question,
        String modelConfigId,
        boolean thinkingEnabled,
        AgentProgressListener progressListener,
        InsightDataAccess access
    ) {
        if (template != null) return template.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener, access);
        return execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener);
    }
}
