# CPClaw 云枢 MCP 交付架构方案（历史提案）

> 文档类型：历史评审｜状态：已被 `20-universal-yunshu-skill-runtime-spec.md`、`19-openclaw-class-ai-tool-integration.md` 和 `18-openclaw-delegation-evidence-contract.md` 取代。保留用于追溯早期方案，不作为当前实现或验收依据。

> 口径说明：本文中的具体对象名称仅用于早期协议示例，不是框架或通用 Skill 的内置判断。

## 1. 设计原则

1. MCP 是 CPClaw 的核心对外能力，但不是 CPClaw 内部业务模型。
2. 宿主只调用高阶自然语言入口，不接触云枢内部编码和原子接口。
3. Skill 约束能力执行方式，不固化具体业务对象和固定报告模板。
4. 统一语义任务运行时负责理解、规划、证据、执行、确认、审计和结果。
5. 过程展示使用可核验业务事件，不展示原始思维链。
6. 查询结果、分析产物和失败说明必须同时适配结构化宿主和纯文本宿主。

## 2. 总体架构

```text
WorkBuddy / 其他 OpenClaw / CPClaw Web
                │
        Host Adapter Layer
   MCP SSE/HTTP   Conversation API
                │
        Semantic Task Runtime
  Router → Context → Planner → Policy → Executor → Reflector
                │
          Skill Registry
  Conversation / Query / Insight / Form / Workflow / Import
                │
       Evidence & Contract Layer
 Metadata Index / Graph / Field Enum / API Contract Registry
                │
       CloudPivot Execution Scope
  Environment + short-lived user credential + permission boundary
                │
             CloudPivot
```

## 3. 模块职责

### 3.1 Host Adapter Layer

负责协议转换，不负责业务推理。

MCP 适配器负责：

- MCP initialize、tools/list、tools/call；
- SSE 会话和进度令牌；
- 安装标识与终端凭据短期注入；
- 将 `TaskExperienceEnvelope` 转换为 `structuredContent`、Markdown 和进度通知。

CPClaw Web 适配器负责：

- 会话、消息、记忆和前端 SSE；
- 将同一任务结果映射为现有 `AgentResponse` 和报告组件。

### 3.2 Semantic Task Runtime

建议组件：

```text
SemanticTaskRouter
SemanticTaskPlanner
SkillSelector
TaskPolicyEngine
TaskExecutionCoordinator
TaskReflectionService
TaskExperienceComposer
TaskLifecycleStore
```

运行状态：

```text
accepted → understanding → planning → waiting_input
                         │          └→ confirmation_required
                         ↓
                       executing → reflecting → completed
                                      ├→ failed
                                      └→ cancelled
```

`waiting_input` 和 `confirmation_required` 都是可恢复状态，不得被当成失败。

### 3.3 Skill Registry

```java
public interface SemanticSkill {
    SkillManifest manifest();
    boolean supports(SemanticTaskContext context);
    SkillPlan plan(SemanticTaskContext context, EvidenceCatalog evidence);
    SkillExecutionResult execute(SkillPlan plan, ExecutionScope scope, TaskEventSink events);
}
```

`SkillManifest` 至少包括：

```json
{
  "id": "yunshu-data-insight",
  "version": "1.0",
  "inputSlots": ["subject", "metrics", "dimensions", "filters", "period"],
  "evidenceRequirements": ["entity", "field", "enum", "relation", "runtime_data"],
  "artifacts": ["kpi", "chart", "table", "finding", "warning", "follow_up"],
  "risk": "READ",
  "completionCriteria": ["scope_verified", "metric_verified", "result_explained"]
}
```

模型只能推荐 Skill ID 和业务意图；服务端注册表决定是否允许执行。

## 4. 分析 Skill 详细链路

```text
用户目标
 → 识别分析任务
 → 定位主对象与关联对象
 → 读取字段角色、选项和关系
 → 生成 AnalysisPlan
 → 校验指标、过滤条件和统计口径
 → 拉取运行态数据
 → 计算 KPI / 分布 / 趋势 / 关联指标
 → InsightVisualizationPlanner 选择图表
 → 生成 AnalysisArtifact
 → 生成 Markdown 降级摘要
```

### 4.1 AnalysisPlan

```json
{
  "subject": "项目",
  "metrics": [{"name": "项目数量", "field": "verified-field", "aggregation": "count"}],
  "dimensions": [{"name": "项目阶段", "field": "verified-stage-field"}],
  "filters": [{"field": "verified-stage-field", "operator": "in", "values": ["在建"]}],
  "relations": [],
  "scope": "当前账号可见数据",
  "verification": {"fieldVerified": true, "enumVerified": true}
}
```

内部编码只存在于受保护执行计划和审计中，不能进入宿主展示结果。

### 4.2 AnalysisArtifact

```json
{
  "type": "data_insight",
  "title": "在建项目分析",
  "summaryMarkdown": "已确认项目阶段字段和‘在建’取值……",
  "kpis": [],
  "charts": [],
  "tables": [],
  "findings": [],
  "warnings": [],
  "sources": [],
  "followUps": [],
  "confidence": "high"
}
```

无法验证“在建”口径时，`warnings` 必须说明缺少字段或枚举，不得将实体总数替代为在建数量。

## 5. MCP 对外契约

### 5.1 工具清单

`yunshu_handle_request` 是 `tools/list` 中唯一业务入口。终端模型只能调用它，不能被引导调用状态、取消、字段探查或云枢 API。

长任务状态与取消属于平台生命周期能力：CPClaw 可通过受保护的任务查询 API、MCP Resource 或宿主已建立的进度通道提供，不作为业务工具出现在模型可见的 `tools/list` 中。最终结果始终包含完整业务结果和 `visibleTrace`，不能依赖状态通道才可用。

原子查询、字段探查和云枢 API 仅作为服务端执行组件。

### 5.2 请求

```json
{
  "request": "分析系统的在建项目情况",
  "context": [],
  "clientRequestId": "stable-id",
  "conversationId": "host-conversation-id",
  "presentation": {"prefer": "chart_and_summary"}
}
```

### 5.3 返回

```json
{
  "experienceVersion": "1.0",
  "task": {"id": "opaque-id", "status": "completed", "retryable": false},
  "hostAction": {"type": "respond_directly", "allowAnotherMcpCallThisTurn": false},
  "output": {
    "message": "可直接转述的业务结论",
    "artifact": {}
  },
  "interaction": {"type": "none"},
  "visibleTrace": []
}
```

状态与宿主动作必须固定：

| 状态 | 宿主动作 |
| --- | --- |
| `completed` | `agent_evidence` 模式由宿主基于证据组织答案；`cpclaw_report` 模式直接展示结果；两种模式均不再调用 MCP |
| `needs_input` | 只向用户追问，补充后携带 continuation 继续 |
| `confirmation_required` | 展示影响范围并引导确认 |
| `failed` | 展示脱敏错误，仅按 `task.retryable=true` 决定重试 |
| `running` | 等待进度通知；宿主断线时由平台生命周期通道恢复 |

## 6. 云枢执行范围与凭据

定义 `ExecutionScope`：

```text
environmentBaseUrl
principal
tenant
credentialProvider
channel
permissionPolicy
noPersistCredential=true
```

CPClaw Web 使用本地加密凭据 Scope；MCP 使用 SSE 会话内终端凭据 Scope。两者共享执行器，不共享凭据存储。

账号密码不得写入任务表、事件表、MCP 审计、模型 Prompt 或普通日志。未来替换 API Key 或 OAuth，只替换 `CredentialProvider`。

## 7. 持久化模型

### 7.1 semantic_task_runs

保存：任务 ID、入口、安装实例、外部主体、幂等键、脱敏目标、状态、计划摘要、结果摘要、确认引用、开始/结束时间。

### 7.2 semantic_task_events

保存：任务 ID、递增序号、业务阶段、脱敏事件摘要、时间。用于断线恢复、历史回放和过程审计。

### 7.3 skill_versions

保存：Skill ID、版本、Manifest、启用状态、发布人、发布时间、灰度范围。报告结果保存 `skillId + skillVersion`，确保历史结果可解释。

## 8. 安全与风险控制

- 元数据是唯一可执行对象来源，模型不得创建对象编码。
- 低置信度、候选不唯一、字段或枚举未验证时进入追问。
- 查询自动执行；填单、修改、删除、流程处理、导入必须确认。
- 确认绑定计划快照、计划哈希、主体、过期时间和幂等键。
- 外部宿主没有可信身份时，不开放跨会话写入确认。
- 对外事件过滤凭据、内部编码、接口地址、原始记录和原始思维链。

## 9. 可观测性

每个任务关联：

```text
taskId / channel / installationId / skillId / skillVersion
principal / latency / modelTokens / cloudPivotCalls
status / clarification / confirmation / feedback
```

管理页面提供：Skill 调用次数、成功率、澄清率、确认率、Token 消耗、云枢接口耗时、失败原因、用户点赞/点踩和结果口径警告。

## 10. 部署架构

```text
Nginx / API Gateway
        ↓ HTTPS
CPClaw Spring Boot
  ├─ MCP Gateway
  ├─ Semantic Runtime
  ├─ Skill Executors
  ├─ Metadata / Contract Registry
  └─ Audit / Task Store
        ↓
MySQL + optional pgvector
        ↓
CloudPivot + OpenAI-compatible Model
```

MCP 服务与 CPClaw 后端共进程部署，首期不单独拆分 MCP 微服务；当并发、租户隔离或独立扩展需求明确后，再将 Gateway 无状态拆分，运行时和数据库契约保持不变。

## 11. 实施顺序

1. 冻结 MCP `TaskExperienceEnvelope`、状态和宿主动作协议。
2. 将 `McpSemanticTaskService` 降为适配器，抽取 `SemanticTaskPlanner` 和 `ExecutionScope`。
3. 把现有 `InsightReportService` 改造为请求级 Scope，并接入 MCP 分析 Skill。
4. 引入 `AnalysisPlan`、证据校验和 `AnalysisArtifact`。
5. 增加受保护的任务恢复、断线恢复和幂等控制，不向模型公开第二个业务工具。
6. 迁移查询、流程只读、填单草稿、写操作确认和导入作业。
7. 增加 WorkBuddy 端到端验证、Skill 版本灰度和质量看板。

## 12. 验收标准

- WorkBuddy 输入自然语言后，不需要任何云枢编码即可获得业务回答。
- 分析结果至少包含数据范围、指标、口径、图表或表格、结论和警告。
- “在建项目”无法被字段/枚举证实时，不得输出伪造数量。
- 宿主不渲染结构化内容时，Markdown 仍完整可读。
- MCP 断线、重复请求和进度丢失不导致重复执行。
- 写操作未经可信确认不得落库。
- 全链路日志不包含账号密码、Token、内部编码和原始思维链。

## 13. v0.3 首轮落地基线

本轮先完成统一能力的最小闭环，而不复制宿主业务逻辑：

- `InsightDataAccess` 是不持久化的请求级执行范围；CPClaw Web 继续使用本地加密凭据，MCP 使用 SSE 会话内的终端凭据。
- `InsightReportService` 接收该 Scope，因此“我的”过滤、关联分析和云枢数据读取均使用本次 MCP 绑定账号，不回退到管理员或系统默认账号。
- `McpTaskExperienceRenderer` 将洞察报告同时转换为 `output.artifact` 与完整 Markdown；文本宿主也能看到范围、口径、KPI、图表文字数据、警告和追问。
- MCP 请求接收 `conversationId` 和 `clientRequestId`；运行时以不可逆主体指纹隔离幂等结果，不保存账号、密码、云枢地址或内部编码。

尚未在本轮开放的能力包括异步长任务恢复、宿主可信身份/OIDC 映射、跨宿主确认回调和其他写类 Skill。它们必须沿用本章的 `ExecutionScope`、确认计划和统一任务结果契约，不能另建宿主专用执行链路。
