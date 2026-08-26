package com.cpclaw.skill.yunshu.template;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.insight.CloudPivotInsightDataReader;
import com.cpclaw.insight.template.ReportSkillCatalog;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.model.ModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Configuration;

/** Explicit installation point for the optional scenario template plugin. */
@Configuration
@ConditionalOnProperty(prefix = "cpclaw.templates.scenario", name = "enabled", havingValue = "true")
public class YunshuScenarioTemplateConfiguration {
    @Bean
    @Primary
    YunshuScenarioTemplateSemantics yunshuScenarioTemplateSemantics() {
        return new YunshuScenarioTemplateSemantics();
    }

    @Bean
    YunshuScenarioVisualizationTemplate yunshuScenarioVisualizationTemplate(ObjectMapper objectMapper) {
        return new YunshuScenarioVisualizationTemplate(objectMapper);
    }

    @Bean
    YunshuScenarioInsightReportTemplate yunshuScenarioInsightReportTemplate(
            CloudPivotEntityRepository entities,
            CloudPivotDataItemRepository items,
            CloudPivotEntityRelationRepository relations,
            CloudPivotInsightDataReader reader,
            ModelGateway modelGateway,
            ObjectMapper objectMapper,
            YunshuScenarioVisualizationTemplate visualization,
            ReportSkillCatalog catalog) {
        return new YunshuScenarioInsightReportTemplate(entities, items, relations, reader, modelGateway, objectMapper, visualization, catalog);
    }
}
