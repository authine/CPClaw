package com.cpclaw.insight;

import com.cpclaw.agent.AgentProgressListener;
import com.cpclaw.agent.AnswerStreamSupport;
import com.cpclaw.cloudpivot.CloudPivotRuntimeQueryResult;
import com.cpclaw.cloudpivot.RuntimeQueryFilter;
import com.cpclaw.insight.dto.InsightReportDto;
import com.cpclaw.insight.dto.InsightReportDto.Chart;
import com.cpclaw.insight.dto.InsightReportDto.Kpi;
import com.cpclaw.insight.dto.InsightReportDto.Section;
import com.cpclaw.metadata.dto.MetadataSearchResult;
import com.cpclaw.metadata.entity.CloudPivotDataItem;
import com.cpclaw.metadata.entity.CloudPivotEntity;
import com.cpclaw.metadata.entity.CloudPivotEntityRelation;
import com.cpclaw.metadata.repository.CloudPivotDataItemRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRelationRepository;
import com.cpclaw.metadata.repository.CloudPivotEntityRepository;
import com.cpclaw.model.IntentPlanningResult;
import com.cpclaw.model.ModelGateway;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InsightReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int LOCAL_PERIOD_FILTER_RECORD_LIMIT = 20_000;
    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})\\s*年");
    private static final List<String> REPORT_WORDS = List.of(
        "情况怎么样", "情况如何", "整体情况", "分析", "洞察", "诊断", "报告", "概览", "健康度", "趋势", "经营情况"
    );
    private static final List<String> LIFECYCLE_WORDS = List.of(
        "线索", "商机", "机会", "报价", "合同", "签约", "订单", "回款", "收款", "项目", "客户",
        "lead", "clue", "opportunity", "quote", "contract", "order", "payment", "project", "customer"
    );

    private final CloudPivotEntityRepository entityRepository;
    private final CloudPivotDataItemRepository dataItemRepository;
    private final CloudPivotEntityRelationRepository relationRepository;
    private final CloudPivotInsightDataReader dataReader;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final InsightVisualizationPlanner visualizationPlanner;

    public InsightReportService(
        CloudPivotEntityRepository entityRepository,
        CloudPivotDataItemRepository dataItemRepository,
        CloudPivotEntityRelationRepository relationRepository,
        CloudPivotInsightDataReader dataReader,
        ModelGateway modelGateway,
        ObjectMapper objectMapper,
        InsightVisualizationPlanner visualizationPlanner
    ) {
        this.entityRepository = entityRepository;
        this.dataItemRepository = dataItemRepository;
        this.relationRepository = relationRepository;
        this.dataReader = dataReader;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.visualizationPlanner = visualizationPlanner;
    }

    public boolean supports(String question, String intent) {
        if (!("analyze_data".equals(intent) || "summarize_data".equals(intent))) {
            return false;
        }
        String value = normalize(question);
        boolean focusedDimension = containsAny(
            value,
            "每年", "按年", "年度", "阶段分布", "状态分布", "金额有多少", "金额合计", "按省", "省份", "新老客户", "排名", "第一条", "详情"
        );
        boolean explicitlyBroad = containsAny(value, "整体", "全面", "综合分析", "经营报告", "洞察", "诊断", "健康度");
        if (focusedDimension && !explicitlyBroad) {
            return false;
        }
        return REPORT_WORDS.stream().map(this::normalize).anyMatch(value::contains)
            || (hasTimeScope(value) && value.contains("情况"));
    }

    public InsightExecutionResult execute(
        MetadataSearchResult primaryMatch,
        String question,
        String modelConfigId,
        boolean thinkingEnabled,
        AgentProgressListener progressListener
    ) {
        AgentProgressListener progress = progressListener == null ? AgentProgressListener.NOOP : progressListener;
        CloudPivotEntity primary = resolveEntity(primaryMatch)
            .orElseThrow(() -> new IllegalArgumentException("未能从元数据库定位智能问数的主业务对象"));
        TimeRange timeRange = parseTimeRange(question);
        boolean personalScope = asksPersonalScope(question);

        progress.onThought("insight_metadata", "构建业务元数据图", "正在读取主对象的数据项、关联关系和相邻业务对象", "running");
        MetadataGraph graph = buildGraph(primary);
        IntentPlanningResult modelPlan = planWithModel(question, primary, graph, modelConfigId, thinkingEnabled);
        List<EntityTask> tasks = selectTasks(primary, graph, question, modelPlan);
        progress.onThought(
            "insight_metadata",
            "构建业务元数据图",
            "已形成跨对象分析路径：" + tasks.stream().map(task -> task.entity().getName()).collect(Collectors.joining(" -> ")),
            "completed"
        );

        List<String> warnings = new ArrayList<>();
        List<NodeData> nodeData = new ArrayList<>();
        for (EntityTask task : tasks) {
            progress.onExecution(
                "查询“" + task.entity().getName() + "”",
                "正在调用云枢列表接口获取报告所需数据",
                Map.of("schemaCode", task.entity().getEntityCode(), "role", task.role()),
                "running"
            );
            boolean enrichDetails = "primary".equals(task.role()) || personalScope;
            int recordLimit = LOCAL_PERIOD_FILTER_RECORD_LIMIT;
            List<RuntimeQueryFilter> timeFilters = dateFilters(task, timeRange);
            boolean serverTimeFiltered = !timeFilters.isEmpty();
            CloudPivotRuntimeQueryResult raw;
            try {
                raw = dataReader.query(task.entity(), enrichDetails, recordLimit, timeFilters);
            } catch (RuntimeException filteredQueryFailure) {
                progress.checkCancelled();
                if (timeFilters.isEmpty()) throw filteredQueryFailure;
                warnings.add("云枢未直接接受“" + task.entity().getName() + "”的期间筛选，报告已改为读取完整列表并按日期字段计算");
                raw = dataReader.query(task.entity(), enrichDetails, LOCAL_PERIOD_FILTER_RECORD_LIMIT, List.of());
                serverTimeFiltered = false;
            }
            EntityTask runtimeTask = resolveRuntimeFields(task, raw.records(), warnings);
            NodeData filtered = applyScope(runtimeTask, raw, timeRange, personalScope, warnings, serverTimeFiltered);
            nodeData.add(filtered);
            progress.onExecution(
                "查询“" + task.entity().getName() + "”",
                "云枢接口返回总数 " + raw.total() + "，按报告口径计入 " + filtered.count() + " 条",
                Map.of(
                    "schemaCode", task.entity().getEntityCode(),
                    "sourceEndpoint", raw.sourceEndpoint(),
                    "runtimeTotal", raw.total(),
                    "reportRecords", filtered.count()
                ),
                "completed"
            );
        }

        progress.onExecution("计算经营指标", "正在计算数量、金额、阶段、转化和风险指标", Map.of("entityCount", nodeData.size()), "running");
        progress.onThought("insight_visualization", "规划报告呈现", "正在根据问题意图、字段语义和指标关系选择图表", "running");
        InsightReportDto report = buildReport(primary, question, timeRange, personalScope, modelPlan, nodeData, warnings);
        progress.onThought(
            "insight_visualization",
            "规划报告呈现",
            "已选择：" + report.charts().stream().map(chart -> chart.title() + "（" + chart.type() + "）").collect(Collectors.joining("、")),
            "completed"
        );
        progress.onExecution("计算经营指标", "指标、图表和关联追问已生成", Map.of("kpiCount", report.kpis().size(), "chartCount", report.charts().size()), "completed");

        String fallback = fallbackNarrative(report);
        Map<String, Object> reasoningContext = insightReasoningContext(question, modelPlan, tasks, report);
        NodeData primaryData = nodeData.getFirst();
        String answer = streamNarrative(
            question,
            modelConfigId,
            thinkingEnabled,
            primary,
            primaryData,
            reasoningContext,
            fallback,
            progress
        );
        List<String> endpoints = nodeData.stream().map(data -> data.raw().sourceEndpoint()).distinct().toList();
        return new InsightExecutionResult(
            answer,
            report,
            primary.getName(),
            primary.getEntityCode(),
            primaryData.count(),
            endpoints,
            "基于 " + tasks.size() + " 个真实云枢业务对象生成智能问数报告"
        );
    }

    private Optional<CloudPivotEntity> resolveEntity(MetadataSearchResult match) {
        if (match == null) return Optional.empty();
        if (match.objectId() != null && !match.objectId().isBlank()) {
            Optional<CloudPivotEntity> byId = entityRepository.findById(match.objectId());
            if (byId.isPresent()) return byId;
        }
        if (match.code() == null || match.code().isBlank()) return Optional.empty();
        return entityRepository.findByEntityCodeIgnoreCase(match.code()).stream().findFirst();
    }

    private MetadataGraph buildGraph(CloudPivotEntity primary) {
        List<CloudPivotEntityRelation> relations = new ArrayList<>();
        relations.addAll(relationRepository.findBySourceEntityId(primary.getId()));
        relations.addAll(relationRepository.findByTargetEntityId(primary.getId()));
        List<RelationEdge> edges = relations.stream()
            .distinct()
            .map(relation -> toEdge(primary, relation))
            .flatMap(Optional::stream)
            .toList();
        return new MetadataGraph(dataItemRepository.findByEntityId(primary.getId()), edges);
    }

    private Optional<RelationEdge> toEdge(CloudPivotEntity primary, CloudPivotEntityRelation relation) {
        boolean primaryIsSource = primary.getId().equals(relation.getSourceEntityId());
        String relatedId = primaryIsSource ? relation.getTargetEntityId() : relation.getSourceEntityId();
        Optional<CloudPivotEntity> related = entityRepository.findById(relatedId);
        if (related.isEmpty()) return Optional.empty();
        CloudPivotDataItem relationField = relation.getSourceDataItemId() == null
            ? null
            : dataItemRepository.findById(relation.getSourceDataItemId()).orElse(null);
        return Optional.of(new RelationEdge(relation, related.get(), relationField, primaryIsSource));
    }

    private IntentPlanningResult planWithModel(
        String question,
        CloudPivotEntity primary,
        MetadataGraph graph,
        String modelConfigId,
        boolean thinkingEnabled
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userGoal", question == null ? "" : question);
        context.put("entityName", primary.getName());
        context.put("entityCode", primary.getEntityCode());
        context.put("fieldHints", graph.primaryFields().stream().map(this::fieldSummary).toList());
        context.put("relationHints", graph.edges().stream().map(this::edgeSummary).toList());
        context.put("metadataGraph", graph.edges().stream().map(edge -> Map.of(
            "relatedEntity", edge.related().getName(),
            "relatedSchemaCode", edge.related().getEntityCode(),
            "direction", edge.primaryIsSource() ? "primary_to_related" : "related_to_primary",
            "relationName", safe(edge.relation().getRelationName()),
            "relationField", edge.field() == null ? "" : edge.field().getDataItemCode(),
            "relatedFields", dataItemRepository.findByEntityId(edge.related().getId()).stream().map(this::fieldSummary).limit(80).toList()
        )).toList());
        return modelGateway.planIntent(modelConfigId, context, thinkingEnabled).orElse(IntentPlanningResult.empty());
    }

    private List<EntityTask> selectTasks(
        CloudPivotEntity primary,
        MetadataGraph graph,
        String question,
        IntentPlanningResult modelPlan
    ) {
        List<EntityTask> tasks = new ArrayList<>();
        tasks.add(toTask(primary, "primary", null));
        String modelSignal = modelSignal(modelPlan);
        List<ScoredEdge> candidates = graph.edges().stream()
            .map(edge -> new ScoredEdge(edge, relationScore(primary, edge, question, modelSignal)))
            .filter(value -> value.score() >= 30)
            .sorted(Comparator.comparingInt(ScoredEdge::score).reversed())
            .toList();
        Set<String> selectedEntities = new LinkedHashSet<>();
        for (String preferredRole : List.of("upstream", "downstream", "related")) {
            candidates.stream()
                .map(ScoredEdge::edge)
                .filter(edge -> preferredRole.equals(lifecycleRole(primary, edge.related())))
                .filter(edge -> selectedEntities.add(edge.related().getId()))
                .findFirst()
                .ifPresent(edge -> tasks.add(toTask(edge.related(), preferredRole, edge)));
            if (tasks.size() >= 3) break;
        }
        return tasks;
    }

    private EntityTask resolveRuntimeFields(EntityTask task, List<Map<String, Object>> records, List<String> warnings) {
        CloudPivotDataItem date = bestAvailableField(task.fields(), records, FieldKind.DATE, task.dateField());
        CloudPivotDataItem owner = bestAvailableField(task.fields(), records, FieldKind.OWNER, task.ownerField());
        CloudPivotDataItem stage = bestAvailableField(task.fields(), records, FieldKind.STAGE, task.stageField());
        CloudPivotDataItem amount = bestAvailableField(task.fields(), records, FieldKind.AMOUNT, task.amountField());
        CloudPivotDataItem sign = bestAvailableField(task.fields(), records, FieldKind.SIGN_DATE, task.signField());
        CloudPivotDataItem modified = bestAvailableField(task.fields(), records, FieldKind.MODIFIED_DATE, task.modifiedField());
        if (task.dateField() != null && date != null && !task.dateField().getId().equals(date.getId())) {
            warnings.add("“" + task.entity().getName() + "”的首选期间字段“" + task.dateField().getName() + "”在列表中不可用，已改用“" + date.getName() + "”");
        }
        if (task.amountField() != null && amount != null && !task.amountField().getId().equals(amount.getId())) {
            warnings.add("“" + task.entity().getName() + "”的金额字段按运行态有效值覆盖率改用“" + amount.getName() + "”");
        }
        return new EntityTask(task.entity(), task.role(), task.edge(), task.fields(), date, owner, stage, amount, sign, modified);
    }

    private CloudPivotDataItem bestAvailableField(
        List<CloudPivotDataItem> fields,
        List<Map<String, Object>> records,
        FieldKind kind,
        CloudPivotDataItem fallback
    ) {
        return fields.stream()
            .map(field -> new AvailableField(
                field,
                runtimeCoverage(records, field, kind),
                kind == FieldKind.DATE ? Math.max(fieldScore(field, FieldKind.DATE), fieldScore(field, FieldKind.MODIFIED_DATE)) : fieldScore(field, kind)
            ))
            .filter(value -> value.semanticScore() > 0 && value.coverage() > 0)
            .max(Comparator.comparingLong(AvailableField::coverage).thenComparingInt(AvailableField::semanticScore))
            .map(AvailableField::field)
            .orElse(fallback);
    }

    private long runtimeCoverage(List<Map<String, Object>> records, CloudPivotDataItem field, FieldKind kind) {
        return records.stream().filter(record -> {
            Object raw = value(record, field);
            return switch (kind) {
                case DATE, MODIFIED_DATE, SIGN_DATE -> parseDate(raw).isPresent();
                case AMOUNT -> number(raw).isPresent();
                case OWNER, STAGE -> !readable(raw).isBlank();
            };
        }).count();
    }

    private EntityTask toTask(CloudPivotEntity entity, String role, RelationEdge edge) {
        List<CloudPivotDataItem> fields = dataItemRepository.findByEntityId(entity.getId());
        return new EntityTask(
            entity,
            role,
            edge,
            fields,
            bestField(fields, FieldKind.DATE).orElse(null),
            bestField(fields, FieldKind.OWNER).orElse(null),
            bestField(fields, FieldKind.STAGE).orElse(null),
            bestField(fields, FieldKind.AMOUNT).orElse(null),
            bestField(fields, FieldKind.SIGN_DATE).orElse(null),
            bestField(fields, FieldKind.MODIFIED_DATE).orElse(null)
        );
    }

    private int relationScore(CloudPivotEntity primary, RelationEdge edge, String question, String modelSignal) {
        String query = normalize(question);
        String related = normalize(edge.related().getName() + " " + edge.related().getEntityCode());
        String relation = normalize(safe(edge.relation().getRelationName()));
        int score = 0;
        if (containsTerm(query, edge.related().getName(), edge.related().getEntityCode())) score += 140;
        if (!relation.isBlank() && query.contains(relation)) score += 100;
        if (containsTerm(modelSignal, edge.related().getName(), edge.related().getEntityCode())) score += 100;
        if (!relation.isBlank() && modelSignal.contains(relation)) score += 60;
        int primaryRank = lifecycleRank(primary);
        int relatedRank = lifecycleRank(edge.related());
        if (primaryRank >= 0 && relatedRank >= 0 && primaryRank != relatedRank) score += 45;
        if (LIFECYCLE_WORDS.stream().map(this::normalize).anyMatch(related::contains)) score += 20;
        if (primary.getAppId().equals(edge.related().getAppId())) score += 10;
        if (REPORT_WORDS.stream().map(this::normalize).anyMatch(query::contains)) score += 10;
        return score;
    }

    private String lifecycleRole(CloudPivotEntity primary, CloudPivotEntity related) {
        int primaryRank = lifecycleRank(primary);
        int relatedRank = lifecycleRank(related);
        if (primaryRank >= 0 && relatedRank >= 0) {
            if (relatedRank < primaryRank) return "upstream";
            if (relatedRank > primaryRank) return "downstream";
        }
        return "related";
    }

    private int lifecycleRank(CloudPivotEntity entity) {
        String value = normalize(entity.getName() + " " + entity.getEntityCode());
        if (containsAny(value, "线索", "lead", "clue")) return 10;
        if (containsAny(value, "商机", "机会", "opportunity")) return 20;
        if (containsAny(value, "报价", "quote")) return 30;
        if (containsAny(value, "合同", "签约", "订单", "contract", "order")) return 40;
        if (containsAny(value, "回款", "收款", "payment")) return 50;
        return -1;
    }

    private List<RuntimeQueryFilter> dateFilters(EntityTask task, TimeRange timeRange) {
        if (!timeRange.present() || task.dateField() == null) return List.of();
        String fieldCode = task.dateField().getDataItemCode();
        String fieldName = task.dateField().getName();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return List.of(
            new RuntimeQueryFilter(fieldCode, fieldName, "gte", timeRange.start().format(formatter), "insight_period", 1D),
            new RuntimeQueryFilter(fieldCode, fieldName, "lt", timeRange.endExclusive().format(formatter), "insight_period", 1D)
        );
    }

    private NodeData applyScope(
        EntityTask task,
        CloudPivotRuntimeQueryResult raw,
        TimeRange timeRange,
        boolean personalScope,
        List<String> warnings,
        boolean serverTimeFiltered
    ) {
        List<Map<String, Object>> records = raw.records();
        long count = raw.total();
        boolean timeApplied = false;
        boolean identityApplied = false;
        boolean usable = true;
        if (timeRange.present()) {
            if (serverTimeFiltered) {
                timeApplied = true;
            } else if (task.dateField() == null) {
                warnings.add("“" + task.entity().getName() + "”未找到可靠日期字段，该对象未计入期间指标");
                records = List.of();
                count = 0;
                usable = false;
            } else {
                long parsableDates = records.stream()
                    .map(record -> parseDate(value(record, task.dateField())))
                    .filter(Optional::isPresent)
                    .count();
                if (parsableDates == 0 && !records.isEmpty()) {
                    warnings.add("“" + task.entity().getName() + "”存在日期元数据“" + task.dateField().getName() + "”，但运行态列表未返回可解析日期值，该对象未计入期间指标");
                    usable = false;
                } else if (parsableDates < records.size()) {
                    warnings.add("“" + task.entity().getName() + "”仅有 " + parsableDates + "/" + records.size() + " 条记录返回可解析日期，期间指标按可解析记录计算");
                }
                records = records.stream().filter(record -> inRange(value(record, task.dateField()), timeRange)).toList();
                count = records.size();
                timeApplied = true;
            }
        }
        if (personalScope) {
            String username = dataReader.configuredUsername();
            if (username.isBlank() || task.ownerField() == null) {
                warnings.add("“" + task.entity().getName() + "”无法把当前登录账号映射到负责人字段，未计入“我的”指标");
                records = List.of();
                count = 0;
                usable = false;
            } else {
                List<Map<String, Object>> matched = records.stream()
                    .filter(record -> recursivelyContains(value(record, task.ownerField()), username))
                    .toList();
                if (matched.isEmpty() && !records.isEmpty()) {
                    warnings.add("“" + task.entity().getName() + "”的负责人数据未匹配当前账号“" + username + "”，未返回全员数据冒充个人结果");
                    usable = false;
                }
                records = matched;
                count = matched.size();
                identityApplied = true;
            }
        }
        if (raw.total() > raw.records().size()) {
            warnings.add("“" + task.entity().getName() + "”数量使用云枢接口 total=" + raw.total() + "，明细分析仅覆盖 " + raw.records().size() + " 条");
        }
        return new NodeData(task, raw, records, count, timeApplied, identityApplied, usable);
    }

    private InsightReportDto buildReport(
        CloudPivotEntity primary,
        String question,
        TimeRange timeRange,
        boolean personalScope,
        IntentPlanningResult modelPlan,
        List<NodeData> nodes,
        List<String> warnings
    ) {
        NodeData primaryData = nodes.getFirst();
        long primaryCount = primaryData.count();
        double amount = sum(primaryData, primaryData.task().amountField());
        Map<String, Long> stages = distribution(primaryData, primaryData.task().stageField());
        long signedCount = signedCount(primaryData);
        Optional<NodeData> upstream = nodes.stream().filter(NodeData::usable).filter(node -> "upstream".equals(node.task().role())).findFirst();
        Optional<NodeData> downstream = nodes.stream().filter(NodeData::usable).filter(node -> "downstream".equals(node.task().role())).findFirst();
        Optional<Long> linkedUpstreamCount = upstream.flatMap(node -> linkedRelatedCount(primaryData, node));
        Optional<Long> linkedDownstreamCount = downstream.flatMap(node -> linkedRelatedCount(primaryData, node));
        long amountCoverage = metricCoverage(primaryData, primaryData.task().amountField(), FieldKind.AMOUNT);
        long stageCoverage = metricCoverage(primaryData, primaryData.task().stageField(), FieldKind.STAGE);

        if (primaryData.task().amountField() != null && amountCoverage < primaryCount) {
            warnings.add("“" + primary.getName() + "”金额仅覆盖 " + amountCoverage + "/" + primaryCount + " 条可识别记录，金额指标不代表未识别记录");
        }
        if (primaryData.task().stageField() != null && stageCoverage < primaryCount) {
            warnings.add("“" + primary.getName() + "”阶段仅覆盖 " + stageCoverage + "/" + primaryCount + " 条可识别记录，阶段分布按已识别记录计算");
        }
        upstream.filter(node -> linkedUpstreamCount.isEmpty())
            .ifPresent(node -> warnings.add("未能通过关联字段验证“" + node.task().entity().getName() + "”到“" + primary.getName() + "”的转化关系，本报告不输出推测转化率"));
        downstream.filter(node -> linkedDownstreamCount.isEmpty())
            .ifPresent(node -> warnings.add("未能通过关联字段验证“" + primary.getName() + "”到“" + node.task().entity().getName() + "”的形成关系，本报告不把同期总数当作关联结果"));

        List<Kpi> kpis = new ArrayList<>();
        upstream.ifPresent(node -> kpis.add(new Kpi(node.task().entity().getName() + "数", formatInteger(node.count()), "条", "neutral", "按相同时间与人员口径统计")));
        kpis.add(new Kpi(primary.getName() + "数", formatInteger(primaryCount), "条", "primary", "智能问数报告主对象"));
        if (primaryData.task().amountField() != null) {
            String amountLabel = amountCoverage < primaryCount ? "已识别金额合计" : "金额合计";
            kpis.add(new Kpi(amountLabel, formatAmount(amount) + "元", "", "primary", "字段：" + primaryData.task().amountField().getName() + "；覆盖 " + amountCoverage + "/" + primaryCount + " 条"));
        }
        if (signedCount > 0 || primaryData.task().signField() != null) {
            kpis.add(new Kpi("已签约", formatInteger(signedCount), "条", signedCount == 0 ? "warning" : "success", "依据签约日期或签约状态识别"));
        } else {
            downstream.ifPresent(node -> linkedDownstreamCount.ifPresent(linked -> kpis.add(new Kpi(node.task().entity().getName() + "数", formatInteger(linked), "条", "success", "依据关联字段核验"))));
        }

        List<Chart> charts = new ArrayList<>();
        buildBusinessFlow(primary, primaryCount, upstream, linkedUpstreamCount, downstream, linkedDownstreamCount).ifPresent(charts::add);
        if (!stages.isEmpty()) {
            charts.add(visualizationPlanner.planStageDistribution(
                primary.getName(), question, primaryData.task().stageField(), stages, stageCoverage, primaryCount
            ));
        }
        Map<String, Double> stageAmounts = amountDistribution(primaryData);
        if (!stageAmounts.isEmpty()) {
            charts.add(visualizationPlanner.planStageAmounts(primaryData.task().stageField(), stageAmounts));
        } else {
            Map<String, Long> monthly = monthlyTrend(primaryData);
            if (!monthly.isEmpty()) {
                charts.add(visualizationPlanner.planMonthlyTrend(monthly));
            }
        }

        List<String> coreFindings = new ArrayList<>();
        coreFindings.add(periodPrefix(timeRange, personalScope) + primary.getName() + "共 " + primaryCount + " 条" + (primaryData.task().amountField() == null ? "。" : "，可识别金额合计 " + formatAmount(amount) + " 元，覆盖 " + amountCoverage + "/" + primaryCount + " 条。"));
        upstream.ifPresent(node -> linkedUpstreamCount.ifPresentOrElse(
            linked -> coreFindings.add(node.task().entity().getName() + "共 " + node.count() + " 条，其中 " + linked + " 条通过关联字段确认已进入" + primary.getName() + "，关联转化率为 " + percent(linked, node.count()) + "。"),
            () -> coreFindings.add(node.task().entity().getName() + "同期共 " + node.count() + " 条，但当前数据未能验证与" + primary.getName() + "的逐条关联。")
        ));
        downstream.ifPresent(node -> linkedDownstreamCount.ifPresentOrElse(
            linked -> coreFindings.add("通过关联字段确认已有 " + linked + " 条" + node.task().entity().getName() + "由本期" + primary.getName() + "形成，占本期" + primary.getName() + "的 " + percent(linked, primaryCount) + "。"),
            () -> coreFindings.add(node.task().entity().getName() + "同期共 " + node.count() + " 条，但当前数据未能验证其与本期" + primary.getName() + "的逐条关联。")
        ));
        stages.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(entry -> coreFindings.add("当前数量最多的阶段是“" + entry.getKey() + "”，共 " + entry.getValue() + " 条。"));

        List<String> risks = buildRisks(primaryData, stages, upstream, linkedUpstreamCount, amount, warnings);
        List<String> recommendations = buildRecommendations(primary, primaryData, upstream, downstream, stages, risks);
        List<Section> sections = List.of(
            new Section("核心结论", coreFindings),
            new Section("问题与风险", risks),
            new Section("建议动作", recommendations)
        );

        List<String> relatedQuestions = buildRelatedQuestions(primary, primaryData, upstream, downstream, stages);
        List<String> sources = nodes.stream()
            .map(node -> node.task().entity().getName() + "(`" + node.task().entity().getEntityCode() + "`) - " + node.raw().sourceEndpoint())
            .toList();
        long confidenceRisks = warnings.stream().filter(this::reducesConfidence).count();
        String confidence = confidenceRisks == 0 && modelPlan.confidence() >= 0.7 ? "high" : confidenceRisks <= 2 ? "medium" : "low";
        return new InsightReportDto(
            periodPrefix(timeRange, personalScope) + primary.getName() + "经营洞察",
            primary.getName(),
            timeRange.label(),
            personalScope ? "当前云枢账号" : "当前可见数据",
            confidence,
            kpis,
            charts.stream().limit(3).toList(),
            sections,
            relatedQuestions,
            sources,
            warnings.stream().distinct().toList()
        );
    }

    private Optional<Chart> buildBusinessFlow(
        CloudPivotEntity primary,
        long primaryCount,
        Optional<NodeData> upstream,
        Optional<Long> linkedUpstreamCount,
        Optional<NodeData> downstream,
        Optional<Long> linkedDownstreamCount
    ) {
        if (upstream.isEmpty() && downstream.isEmpty()) return Optional.empty();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        upstream.ifPresent(node -> {
            labels.add(node.task().entity().getName() + "（同期）");
            values.add((double) node.count());
        });
        linkedUpstreamCount.ifPresent(value -> {
            labels.add("已关联" + upstream.map(node -> node.task().entity().getName()).orElse("上游数据"));
            values.add(value.doubleValue());
        });
        labels.add(primary.getName());
        values.add((double) primaryCount);
        downstream.ifPresent(node -> linkedDownstreamCount.ifPresent(value -> {
            labels.add("已关联" + node.task().entity().getName());
            values.add(value.doubleValue());
        }));
        boolean relationsVerified = upstream.map(node -> linkedUpstreamCount.isPresent()).orElse(true)
            && downstream.map(node -> linkedDownstreamCount.isPresent()).orElse(true);
        return visualizationPlanner.planBusinessFlow(labels, values, relationsVerified);
    }

    private List<String> buildRisks(
        NodeData primary,
        Map<String, Long> stages,
        Optional<NodeData> upstream,
        Optional<Long> linkedUpstreamCount,
        double amount,
        List<String> warnings
    ) {
        List<String> risks = new ArrayList<>();
        long count = primary.count();
        stages.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(entry -> {
            if (count > 0 && entry.getValue() * 1D / count >= 0.6) {
                risks.add("阶段“" + entry.getKey() + "”占比达到 " + percent(entry.getValue(), count) + "，业务可能过度集中在单一阶段。");
            }
        });
        if (primary.task().amountField() != null && count > 0) {
            long missing = primary.records().stream().filter(record -> number(value(record, primary.task().amountField())).isEmpty()).count();
            if (missing > 0) risks.add("有 " + missing + " 条记录缺少可计算金额，金额结论存在数据质量缺口。");
            if (amount == 0D) risks.add("当前口径金额合计为 0，需要核查金额字段是否填写或字段口径是否正确。");
        }
        upstream.ifPresent(node -> linkedUpstreamCount.ifPresent(linked -> {
            if (node.count() > 0 && linked * 1D / node.count() < 0.2) {
                risks.add("从“" + node.task().entity().getName() + "”到“" + primary.task().entity().getName() + "”的口径转化率低于 20%。");
            }
        }));
        warnings.stream().limit(2).map(value -> "数据口径提示：" + value).forEach(risks::add);
        if (risks.isEmpty()) risks.add("在当前可用字段和数据范围内，未发现明显的集中度或数据完整性异常。");
        return risks;
    }

    private List<String> buildRecommendations(
        CloudPivotEntity primary,
        NodeData primaryData,
        Optional<NodeData> upstream,
        Optional<NodeData> downstream,
        Map<String, Long> stages,
        List<String> risks
    ) {
        List<String> result = new ArrayList<>();
        stages.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(entry -> result.add("优先下钻“" + entry.getKey() + "”阶段，检查停留时间、负责人和下一步动作。"));
        upstream.ifPresent(node -> result.add("对比未转化与已转化的“" + node.task().entity().getName() + "”，识别来源和负责人差异。"));
        downstream.ifPresent(node -> result.add("核查尚未形成“" + node.task().entity().getName() + "”的高金额" + primary.getName() + "，明确签约障碍。"));
        if (primaryData.task().ownerField() != null) result.add("按“" + primaryData.task().ownerField().getName() + "”比较数量、金额和转化效率，确定辅导优先级。");
        if (risks.stream().anyMatch(value -> value.contains("数据"))) result.add("补齐缺失字段并确认日期、金额、负责人字段口径后，再进行经营决策。");
        return result.stream().distinct().limit(4).toList();
    }

    private List<String> buildRelatedQuestions(
        CloudPivotEntity primary,
        NodeData primaryData,
        Optional<NodeData> upstream,
        Optional<NodeData> downstream,
        Map<String, Long> stages
    ) {
        List<String> questions = new ArrayList<>();
        stages.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(entry -> questions.add("“" + entry.getKey() + "”阶段的" + primary.getName() + "分别由谁负责？"));
        if (primaryData.task().amountField() != null) questions.add("哪些" + primary.getName() + "金额最高，但推进较慢？");
        upstream.ifPresent(node -> questions.add("哪些" + node.task().entity().getName() + "还没有转化为" + primary.getName() + "？"));
        downstream.ifPresent(node -> questions.add("哪些高价值" + primary.getName() + "还没有形成" + node.task().entity().getName() + "？"));
        if (primaryData.task().ownerField() != null) questions.add("按负责人比较" + primary.getName() + "数量、金额和阶段分布。");
        return questions.stream().distinct().limit(4).toList();
    }

    private String streamNarrative(
        String question,
        String modelConfigId,
        boolean thinkingEnabled,
        CloudPivotEntity primary,
        NodeData primaryData,
        Map<String, Object> reasoningContext,
        String fallback,
        AgentProgressListener progress
    ) {
        progress.onThought("insight_summary", "生成咨询式报告", "正在把已核验指标交给大模型组织为可读结论", "running");
        progress.onAnswerStart("model");
        Optional<String> modelAnswer = modelGateway.analyzeRecordsStream(
            modelConfigId,
            question,
            primary.getName() + "智能问数报告",
            primaryData.count(),
            primaryData.records().stream().limit(8).toList(),
            thinkingEnabled,
            reasoningContext,
            progress::onAnswerChunk
        );
        if (modelAnswer.isPresent() && !modelAnswer.get().isBlank()) {
            progress.onAnswerComplete("model");
            progress.onThought("insight_summary", "生成咨询式报告", "大模型已基于真实指标完成报告总结", "completed");
            return modelAnswer.get();
        }
        progress.onAnswerReset("模型总结未完整返回，切换为基于已核验指标的稳定报告");
        streamChunks(fallback, progress);
        progress.onAnswerComplete("rule");
        progress.onThought("insight_summary", "生成咨询式报告", "已使用确定性指标生成报告，未编造缺失数据", "completed");
        return fallback;
    }

    private Map<String, Object> insightReasoningContext(
        String question,
        IntentPlanningResult modelPlan,
        List<EntityTask> tasks,
        InsightReportDto report
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("mode", "metadata_driven_intelligent_data_report");
        context.put("userQuestion", question);
        context.put("modelPlanningReason", modelPlan.reasoning());
        context.put("executionPath", tasks.stream().map(task -> Map.of(
            "entityName", task.entity().getName(),
            "schemaCode", task.entity().getEntityCode(),
            "role", task.role(),
            "dateField", fieldCode(task.dateField()),
            "ownerField", fieldCode(task.ownerField()),
            "stageField", fieldCode(task.stageField()),
            "amountField", fieldCode(task.amountField())
        )).toList());
        context.put("verifiedInsightReport", report);
        context.put("outputRules", List.of(
            "先直接回答用户问题，再解释关键指标",
            "只使用 verifiedInsightReport 中的事实和数值",
            "指出风险、数据盲区和建议，不输出原始表结构",
            "不要重复字段名称，不要编造关联对象或指标",
            "使用清晰的中文 Markdown，保持专业咨询报告风格",
            "正文控制在约 1000 个中文字符内，优先保留结论、关键依据、风险和建议，图表细节由结构化报告承载"
        ));
        return context;
    }

    private String fallbackNarrative(InsightReportDto report) {
        StringBuilder answer = new StringBuilder();
        answer.append("## ").append(report.title()).append("\n\n");
        Section core = report.sections().getFirst();
        if (!core.findings().isEmpty()) answer.append("**结论：** ").append(core.findings().getFirst()).append("\n\n");
        answer.append("### 关键发现\n");
        core.findings().forEach(value -> answer.append("- ").append(value).append("\n"));
        answer.append("\n### 风险与问题\n");
        report.sections().get(1).findings().forEach(value -> answer.append("- ").append(value).append("\n"));
        answer.append("\n### 建议动作\n");
        report.sections().get(2).findings().forEach(value -> answer.append("- ").append(value).append("\n"));
        if (!report.warnings().isEmpty()) {
            answer.append("\n> 数据口径：").append(String.join("；", report.warnings())).append("\n");
        }
        return answer.toString();
    }

    private void streamChunks(String content, AgentProgressListener progress) {
        AnswerStreamSupport.emitReadableChunks(content, progress::onAnswerChunk);
    }

    private Optional<CloudPivotDataItem> bestField(List<CloudPivotDataItem> fields, FieldKind kind) {
        return fields.stream()
            .map(field -> new ScoredField(field, fieldScore(field, kind)))
            .filter(value -> value.score() > 0)
            .max(Comparator.comparingInt(ScoredField::score))
            .map(ScoredField::field);
    }

    private int fieldScore(CloudPivotDataItem field, FieldKind kind) {
        String value = normalize(field.getName() + " " + field.getDataItemCode() + " " + safe(field.getDescription()));
        return switch (kind) {
            case DATE -> score(value, Map.of("createdtime", 120, "createtime", 110, "创建时间", 120, "创建日期", 110, "日期", 20));
            case MODIFIED_DATE -> score(value, Map.of("modifiedtime", 120, "updatetime", 110, "修改时间", 120, "更新时间", 110));
            case OWNER -> score(value, Map.of("负责人", 130, "拥有者", 120, "客户经理", 110, "跟进人", 100, "owner", 120, "salesman", 100));
            case STAGE -> score(value, Map.of("销售阶段", 140, "阶段", 120, "stage", 120, "状态", 80, "status", 80));
            case AMOUNT -> score(value, Map.ofEntries(
                Map.entry("pre_sign_dam_yuan", 190),
                Map.entry("预计签约金额", 180),
                Map.entry("max_pre_sign_dam_yuan", 165),
                Map.entry("商机金额", 150),
                Map.entry("合同金额", 145),
                Map.entry("项目金额", 140),
                Map.entry("implementation_amount", 130),
                Map.entry("金额", 100),
                Map.entry("amount", 100),
                Map.entry("revenue", 90)
            ));
            case SIGN_DATE -> score(value, Map.of("实际签约时间", 160, "签约时间", 150, "signtime", 150, "签约日期", 140, "签约", 70));
        };
    }

    private int score(String value, Map<String, Integer> weights) {
        return weights.entrySet().stream().filter(entry -> value.contains(normalize(entry.getKey()))).mapToInt(Map.Entry::getValue).max().orElse(0);
    }

    private long metricCoverage(NodeData data, CloudPivotDataItem field, FieldKind kind) {
        return field == null ? 0 : runtimeCoverage(data.records(), field, kind);
    }

    private Optional<Long> linkedRelatedCount(NodeData primary, NodeData related) {
        RelationEdge edge = related.task().edge();
        if (edge == null || edge.field() == null) return Optional.empty();
        Set<String> primaryIds = recordIds(primary.records());
        Set<String> relatedIds = recordIds(related.records());
        if (edge.primaryIsSource()) {
            Set<String> referencedRelatedIds = primary.records().stream()
                .flatMap(record -> relationIds(value(record, edge.field())).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
            if (referencedRelatedIds.isEmpty()) return Optional.empty();
            if (!relatedIds.isEmpty()) referencedRelatedIds.retainAll(relatedIds);
            return Optional.of((long) referencedRelatedIds.size());
        }
        if (primaryIds.isEmpty()) return Optional.empty();
        long linked = related.records().stream().filter(record -> {
            Set<String> references = relationIds(value(record, edge.field()));
            return references.stream().anyMatch(primaryIds::contains);
        }).count();
        return linked > 0 || related.count() == 0 ? Optional.of(linked) : Optional.empty();
    }

    private Set<String> recordIds(List<Map<String, Object>> records) {
        return records.stream().map(this::recordId).flatMap(Optional::stream).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Optional<String> recordId(Map<String, Object> record) {
        for (String key : List.of("id", "objectId", "bizObjectId")) {
            Object candidate = mapValue(record, key);
            if (candidate != null && !String.valueOf(candidate).isBlank()) return Optional.of(String.valueOf(candidate));
        }
        return Optional.empty();
    }

    private Set<String> relationIds(Object value) {
        Set<String> result = new LinkedHashSet<>();
        collectRelationIds(value, result, true);
        return result;
    }

    private void collectRelationIds(Object value, Set<String> result, boolean scalarAllowed) {
        if (value == null) return;
        if (value instanceof List<?> list) {
            list.forEach(item -> collectRelationIds(item, result, true));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = normalize(String.valueOf(entry.getKey()));
                Object nested = entry.getValue();
                if (Set.of("id", "objectid", "bizobjectid").contains(key)) {
                    collectRelationIds(nested, result, true);
                } else if (nested instanceof Map<?, ?> || nested instanceof List<?>) {
                    collectRelationIds(nested, result, false);
                }
            }
            return;
        }
        if (scalarAllowed) {
            String id = String.valueOf(value).trim();
            if (!id.isBlank()) result.add(id);
        }
    }

    private Map<String, Long> distribution(NodeData data, CloudPivotDataItem field) {
        if (field == null) return Map.of();
        return data.records().stream()
            .map(record -> readable(value(record, field)))
            .filter(text -> !text.isBlank())
            .collect(Collectors.groupingBy(text -> text, LinkedHashMap::new, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, Double> amountDistribution(NodeData data) {
        if (data.task().stageField() == null || data.task().amountField() == null) return Map.of();
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map<String, Object> record : data.records()) {
            String stage = readable(value(record, data.task().stageField()));
            Optional<Double> amount = number(value(record, data.task().amountField()));
            if (!stage.isBlank() && amount.isPresent()) result.merge(stage, amount.get(), Double::sum);
        }
        return result.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(8)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, Long> monthlyTrend(NodeData data) {
        if (data.task().dateField() == null) return Map.of();
        return data.records().stream()
            .map(record -> parseDate(value(record, data.task().dateField())))
            .flatMap(Optional::stream)
            .collect(Collectors.groupingBy(date -> date.format(DateTimeFormatter.ofPattern("yyyy-MM")), LinkedHashMap::new, Collectors.counting()));
    }

    private double sum(NodeData data, CloudPivotDataItem field) {
        if (field == null) return 0D;
        return data.records().stream().map(record -> number(value(record, field))).flatMap(Optional::stream).mapToDouble(Double::doubleValue).sum();
    }

    private long signedCount(NodeData data) {
        return data.records().stream().filter(record -> {
            if (data.task().signField() != null && !readable(value(record, data.task().signField())).isBlank()) return true;
            return data.task().fields().stream()
                .filter(field -> fieldScore(field, FieldKind.STAGE) > 0)
                .map(field -> normalize(readable(value(record, field))))
                .anyMatch(status -> containsAny(status, "已签约", "签约完成", "成交", "赢单", "closedwon", "contracted"));
        }).count();
    }

    private Object value(Map<String, Object> record, CloudPivotDataItem field) {
        Map<?, ?> data = record.get("data") instanceof Map<?, ?> map ? map : record;
        for (String key : candidateKeys(field)) {
            Object nested = mapValue(data, key);
            if (nested != null) return nested;
            if (data != record) {
                Object topLevel = mapValue(record, key);
                if (topLevel != null) return topLevel;
            }
        }
        return null;
    }

    private Object mapValue(Map<?, ?> source, String key) {
        Object exact = source.get(key);
        if (exact != null) return exact;
        return source.entrySet().stream()
            .filter(entry -> entry.getValue() != null && String.valueOf(entry.getKey()).equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }

    private List<String> candidateKeys(CloudPivotDataItem field) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(field.getDataItemCode());
        String value = normalize(field.getName() + " " + field.getDataItemCode());
        if (containsAny(value, "createdtime", "createtime", "创建时间", "创建日期")) {
            keys.addAll(List.of("createdTime", "createdAt", "createTime", "creationTime", "createdDate"));
        }
        if (containsAny(value, "modifiedtime", "updatetime", "修改时间", "更新时间")) {
            keys.addAll(List.of("modifiedTime", "modifiedAt", "updatedAt", "updateTime", "modifyTime"));
        }
        return new ArrayList<>(keys);
    }

    private String readable(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            return list.stream().map(this::readable).filter(text -> !text.isBlank()).distinct().limit(3).collect(Collectors.joining("、"));
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("name", "displayName", "label", "instanceName", "sequenceNo", "org_name", "value", "id")) {
                Object nested = map.get(key);
                if (nested != null && !String.valueOf(nested).isBlank()) return String.valueOf(nested);
            }
            return map.values().stream().map(this::readable).filter(text -> !text.isBlank()).findFirst().orElse("");
        }
        return String.valueOf(value).trim();
    }

    private Optional<Double> number(Object value) {
        if (value instanceof Number number) return Optional.of(number.doubleValue());
        String text = readable(value).replace(",", "").replace("￥", "").replace("¥", "").trim();
        if (text.isBlank()) return Optional.empty();
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text);
        if (!matcher.find()) return Optional.empty();
        try {
            return Optional.of(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private boolean recursivelyContains(Object value, String expected) {
        String needle = normalize(expected);
        if (needle.isBlank() || value == null) return false;
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(item -> recursivelyContains(item, expected));
        if (value instanceof List<?> list) return list.stream().anyMatch(item -> recursivelyContains(item, expected));
        return normalize(String.valueOf(value)).contains(needle);
    }

    private boolean inRange(Object value, TimeRange range) {
        return parseDate(value).map(date -> !date.isBefore(range.start()) && date.isBefore(range.endExclusive())).orElse(false);
    }

    private Optional<LocalDateTime> parseDate(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof Instant instant) return Optional.of(LocalDateTime.ofInstant(instant, BUSINESS_ZONE));
        if (value instanceof LocalDateTime date) return Optional.of(date);
        if (value instanceof LocalDate date) return Optional.of(date.atStartOfDay());
        String text = readable(value).trim();
        if (text.isBlank()) return Optional.empty();
        for (DateTimeFormatter formatter : List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
        )) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME || text.contains(":")) return Optional.of(LocalDateTime.parse(text, formatter));
                return Optional.of(LocalDate.parse(text, formatter).atStartOfDay());
            } catch (DateTimeParseException ignored) {
                // Try the next supported CloudPivot date shape.
            }
        }
        try {
            return Optional.of(OffsetDateTime.parse(text).atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private TimeRange parseTimeRange(String question) {
        String value = normalize(question);
        int currentYear = LocalDate.now(BUSINESS_ZONE).getYear();
        Matcher matcher = YEAR_PATTERN.matcher(question == null ? "" : question);
        int year = matcher.find() ? Integer.parseInt(matcher.group(1)) : currentYear;
        if (value.contains("上半年")) return range(year, 1, 1, year, 7, 1, year + "年上半年");
        if (value.contains("下半年")) return range(year, 7, 1, year + 1, 1, 1, year + "年下半年");
        if (value.contains("去年")) return range(currentYear - 1, 1, 1, currentYear, 1, 1, (currentYear - 1) + "年");
        if (value.contains("今年") || matcher.find(0)) return range(year, 1, 1, year + 1, 1, 1, year + "年");
        if (value.contains("本月")) {
            LocalDate first = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
            return new TimeRange(first.atStartOfDay(), first.plusMonths(1).atStartOfDay(), first.format(DateTimeFormatter.ofPattern("yyyy年MM月")));
        }
        return TimeRange.none();
    }

    private TimeRange range(int sy, int sm, int sd, int ey, int em, int ed, String label) {
        return new TimeRange(LocalDate.of(sy, sm, sd).atStartOfDay(), LocalDate.of(ey, em, ed).atStartOfDay(), label);
    }

    private boolean asksPersonalScope(String question) {
        String value = normalize(question);
        return containsAny(value, "我的", "我负责", "我名下", "分配给我", "由我跟进");
    }

    private boolean hasTimeScope(String value) {
        return containsAny(value, "上半年", "下半年", "今年", "去年", "本月") || YEAR_PATTERN.matcher(value).find();
    }

    private String periodPrefix(TimeRange range, boolean personal) {
        StringBuilder value = new StringBuilder();
        if (range.present()) value.append(range.label());
        if (personal) value.append(value.isEmpty() ? "我的" : "我的");
        if (!value.isEmpty()) value.append(" ");
        return value.toString();
    }

    private String formatInteger(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private String formatAmount(double value) {
        if (Math.abs(value) >= 100_000_000) return decimal(value / 100_000_000) + "亿";
        if (Math.abs(value) >= 10_000) return decimal(value / 10_000) + "万";
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String percent(long numerator, long denominator) {
        if (denominator <= 0) return "无法计算";
        return BigDecimal.valueOf(numerator * 100D / denominator).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String fieldSummary(CloudPivotDataItem field) {
        return field.getName() + "(" + field.getDataItemCode() + "," + safe(field.getDataType()) + ")";
    }

    private String edgeSummary(RelationEdge edge) {
        return edge.related().getName() + "(" + edge.related().getEntityCode() + ") via "
            + safe(edge.relation().getRelationName()) + "/" + (edge.field() == null ? "" : edge.field().getDataItemCode());
    }

    private String modelSignal(IntentPlanningResult plan) {
        try {
            return normalize(objectMapper.writeValueAsString(plan));
        } catch (JsonProcessingException ignored) {
            return normalize(plan.reasoning());
        }
    }

    private String fieldCode(CloudPivotDataItem field) {
        return field == null ? "" : field.getDataItemCode();
    }

    private boolean reducesConfidence(String warning) {
        return !warning.contains("已改为读取完整列表并按日期字段计算");
    }

    private boolean containsTerm(String source, String... values) {
        for (String value : values) {
            String term = normalize(value);
            if (!term.isBlank() && source.contains(term)) return true;
        }
        return false;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(normalize(term))) return true;
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s`'\"，。！？、：；（）()_-]+", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private enum FieldKind { DATE, MODIFIED_DATE, OWNER, STAGE, AMOUNT, SIGN_DATE }

    private record MetadataGraph(List<CloudPivotDataItem> primaryFields, List<RelationEdge> edges) {
    }

    private record RelationEdge(
        CloudPivotEntityRelation relation,
        CloudPivotEntity related,
        CloudPivotDataItem field,
        boolean primaryIsSource
    ) {
    }

    private record EntityTask(
        CloudPivotEntity entity,
        String role,
        RelationEdge edge,
        List<CloudPivotDataItem> fields,
        CloudPivotDataItem dateField,
        CloudPivotDataItem ownerField,
        CloudPivotDataItem stageField,
        CloudPivotDataItem amountField,
        CloudPivotDataItem signField,
        CloudPivotDataItem modifiedField
    ) {
    }

    private record NodeData(
        EntityTask task,
        CloudPivotRuntimeQueryResult raw,
        List<Map<String, Object>> records,
        long count,
        boolean timeApplied,
        boolean identityApplied,
        boolean usable
    ) {
    }

    private record ScoredEdge(RelationEdge edge, int score) {
    }

    private record ScoredField(CloudPivotDataItem field, int score) {
    }

    private record AvailableField(CloudPivotDataItem field, long coverage, int semanticScore) {
    }

    private record TimeRange(LocalDateTime start, LocalDateTime endExclusive, String label) {
        static TimeRange none() { return new TimeRange(null, null, "全部时间"); }
        boolean present() { return start != null && endExclusive != null; }
    }
}
