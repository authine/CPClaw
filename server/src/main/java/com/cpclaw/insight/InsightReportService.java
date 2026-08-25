package com.cpclaw.insight;

import com.cpclaw.skill.yunshu.YunshuInsightReportService;
import com.cpclaw.skill.yunshu.YunshuVisualizationPlanner;
import com.cpclaw.model.ModelGateway;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.insight.CloudPivotInsightDataReader;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Compatibility facade; report planning and business semantics live in Skill. */
public class InsightReportService extends YunshuInsightReportService {
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
    }
}
