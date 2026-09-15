# CPClaw 助手化核心架构复核与优化方案

> 状态：专家评审通过后的实施基线（契约先行）；不代表已实施或可生产交付。  
> 范围：CPClaw 如何从“能查询/能生成报告的云枢对话入口”升级为“能理解、引导并受控操作云枢的业务助手”。  
> 依据：当前源码、`20-universal-yunshu-skill-runtime-spec.md`、`22-yunshu-skill-executable-architecture.md`、`23-security-provider-release-gates.md`、`24-yunshu-skill-implementation-contract.md` 与现有验证记录。

## 1. 结论

当前架构方向正确：元数据约束、统一任务入口、Provider 隔离、确认与审计、宿主可替换，都是业务助手必须具备的基础。

但当前运行时仍主要是“单轮请求 → 意图分类 → 元数据匹配 → 一次读取或一次报告”的执行模型，尚未形成统一、长期存在的**业务任务契约**。这会直接表现为：

- 用户说得不完整时，系统不能稳定判断应追问对象、范围、字段、口径还是执行方式；
- 填单、查询、分析、待办等任务在不同入口或不同 Runtime 中切换，无法保证同一任务连续推进；
- 通用分析仍有关键词/能力判定门槛，不能以“任何已验证对象 + 关系 + 字段角色”作为默认能力；
- 表单字段、枚举、关联、联动和真实写入尚未由已验证 Provider 契约闭环；
- 已存在的记忆存储尚未成为任务理解与纠错的受控输入。

因此，优化重点不应是增加提示词或为“线索、商机、项目、收款”增加分支，而应建设**任务中心、元数据语义层、受限计划执行器和可靠 Provider**四项通用能力。

## 2. 复核方法与事实边界

本文明确区分：

| 标记 | 含义 |
| --- | --- |
| 代码事实 | 已在当前源码中确认；不等同于生产 E2E 已通过。 |
| 设计目标 | 已在 20/22/23/24 号文档中定义，尚未必然由代码实现。 |
| 优化推论 | 基于代码事实和用户体验目标提出，需在开发前冻结契约与验收。 |

本次未把真实云枢 `create/update/submit/getOperationResult`、OIDC/JWT、跨实例恢复或 WorkBuddy 生产联调当作已完成能力。

## 3. 当前架构的有效基础

| 基础能力 | 代码事实 | 应保留的原因 |
| --- | --- | --- |
| 元数据优先 | `YunshuMcpTaskExecutor` 通过 `YunshuMetadataDiscovery`、`MetadataExecutionPlanner` 定位可执行对象。 | 防止模型臆造对象、字段和云枢接口。 |
| 统一任务封装 | Web 主路径进入 `TaskGateway → SemanticTaskRuntime → SkillRegistry → YunshuMcpTaskExecutor`。 | 应继续作为所有 Skill 的协议、审计和幂等边界。 |
| 分析数据与展示分离 | 洞察结果产出结构化 artifact、证据与 Markdown。 | 图表和说明可保持同一事实来源。 |
| 表单草稿持久化 | `PersistentFormTaskRuntime` 有 `taskId`、CAS 版本、草稿修订、取消与字段级本地校验。 | 是连续填单任务的可用基础。 |
| 安全默认拒绝写入 | `YunshuProvider` 的写接口默认 `CONTRACT_UNVERIFIED`。 | 在真实契约未验证前避免伪造“已提交”。 |
| Skill 治理 | Markdown Skill 被限制在受审核 Manifest 和服务端白名单 Executor。 | 允许扩展而不引入任意脚本执行。 |

## 4. 关键架构偏差与优化必要性

### 4.1 缺少统一业务任务契约（P0）

**代码事实**：`SemanticTaskRuntime` 持久化一次执行的任务运行；`PersistentFormTaskRuntime` 单独持久化表单任务。`ConversationService` 发现写操作确认后，直接启动 Form Runtime 重新处理，而不是在同一任务聚合中转化任务类型。

**影响**：查询/分析/填单的上下文、任务 ID、确认、恢复、审计和用户纠错无法天然连续；这正是用户感到“像多个功能，而不是一个助手”的根因之一。

**优化方案**：以 `BusinessTask` 取代“请求一次执行”作为唯一任务聚合，统一承载：

```text
goal + subject scope + semantic slots + target candidates + execution plan
+ evidence + interaction + stateVersion + run/attempt + audit references
```

任务种类可以是 `query / analysis / form / workflow / import / general_conversation`，但它们必须共享一个根 `taskId`、状态机、续接、取消、确认、事件和恢复模型。`FormTask` 应成为 `BusinessTask` 的领域子状态，而不是 Web 中转时新建的旁路任务。

### 4.2 “意图分类”应升级为可解释的任务理解（P0）

**代码事实**：当前执行主线先做路由、意图分类和元数据匹配；模型计划只能在受限范围内调整读取意图。缺少持久化的“用户到底要什么”结构化对象。

**影响**：同一句“看一下本月转化”可能缺对象、转化定义、时间字段、分母、可见范围或交付形式；一次分类无法判断哪一个缺口最阻塞，也无法在用户补充后稳定复用已确认信息。

**优化方案**：新增 `TaskUnderstanding`，由模型提出候选、由元数据和规则裁决：

```text
action: 查询 | 分析 | 新增 | 修改 | 流程处理
target: 应用/对象/表单/流程候选及置信度
scope: 时间、组织、负责人、记录范围、数据权限
semantics: 指标、维度、筛选、比较、关联、输出偏好
completion: 用户想得到的报告、草稿、回执或待办处理结果
risks: 写入、权限、歧义、口径、数据量和契约风险
```

澄清策略应采用“最小阻塞缺口”原则：只有当候选对象不唯一、口径无法确定、必填字段缺失或权限/风险阻断时才提问；每个问题要稳定引用 slot/field，回答后只更新相关部分，不能要求用户重新描述整个目标。

### 4.3 元数据检索需要升级为“能力图 + 置信度消歧”（P0）

**代码事实**：本地检索和执行计划已经使用元数据；但 `LocalMetadataFormSchemaProvider` 会从可用候选中选择一个对象，并包含“线索 → 私海线索”的特定映射。字段槽位只保留非系统字段的基础类型/必填信息，枚举、关联、联动和默认值没有来自真实 Provider Schema 的统一来源。

**影响**：错误定位表单比“多问一个问题”更危险；硬编码对象映射也违反通用云枢 Skill 边界。仅有对象/字段索引不足以推理业务关系、可执行能力和字段约束。

**优化方案**：构建并版本化 `MetadataCapabilityGraph`：

```text
App → Entity/Form → Field(role/type/enum/default/permission)
Entity ↔ Relation ↔ Entity
Entity/Form/Workflow → Registered Provider Capability → Contract Version
```

每个候选返回匹配证据、置信度和消歧阈值。候选分差不足或跨应用冲突时必须 `AMBIGUOUS_TARGET`，让用户选择；禁止框架层的具体对象映射。字段角色（名称、日期、金额、状态、负责人、关联键等）可由元数据、命名、历史已验证映射提出候选，但只有被元数据/契约验证后才能进入计划。

### 4.4 通用分析的路由与计算仍需解耦（P0）

**代码事实**：`YunshuScenarioInsightReportTemplate` 内部已实现多对象、关系、时间和图表的元数据驱动分析，并声明报告模板为可选覆盖；但其 `supports()` 仍以问句关键词决定是否进入洞察。主 Runtime 不满足该条件时会退回普通读取或返回“未安装分析能力”。

**影响**：用户更换问法、对象或关注维度后，系统可能失去分析能力；这与“模板只做个性化，通用能力默认可用”的产品目标不一致。

**优化方案**：新增与模板无关的 `AnalysisSpec` 和 `GenericAnalysisEngine`：

```text
population + timeRange + measures + dimensions + comparisons
+ relationPath + aggregation + anomalyPolicy + evidenceRequirements + renderHints
```

执行顺序固定为：解析需求 → 在能力图中验证对象/字段/关系 → 生成受限查询图 → 取数/关联 → 确定性统计 → 数据质量检查 → 生成图文报告。模型只能解释结论、提出可能的指标和后续问题，不能计算或补造数据。报告模板只能在已完成的 `AnalysisSpec + EvidenceBundle` 上覆盖指标、规则或版式，不能决定是否可以分析。

### 4.5 表单任务尚未达到“理解并操作”的闭环（P0）

**代码事实**：真实元数据字段已经能形成局部草稿；确认票组件具备绑定和一次性消费语义；但 `YunshuProvider` 的 `loadFormSchema/validateDraft/createRecord/updateRecord/submitAction/getOperationResult` 默认未验证，`confirm` 最终仍返回 `CONTRACT_UNVERIFIED`。

**影响**：系统可以安全地“帮用户整理草稿”，但还不能可靠地“帮用户完成云枢操作”。这是正确的安全底线，不是模型能力问题。

**优化方案**：以真实云枢 Provider Contract Registry 逐能力开放：

1. 获取真实表单 Schema（字段、必填、枚举、关联、默认、联动、字段权限）；
2. 服务端与云枢双层校验草稿；
3. 确认前展示字段差异、影响范围、来源、风险与计划哈希；
4. 使用远端幂等键创建/更新/提交，并读取真实回执；
5. 超时进入 `UNKNOWN`，由回查、outbox 和对账任务收敛，禁止盲目重试。

任何 Provider 能力未完成录制回放和真实 E2E 前，都只开放草稿或预览。

### 4.6 记忆目前是“可存”，而不是“可帮助任务”（P1）

**代码事实**：`MemoryService` 已支持 `SESSION / USER / SYSTEM` 存储、TTL 和人工维护；当前 `ConversationService` 注入它主要用于删除会话，未在主任务构建中调用 `recallForPrincipal`，也未形成“用户纠正 → 验证 → 记忆 → 下次任务使用”的闭环。

**影响**：用户每次仍需重复说明偏好的组织、常用范围、报告口径或已确认对象；系统无法从用户纠正中稳定变好。

**优化方案**：建立受控的 `TaskMemoryContext`：只召回与当前目标、主体、租户和权限相关的已验证记忆；按来源、置信度、有效期、适用对象和用户可见性排序。允许记忆帮助理解和建议，但禁止决定权限、绕过确认或替代实时数据。写入记忆必须来自用户显式设置、用户确认的纠正，或已验证的成功任务，并支持查看、撤销和过期。

### 4.7 多 Skill 路由还未成为平台能力（P1）

**代码事实**：`SkillCatalog` 与受审核 Markdown Manifest 已存在，但 Web 调用路径固定传入 `yunshu-business-system`；通用对话和云枢处理虽可分流，却没有统一的“显式 Skill → 已发布 Manifest → 云枢能力匹配 → 通用对话”路由决策记录。

**影响**：其他 Markdown Skill 虽可登记，尚未自然参与用户任务；CPClaw 难以成长为多 Skill 助手。

**优化方案**：增加 `SkillRouter`，输出可审计的候选 Skill、能力匹配、授权结果和路由理由。跨 Skill 只能由明确的 `TaskPlan` 调用已审核能力，禁止模型依据文本直接选择任意 Executor。云枢 Skill 在没有能力命中时不得被误调用。

### 4.8 长任务、恢复与质量评估不足（P1）

**代码事实**：语义任务和草稿已持久化，事件也有存储基础；但 Form Runtime 的 `events` 明确未开放，真实写入后的 `UNKNOWN` 回查、outbox/worker、租约和跨实例恢复尚未形成可验证链路。

**影响**：长分析、批量处理、提交超时或宿主断线时，用户看不到可靠的“正在做什么、能否恢复、是否已经提交”。

**优化方案**：使用持久化任务队列和 Worker，把交互请求与执行尝试分离；每次尝试拥有独立 `attemptId`，但全程保持 `taskId`。增加查询、事件、恢复、取消、对账 API。建立离线评测集并长期监控：对象命中率、澄清有效率、字段填充准确率、关系取数完整率、结果口径正确率、重复写入率、UNKNOWN 收敛时间和用户纠错率。

## 5. 目标架构

```text
CPClaw Web / WorkBuddy / OpenClaw / CLI
                 │  Host adapter: 展示、输入、确认、续接
                 ▼
        BusinessTask Gateway
     身份 · 幂等 · 任务状态 · 事件 · 审计
                 ▼
      Task Understanding & Skill Router
  目标/范围/交付物/风险/缺口 · 已审核 Skill 选择
                 ▼
       Yunshu Semantic Runtime
  能力图发现 → 最小追问 → 受限计划 → 计划校验
                 ▼
  Query/Analysis/Form/Workflow Executors
  确定性计算 · 草稿校验 · 确认票 · UNKNOWN 对账
                 ▼
       Provider Contract Registry
  元数据/表单/API/权限/幂等/回查的真实契约
                 ▼
              CloudPivot
```

贯穿所有层的是 `EvidenceBundle`：每一项结论、字段建议、草稿值和执行回执都必须可追溯到用户输入、已验证元数据、实时数据、系统默认值或已确认记忆。

## 6. 推荐实施优先级与验收

| 阶段 | 目标 | 主要交付物 | 最小验收 |
| --- | --- | --- | --- |
| A：助手内核 | 消除单轮/双 Runtime 语义 | `BusinessTask`、`TaskUnderstanding`、统一状态机和迁移适配器 | 同一 `taskId` 可从“新增线索”追问、草稿、确认、恢复；Web/MCP 不创建旁路任务。 |
| B：通用理解与分析 | 任何已验证对象都能产出可信报告 | `MetadataCapabilityGraph`、`AnalysisSpec`、`GenericAnalysisEngine` | 对线索、商机、项目、收款等不同真实对象，不增加框架分支，均能说明对象、关系、口径、图表和缺口。 |
| C：真实操作闭环 | 能安全完成云枢写入 | Provider Contract Registry、真实 Schema/写入/回查、UNKNOWN 对账 | 真实字段填单可创建、回查；重复确认不重复创建；超时可收敛。 |
| D：成长与扩展 | 从单 Skill 变成可持续学习的助手 | `TaskMemoryContext`、`SkillRouter`、Markdown Skill 发布治理、评测平台 | 用户确认的偏好能在下一任务中生效；未发布 Skill 不可路由；路由与记忆均可解释/撤销。 |
| E：多宿主交付 | CPClaw 与外部 AI 效果等价 | Remote MCP/HTTP、OIDC/JWT、HostCapabilityProfile、运行监控 | CPClaw Web 与 WorkBuddy 对同一任务返回一致的事实、缺口、确认和回执；不经过 CPClaw UI。 |

## 7. 不应采用的优化方式

- 不为“线索、商机、项目、收款”分别新增固定流程或关键词分支；应通过能力图和 `AnalysisSpec` 处理。
- 不将更多模型调用当作可靠性方案；模型负责理解候选和解释，计划、计算、权限和写入必须可验证。
- 不因体验要求绕过确认、幂等、回查或 `UNKNOWN`；“丝滑”来自少而准的追问和可恢复的任务，不来自静默执行。
- 不让外部 AI 宿主自行补字段、拼云枢 API 或解释系统事实；外部宿主只直连 Yunshu Skill Gateway。
- 不把记忆当作权限或实时事实；记忆只能辅助理解，实时云枢数据和当前权限永远优先。

## 8. 评审判定

| 维度 | 判定 | 说明 |
| --- | --- | --- |
| 架构方向 | 可行 | 元数据、统一任务、Provider、安全和宿主解耦方向正确。 |
| 当前“助手感” | 不足 | 任务理解、连续推进、通用分析和记忆闭环尚未成为统一核心。 |
| 当前查询/分析 | 部分可用 | 可基于真实元数据和现有报告引擎工作，但存在关键词路由、能力边界和关系语义泛化不足。 |
| 当前智能填单/操作 | 草稿级可用，提交不可承诺 | 草稿与安全阻断存在；真实写入/回查/UNKNOWN 未完成。 |
| 当前多 Skill/外部宿主 | 设计可行，生产未完成 | Manifest、协议和 Gateway 设计已有，可信身份、Remote MCP、跨网络 E2E 未完成。 |
| 是否建议直接做局部功能修补 | 不建议 | 应先冻结 A、B 阶段的核心对象和契约，再按阶段改造。 |

最终建议：先以“统一业务任务 + 通用元数据能力图 + 通用分析规格”完成架构收敛，再接入真实 Provider 写能力。这样才能使 CPClaw 对不同业务对象保持通用，又能在真实云枢上做到少追问、可执行、可恢复、可解释。

## 9. 专家评审记录（2026-09-10）

### 平台架构评审

判定：方向可行，需先冻结 P0 契约。重点意见：`SemanticTaskRun`、`FormTask` 和 Web 写操作之间目前存在旁路关系；必须引入统一 `BusinessTask` 聚合、能力图、SkillRouter 和 Provider Contract Registry。外部宿主的 Remote MCP、标准 stdio MCP、可信身份和跨实例恢复不能以现有兼容实现代替。

### 后端与运行时评审

判定：可以进入架构实现阶段，不能进入生产交付。重点意见：任务运行缺少完整的状态版本 CAS；空的 `clientRequestId/turnId` 在 MySQL 唯一索引下仍可能重复；同步网络执行缺少 `TaskAttempt + worker lease + outbox`；确认票消费后还需要依赖 Provider 幂等和回查处理失败；事件、表单修订及旧迁移数据需要明确外键、归档和回填策略。

### 产品体验与安全评审

判定：助手体验方向可行，必须以跨宿主事实等价和安全门禁验收。重点场景包括：最小缺口追问、对象歧义消歧、通用分析自动取关联数据并生成图文报告、用户纠正经确认后形成可撤销记忆、宿主内确认、`UNKNOWN` 对账和 WorkBuddy/OpenClaw 直连 Skill Gateway。外部主体映射、租户隔离、敏感日志脱敏、重放防护和 HostCapabilityProfile 是硬条件。

### 汇总结论

三方结论一致：

1. 方案可以进入“契约与测试先行”的分阶段开发；
2. 当前代码不能标记为 `release-ready`；
3. 第一开发阶段必须完成 `BusinessTask + TaskUnderstanding + AnalysisSpec` 的协议和持久化边界，并以 Mock Provider 闭环验收；
4. 在真实 Provider 写入、回查、UNKNOWN 对账、OIDC/JWT 和跨宿主 E2E 通过前，只开放查询、分析、草稿和确认计划。

## 10. 首个实施变更

已将通用分析入口调整为：只要请求已经被 Runtime 判定为 `analyze_data` 或 `summarize_data` 且对象和计划通过校验，即进入 Yunshu 通用分析能力；专项模板只负责后置增强，不再决定分析能力是否存在。该变更仍需通过后端回归测试和真实元数据验证。

## 11. 第一阶段冻结契约：BusinessTask 与 TaskUnderstanding

第一阶段的实现必须先满足以下契约，代码可以分步迁移，但不允许再增加旁路任务。

### 11.1 BusinessTask

```json
{
  "taskId": "stable-opaque-id",
  "taskKind": "query|analysis|form|workflow|import|general_conversation",
  "status": "accepted|needs_input|draft_ready|validation_error|ready_to_submit|confirmation_required|running|submitted|completed|partial|failed|unknown|manual_review|cancelled",
  "stateVersion": 3,
  "tenantId": "trusted-tenant",
  "subjectId": "trusted-subject",
  "hostId": "registered-host",
  "goal": "用户原始目标",
  "understanding": {},
  "plan": {},
  "evidence": {},
  "interaction": {},
  "completion": {},
  "activeAttemptId": "attempt-id",
  "idempotencyKey": "stable-key",
  "auditRef": {}
}
```

约束：`taskId` 在澄清、补充、草稿、确认、提交、断线恢复和对账期间不变；`attemptId` 只表示一次执行尝试。所有状态变更必须经过白名单转移并使用 `stateVersion` CAS；旧版本输入返回最新任务和 `STATE_VERSION_CONFLICT`。

### 11.2 TaskUnderstanding

```json
{
  "action": {"value": "analysis", "confidence": 0.92, "source": "model+rule"},
  "targets": [{"ref": "metadata-ref", "label": "业务对象", "confidence": 0.88, "evidence": ["..."], "selected": true}],
  "scope": {"timeRange": {}, "organization": {}, "recordFilter": {}, "visibleDataScope": "current_subject"},
  "semantics": {"measures": [], "dimensions": [], "comparisons": [], "relations": [], "output": "chart_report"},
  "deliverables": [],
  "missingSlots": [],
  "risks": [],
  "understandingVersion": 1
}
```

模型只能提出候选；元数据、权限和 Provider 契约服务负责裁决候选是否可执行。缺少信息时，`missingSlots` 必须按阻塞程度排序，宿主只展示最小问题集合。用户回答只更新对应 slot，不覆盖已确认的对象、字段和范围。

### 11.3 统一命令接口

所有宿主继续使用 `yunshu-skill/1.0`，但 Runtime 内部必须映射为以下操作：

```text
startTask(goal, context) -> BusinessTask
applyUserInput(taskId, slotAnswers, expectedStateVersion) -> BusinessTask
updateDraft(taskId, fieldPatch, expectedStateVersion) -> BusinessTask
confirmTask(taskId, confirmationTicket, expectedStateVersion) -> BusinessTask
getTask(taskId) -> BusinessTask
appendTaskEvent(taskId) -> TaskEvent
```

Web 适配层不得因为识别到写操作而再次调用 `PersistentFormTaskRuntime.start` 创建旁路任务。表单字段和草稿必须挂在原 `BusinessTask` 下；旧 `SemanticTaskRun` 与 `FormTask` 只能作为迁移期投影或兼容读取。

### 11.4 第一阶段验收

1. 同一目标从 `start → needs_input → draft_ready → confirmation_required` 全程只有一个 `taskId`；
2. Web、MCP、HTTP 对同一目标返回同一理解对象、缺口、字段来源和版本；
3. 空 `clientRequestId`、重复请求、并发旧版本输入均不会产生第二个执行尝试；
4. 对象候选不唯一时返回候选和证据，不静默选择；
5. 普通对话创建 `general_conversation` 任务或明确记录为无需云枢，Provider 调用次数为 0；
6. 用户纠正字段/口径后只修改对应 slot，并产生可审计事件；
7. 服务重启或宿主断线后按原 `taskId` 恢复，不要求用户重新描述目标。

## 12. 2026-09-13 独立复审增量结论

本轮由架构、后端安全、QA/交互三位未参与上一轮实现的专家独立复审最新代码。结论不是“已通过”，而是“可继续分阶段开发，尚不具备生产交付条件”。

### 已落地的防回归收敛

- Web 只有 `create_data` 可以进入表单运行时；`update_data`、`delete_data`、`workflow_action` 不再被伪装成新建表单。
- 表单 `start` 强制非空 `clientRequestId`，避免空幂等键绕过唯一约束。
- 表单终态禁止草稿更新和重复取消；取消不再以相同 `draftVersion` 写入重复修订。
- 已修复 `YunshuTaskEnvelope` 字段变更导致的编译错误，并对先前错误的“全量测试通过”记录进行了更正。

### 仍然阻断交付的问题

1. `findByIdempotencyKey → create` 不是原子操作，并发启动可能产生唯一键异常；必须改为数据库原子获取/创建并回放原结果。
2. 确认票消费、Provider 写入、结果回查没有执行记录和原子协议；当前 `provider-write-verified` 不能替代真实 Provider 实现，仍须保持 fail-closed。
3. 稳定业务任务、执行 attempt、命令 requestId 尚未完全统一；续接票先消费后建子任务存在响应丢失后无法重放风险。
4. 外部宿主尚无真实 JWT/OIDC/签名身份解析；当前 `X-Yunshu-Host-Id` 只完成宿主路由，不构成生产认证。
5. `mcp-adapter/cpclaw-yunshu.mjs` 是 Canonical JSON-line 转发器，不是可直接宣称兼容标准 MCP 的完整 `initialize/tools/list/tools/call` Server。
6. 元数据字段类型、枚举、关联、默认值仍未完整传递到前端和 Provider；自然语言隐含字段抽取仍只能作为候选，不能直接视为已确认值。
7. 通用分析的无模板分支和事件回放仍未达到产品目标；必须补充“项目/收款/合同”等非线索场景和断线恢复验证。

因此本阶段的发布门禁更新为：只读查询、通用分析草稿、表单草稿和确认计划可继续验证；真实云枢创建、标准 MCP、外部宿主生产接入和生产发布均保持 `NOT_READY`。

## 13. 2026-09-13 云枢写入契约探查结论

通过当前云枢环境前端资源的只读静态探查，确认运行时表单 API 为：

- `GET /api/runtime/form/load`
- `POST /api/runtime/form/save`
- `POST /api/runtime/form/submit`
- `GET /api/runtime/form/getReplayToken`
- `GET /api/runtime/form/loadNew`

同时确认 `/api/app/businessrule/create` 属于业务规则定义管理，不是新增业务记录接口，禁止接入智能填单 Provider。

当前仍未从动态表单组装代码中确认 `save`/`submit` 的完整请求体、关联字段编码、回放令牌绑定和真实记录标识返回结构。因此代码只增加了 `CloudPivotConnector.saveForm/submitForm` 的受控边界和 `yunshu_operation_executions` 执行账本（Flyway V34），没有把猜测的 payload 接到确认按钮，也没有执行真实写入。真实 Provider 投产门禁仍未通过。
