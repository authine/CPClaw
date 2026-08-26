package com.cpclaw.insight;

import com.cpclaw.skill.yunshu.YunshuInsightReportService;
import com.cpclaw.skill.yunshu.YunshuVisualizationPlanner;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.insight.CloudPivotInsightDataReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cpclaw.skill.yunshu.template.YunshuScenarioInsightReportTemplate;
import com.cpclaw.skill.yunshu.template.YunshuScenarioVisualizationTemplate;
import com.cpclaw.insight.template.ReportSkillCatalog;
import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import java.util.Objects;
import java.util.function.Consumer;

/** Compatibility facade; report planning and business semantics live in Skill. */
public class InsightReportService extends YunshuInsightReportService {
    private final YunshuScenarioInsightReportTemplate scenarioTemplate;
    public InsightReportService(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotInsightDataReader dataReader,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        YunshuVisualizationPlanner visualizationPlanner
    ) {
        super(entityRepository, dataItemRepository, relationRepository, dataReader, modelGateway, objectMapper, visualizationPlanner);
        this.scenarioTemplate = null;
    }

    public InsightReportService(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotInsightDataReader dataReader,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        InsightVisualizationPlanner visualizationPlanner
    ) {
        super(entityRepository, dataItemRepository, relationRepository, dataReader, modelGateway, objectMapper, visualizationPlanner);
        this.scenarioTemplate = null;
    }

    public InsightReportService(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotInsightDataReader dataReader,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        YunshuVisualizationPlanner visualizationPlanner,
        YunshuScenarioInsightReportTemplate scenarioTemplate
    ) {
        super(entityRepository, dataItemRepository, relationRepository, dataReader, modelGateway, objectMapper, visualizationPlanner);
        this.scenarioTemplate = scenarioTemplate;
    }

    @Override
    public boolean supports(String question, String intent) {
        return scenarioTemplate != null && scenarioTemplate.supports(question, intent);
    }

    @Override
    public InsightExecutionResult execute(MetadataSearchResult primaryMatch, String question, String modelConfigId,
            boolean thinkingEnabled, AgentProgressListener progressListener) {
        return scenarioTemplate != null ? scenarioTemplate.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener) : super.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener);
    }

    @Override
    public InsightExecutionResult execute(MetadataSearchResult primaryMatch, String question, String modelConfigId,
            boolean thinkingEnabled, AgentProgressListener progressListener, InsightDataAccess access) {
        return scenarioTemplate != null ? scenarioTemplate.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener, access) : super.execute(primaryMatch, question, modelConfigId, thinkingEnabled, progressListener, access);
    }
}
