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
 * Generic Skill report entry point. The default engine is metadata-driven and
 * can analyze any executable business object with synchronized fields and
 * relations. Published report skills are optional overlays for custom KPIs,
 * wording or layout; they are never a prerequisite for a report.
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
        // Generic analysis is a built-in Yunshu capability. A report template
        // may enrich wording, metrics or layout, but it must not decide
        // whether an otherwise valid metadata-backed analysis can run.
        return "analyze_data".equals(intent) || "summarize_data".equals(intent);
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

