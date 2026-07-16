package com.cpclaw.insight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.model.IntentPlanningResult;
import com.cpclaw.model.ModelGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InsightReportServiceTests {

    private final CloudPivotEntityRepository entityRepository = mock(CloudPivotEntityRepository.class);
    private final CloudPivotDataItemRepository dataItemRepository = mock(CloudPivotDataItemRepository.class);
    private final CloudPivotEntityRelationRepository relationRepository = mock(CloudPivotEntityRelationRepository.class);
    private final CloudPivotInsightDataReader dataReader = mock(CloudPivotInsightDataReader.class);
    private final ModelGateway modelGateway = mock(ModelGateway.class);
    private InsightReportService service;
    private CloudPivotEntity opportunity;
    private CloudPivotEntity lead;
    private CloudPivotEntity contract;

    @BeforeEach
    void setUp() {
        service = new InsightReportService(
            entityRepository,
            dataItemRepository,
            relationRepository,
            dataReader,
            modelGateway,
            new ObjectMapper(),
            new InsightVisualizationPlanner(new ObjectMapper())
        );
        opportunity = entity("opp-id", "crm-app", "int_bu_oppor", "商机");
        lead = entity("lead-id", "crm-app", "sql_line", "私海线索");
        contract = entity("contract-id", "crm-app", "contract_quotation", "销售合同");

        when(entityRepository.findById("opp-id")).thenReturn(Optional.of(opportunity));
        when(entityRepository.findById("lead-id")).thenReturn(Optional.of(lead));
        when(entityRepository.findById("contract-id")).thenReturn(Optional.of(contract));
        when(entityRepository.findByEntityCodeIgnoreCase("int_bu_oppor")).thenReturn(List.of(opportunity));

        CloudPivotDataItem oppLead = field("opp-lead", "opp-id", "clues_id", "线索", "RELEVANCE_FORM");
        CloudPivotDataItem contractOpp = field("contract-opp", "contract-id", "opportunity_id", "商机", "RELEVANCE_FORM");
        when(dataItemRepository.findById("opp-lead")).thenReturn(Optional.of(oppLead));
        when(dataItemRepository.findById("contract-opp")).thenReturn(Optional.of(contractOpp));
        when(dataItemRepository.findByEntityId("opp-id")).thenReturn(List.of(
            oppLead,
            field("opp-created", "opp-id", "createdTime", "创建时间", "DATETIME"),
            field("opp-owner", "opp-id", "owner", "商机跟进人", "STAFF_SELECTOR"),
            field("opp-stage", "opp-id", "sales_stage", "销售阶段", "TEXT"),
            field("opp-amount", "opp-id", "pre_sign_dam_yuan", "预计签约金额", "NUMBER"),
            field("opp-sign", "opp-id", "sign_time", "实际签约时间", "DATETIME")
        ));
        when(dataItemRepository.findByEntityId("lead-id")).thenReturn(List.of(
            field("lead-created", "lead-id", "createdTime", "创建时间", "DATETIME"),
            field("lead-owner", "lead-id", "owner", "拥有者", "STAFF_SELECTOR"),
            field("lead-status", "lead-id", "cue_status", "线索状态", "TEXT")
        ));
        when(dataItemRepository.findByEntityId("contract-id")).thenReturn(List.of(
            contractOpp,
            field("contract-created", "contract-id", "createdTime", "创建时间", "DATETIME"),
            field("contract-owner", "contract-id", "owner", "负责人", "STAFF_SELECTOR"),
            field("contract-amount", "contract-id", "amount", "合同金额", "NUMBER")
        ));

        CloudPivotEntityRelation leadRelation = relation("rel-lead", "opp-id", "opp-lead", "lead-id", "商机关联线索");
        CloudPivotEntityRelation contractRelation = relation("rel-contract", "contract-id", "contract-opp", "opp-id", "合同关联商机");
        when(relationRepository.findBySourceEntityId("opp-id")).thenReturn(List.of(leadRelation));
        when(relationRepository.findByTargetEntityId("opp-id")).thenReturn(List.of(contractRelation));

        when(modelGateway.planIntent(anyString(), anyMap(), anyBoolean())).thenReturn(Optional.of(IntentPlanningResult.empty()));
        when(modelGateway.analyzeRecordsStream(
            anyString(), anyString(), anyString(), anyLong(), anyList(), anyBoolean(), anyMap(), any(Consumer.class)
        )).thenReturn(Optional.empty());
        when(dataReader.configuredUsername()).thenReturn("18124691161");
        CloudPivotRuntimeQueryResult opportunityResult = result("int_bu_oppor", List.of(
            record("opp-1", Map.of("createdTime", "2024-01-10", "owner", Map.of("name", "张三"), "sales_stage", "方案确认", "pre_sign_dam_yuan", 800000, "sign_time", "2024-05-20", "clues_id", Map.of("id", "lead-1"))),
            record("opp-2", Map.of("createdTime", "2024-03-12", "owner", Map.of("name", "李四"), "sales_stage", "需求沟通", "pre_sign_dam_yuan", 400000, "clues_id", Map.of("id", "lead-2"))),
            record("opp-3", Map.of("createdTime", "2024-09-01", "owner", Map.of("name", "王五"), "sales_stage", "初步接洽", "pre_sign_dam_yuan", 100000)),
            recordWithNullDate("opp-4"),
            recordWithTopLevelDate("opp-5")
        ));
        CloudPivotRuntimeQueryResult leadResult = result("sql_line", List.of(
            record("lead-1", Map.of("createdTime", "2024-01-02", "owner", Map.of("name", "张三"), "cue_status", "已转商机")),
            record("lead-2", Map.of("createdTime", "2024-02-02", "owner", Map.of("name", "李四"), "cue_status", "已转商机")),
            record("lead-3", Map.of("createdTime", "2024-04-02", "owner", Map.of("name", "李四"), "cue_status", "跟进中")),
            record("lead-4", Map.of("createdTime", "2024-08-02", "owner", Map.of("name", "王五"), "cue_status", "跟进中"))
        ));
        CloudPivotRuntimeQueryResult contractResult = result("contract_quotation", List.of(
            record("contract-1", Map.of("createdTime", "2024-05-22", "owner", Map.of("name", "张三"), "amount", 800000, "opportunity_id", Map.of("id", "opp-1")))
        ));
        CloudPivotRuntimeQueryResult opportunityFiltered = result("int_bu_oppor", List.of(
            opportunityResult.records().get(0),
            opportunityResult.records().get(1),
            opportunityResult.records().get(4)
        ));
        CloudPivotRuntimeQueryResult leadFiltered = result("sql_line", leadResult.records().subList(0, 3));
        when(dataReader.query(opportunity, false)).thenReturn(opportunityResult);
        when(dataReader.query(opportunity, true)).thenReturn(opportunityResult);
        when(dataReader.query(lead, false)).thenReturn(leadResult);
        when(dataReader.query(lead, true)).thenReturn(leadResult);
        when(dataReader.query(contract, false)).thenReturn(contractResult);
        when(dataReader.query(contract, true)).thenReturn(contractResult);
        when(dataReader.query(eq(opportunity), eq(true), eq(20_000), anyList())).thenReturn(opportunityFiltered);
        when(dataReader.query(eq(lead), eq(false), eq(20_000), anyList())).thenReturn(leadFiltered);
        when(dataReader.query(eq(lead), eq(true), eq(20_000), anyList())).thenReturn(leadFiltered);
        when(dataReader.query(eq(contract), eq(false), eq(20_000), anyList())).thenReturn(contractResult);
        when(dataReader.query(eq(contract), eq(true), eq(20_000), anyList())).thenReturn(contractResult);
    }

    @Test
    void buildsMetadataDrivenMultiEntityReport() {
        InsightExecutionResult result = service.execute(
            match(),
            "2024年上半年商机情况怎么样？",
            "model-id",
            true,
            AgentProgressListener.NOOP
        );

        InsightReportDto report = result.report();
        assertEquals(3, result.primaryCount());
        assertTrue(report.kpis().stream().anyMatch(kpi -> "私海线索数".equals(kpi.label()) && "3".equals(kpi.value())));
        assertTrue(report.kpis().stream().anyMatch(kpi -> "商机数".equals(kpi.label()) && "3".equals(kpi.value())));
        assertTrue(report.kpis().stream().anyMatch(kpi -> "金额合计".equals(kpi.label()) && kpi.value().contains("140")));
        assertTrue(report.charts().stream().anyMatch(chart -> "business-flow".equals(chart.id()) && "bar".equals(chart.type())));
        assertTrue(report.charts().stream().anyMatch(chart -> "stage-distribution".equals(chart.id())
            && "funnel".equals(chart.type())
            && chart.labels().equals(List.of("需求沟通", "方案确认"))
            && chart.description().contains("不代表")));
        assertTrue(report.sections().getFirst().findings().stream().anyMatch(value -> value.contains("其中 2 条通过关联字段确认")));
        assertTrue(report.sections().getFirst().findings().stream().anyMatch(value -> value.contains("已有 1 条销售合同")));
        assertTrue(report.relatedQuestions().stream().anyMatch(question -> question.contains("没有转化")));
        assertTrue(result.answer().contains("关键发现"));
    }

    @Test
    void doesNotReturnAllUsersWhenPersonalIdentityCannotBeMapped() {
        InsightExecutionResult result = service.execute(
            match(),
            "2024年上半年我的商机情况怎么样？",
            "model-id",
            true,
            AgentProgressListener.NOOP
        );

        assertEquals(0, result.primaryCount());
        assertFalse(result.report().warnings().isEmpty());
        assertTrue(result.report().warnings().stream().anyMatch(value -> value.contains("未匹配当前账号")));
        assertTrue(result.report().sections().get(1).findings().stream().anyMatch(value -> value.contains("数据口径提示")));
    }

    @Test
    void fallsBackToCompleteListWhenCloudPivotRejectsPeriodFilters() {
        CloudPivotRuntimeQueryResult allLeads = result("sql_line", List.of(
            record("lead-1", Map.of("createdTime", "2024-01-02", "owner", Map.of("name", "张三"), "cue_status", "已转商机")),
            record("lead-2", Map.of("createdTime", "2024-02-02", "owner", Map.of("name", "李四"), "cue_status", "已转商机")),
            record("lead-3", Map.of("createdTime", "2024-04-02", "owner", Map.of("name", "李四"), "cue_status", "跟进中")),
            record("lead-4", Map.of("createdTime", "2024-08-02", "owner", Map.of("name", "王五"), "cue_status", "跟进中"))
        ));
        when(dataReader.query(eq(lead), eq(false), eq(20_000), anyList()))
            .thenThrow(new IllegalStateException("CloudPivot does not support this date operator"));
        when(dataReader.query(eq(lead), eq(false), eq(20_000), eq(List.of()))).thenReturn(allLeads);

        InsightExecutionResult result = service.execute(
            match(),
            "2024年上半年商机情况怎么样？",
            "model-id",
            true,
            AgentProgressListener.NOOP
        );

        assertTrue(result.report().kpis().stream().anyMatch(kpi -> "私海线索数".equals(kpi.label()) && "3".equals(kpi.value())));
        assertTrue(result.report().warnings().stream().anyMatch(value -> value.contains("读取完整列表并按日期字段计算")));
        verify(dataReader).query(eq(lead), eq(false), eq(20_000), eq(List.of()));
    }

    private MetadataSearchResult match() {
        return new MetadataSearchResult("entity", "opp-id", "商机", "int_bu_oppor", "CRM/商机", "low", "test");
    }

    private CloudPivotEntity entity(String id, String appId, String code, String name) {
        CloudPivotEntity entity = new CloudPivotEntity();
        entity.setId(id);
        entity.setAppId(appId);
        entity.setEntityCode(code);
        entity.setName(name);
        return entity;
    }

    private CloudPivotDataItem field(String id, String entityId, String code, String name, String type) {
        CloudPivotDataItem field = new CloudPivotDataItem();
        field.setId(id);
        field.setEntityId(entityId);
        field.setDataItemCode(code);
        field.setName(name);
        field.setDataType(type);
        return field;
    }

    private CloudPivotEntityRelation relation(String id, String source, String fieldId, String target, String name) {
        CloudPivotEntityRelation relation = new CloudPivotEntityRelation();
        relation.setId(id);
        relation.setAppId("crm-app");
        relation.setSourceEntityId(source);
        relation.setSourceDataItemId(fieldId);
        relation.setTargetEntityId(target);
        relation.setRelationName(name);
        return relation;
    }

    private CloudPivotRuntimeQueryResult result(String schemaCode, List<Map<String, Object>> records) {
        return new CloudPivotRuntimeQueryResult(schemaCode, records.size(), records, "/api/runtime/query/listSkipQueryListV2");
    }

    private Map<String, Object> record(String id, Map<String, Object> data) {
        return Map.of("id", id, "data", data);
    }

    private Map<String, Object> recordWithNullDate(String id) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("createdTime", null);
        data.put("owner", Map.of("name", "赵六"));
        data.put("sales_stage", "需求沟通");
        return Map.of("id", id, "data", data);
    }

    private Map<String, Object> recordWithTopLevelDate(String id) {
        return Map.of(
            "id", id,
            "createdAt", "2024-06-18",
            "data", Map.of("owner", Map.of("name", "钱七"), "sales_stage", "方案确认", "pre_sign_dam_yuan", 200000)
        );
    }
}
