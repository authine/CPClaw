# OpenClaw 云枢领域委派与证据契约技术方案

> 状态：最终架构基线 v2.0｜适用范围：CPClaw Web、MCP、Remote API、CLI 以及 OpenClaw 类宿主接入。

## 1. 目标与结论

CPClaw 不替代 OpenClaw 的主智能体能力，也不退化为云枢原子查询 API。两者职责如下：

```text
OpenClaw：用户目标理解、跨领域推理、最终自然语言表达
CPClaw：云枢语义解析、元数据/字段/关系定位、聚合取数、证据准备、权限与执行边界
```

一次用户任务必须采用“领域委派”而不是“先取数据、再由宿主猜缺口、反复补查”：

```text
OpenClaw 生成 DelegationSpec
  → CPClaw 一次完成云枢领域证据准备
  → 返回 EvidenceBundle + EvidenceCompletion
  → OpenClaw 基于事实证据完成最终回答
```

CPClaw Web 可使用同一内核生成完整报告；OpenClaw/MCP 默认使用 `agent_evidence` 模式，由 OpenClaw 主导最终表达。

## 2. 示例任务的闭环

用户输入：

> 帮我看看今年项目的整体情况，找出风险，并把高风险项目的负责人列出来

OpenClaw 在本轮内部形成：

```json
{
  "protocolVersion": "cpclaw-delegation/1.0",
  "goal": "分析今年项目整体情况并识别高风险项目",
  "deliverables": [
    {"id": "overall_summary", "required": true},
    {"id": "risk_basis", "required": true},
    {"id": "high_risk_projects", "required": true},
    {"id": "project_owners", "required": true},
    {"id": "scope_and_caveats", "required": true}
  ],
  "constraints": {
    "period": "今年",
    "visibleScope": "当前账号可见范围"
  },
  "conversationId": "openclaw-conversation-id",
  "turnId": "openclaw-turn-id",
  "clientRequestId": "stable-request-id"
}
```

CPClaw 内部完成：

```text
定位项目实体
 → 定位时间、阶段、状态、更新时间、负责人及关系
 → 分页/聚合读取数据
 → 计算通用风险信号
 → 建立项目与负责人的关系映射
 → 校验字段、关系、覆盖范围和数据口径
```

OpenClaw 不需要知道 `schemaCode`、`apiCode` 或云枢内部字段编码。

## 3. DelegationSpec

`DelegationSpec` 是用户交付要求，不是云枢执行计划。它至少包含：

- `goal`：用户目标；
- `deliverables`：必需/可选交付项及验收条件；
- `constraints`：时间、范围、过滤和展示要求；
- `contextRefs`：已有结果或用户明确引用；
- `presentationMode`：`agent_evidence` 或 `cpclaw_report`；
- `conversationId`、`turnId`、`clientRequestId`；
- `continuationToken`：仅用于 CPClaw 明确允许的后续任务。

没有结构化 `DelegationSpec` 时，CPClaw 可以依据原话生成默认交付项，但必须保存规范化后的规格并用于完成度校验。

## 4. EvidenceBundle 与 EvidenceCompletion

CPClaw 返回事实证据，而不是要求 OpenClaw 再次猜测字段或数据范围：

```json
{
  "evidence": {
    "scope": "今年、当前账号可见项目",
    "facts": [],
    "metrics": [],
    "riskSignals": [],
    "records": [],
    "relations": [],
    "coverage": {},
    "caveats": [],
    "provenance": []
  },
  "completion": {
    "state": "complete",
    "answerReady": true,
    "deliverables": {
      "overall_summary": "fulfilled",
      "risk_basis": "fulfilled",
      "high_risk_projects": "fulfilled",
      "project_owners": "fulfilled",
      "scope_and_caveats": "fulfilled"
    },
    "missingEvidence": [],
    "continuationAllowed": false
  }
}
```

状态定义：

| 状态 | 语义 | 是否允许自动重试 |
| --- | --- | --- |
| `complete` | 所有必需交付项具备足够证据 | 否 |
| `partial` | 部分交付项缺失，但已有内容可安全回答 | 否 |
| `needs_input` | 用户补充后可继续 | 仅带 continuation token |
| `blocked` | 权限、元数据或真实契约不足 | 否 |
| `confirmation_required` | 已有完整写计划，等待可信确认 | 仅确认票继续 |
| `failed` | 技术失败 | 仅显式 retryable 时允许 |
| `cancelled` | 用户或系统取消 | 否 |

`partial` 是终态，不得被映射为失败，也不得因为宿主认为“表达不够漂亮”而重复执行。

## 5. OpenClaw 与 CPClaw 的职责边界

OpenClaw 可以基于 `facts`、`metrics`、`riskSignals`、`coverage` 和 `caveats` 组织最终回答，但不得修改：

- 数字和统计口径；
- 数据范围和权限范围；
- 风险信号及其证据；
- 字段覆盖和缺失警告；
- 确认条件和执行结果。

CPClaw 不承担 OpenClaw 的通用跨领域推理，但必须完成云枢领域内的数据准备闭环。分页、关联、字段补查、聚合和证据校验均在 CPClaw 内部完成。

云枢记录、备注和附件中的文本均标记为数据证据，不能改变 `DelegationSpec`、权限、确认或工具策略，防止业务数据提示注入。

## 6. 同轮防重复与任务续接

`hostAction` 和提示词不是安全控制。Task Gateway 必须按以下键建立 `TurnExecutionLease`：

```text
externalPrincipal + conversationId + turnId
```

规则：

1. 相同 `clientRequestId` 重放直接返回原结果；
2. 同一 `turnId` 没有有效 `continuationToken` 的重复调用直接 replay 终态；
3. 不重新访问云枢，不创建第二个业务任务；
4. 只有用户新增信息、明确扩大范围或可信确认票才允许继续；
5. 继续任务沿用原 `taskId`，并记录新的 continuation 事件。

## 7. 统一任务内核

```text
Web / MCP / Remote API / CLI
        ↓
Task Gateway
        ↓
TaskSpec Normalizer
        ↓
Context + Identity + Memory
        ↓
Skill Resolver
        ↓
Evidence Planner / Plan Builder
        ↓
Policy / Permission / Confirmation
        ↓
Execution Coordinator
        ↓
Evidence Completion Validator
        ↓
Artifact / Answer Composer
        ↓
Task Lifecycle / Event / Audit
```

Web、MCP 和 CLI 只能是传输适配器，不得各自实现云枢意图分类、分页、流程 API 映射或结果补查。

## 8. Skill 与云枢 Provider

云枢按照普通 Skill 规范接入：

```text
Yunshu Query Skill
Yunshu Analysis Skill
Yunshu Mutation Skill
Yunshu Workflow Skill
Yunshu Form Skill
Yunshu Import Skill
```

商机、项目、客户等均为运行时元数据对象，不能新增 Java 业务分支。分析和风险分别使用通用 `AnalysisPlan`、`RiskAnalysisPlan`，字段、枚举、关系和 API 只能来自已同步并验证的 Registry。

Markdown Skill 只作为声明输入：解析、Schema 校验、来源/签名校验、管理员审核、绑定白名单 Executor 后才能发布；不得直接执行任意 URL、脚本或凭据。

## 9. 对外协议

MCP 默认提供单一业务入口：

```text
cpclaw_cloudpivot_agent
```

旧名称 `yunshu_handle_request` 保留兼容。生命周期能力通过受保护的状态、事件、继续、确认和取消资源提供，不向模型暴露云枢原子工具。

MCP、Remote API、CLI 共用同一 `TaskGateway`。MCP `content.text` 必须是完整 Markdown 降级结果，`structuredContent` 必须包含事实证据和完成契约；进度通知只是增强，不是终态依赖。

### 9.1 宿主终态决策（必须实现）

宿主先读取 `structuredContent.completion`，再读取 `evidence` 与 `hostAction`，不得依据 Markdown 文本长短重新判断证据是否充分。`agent_evidence` 模式下，完成态使用 `compose_answer` 交给 OpenClaw 组织最终表达；`cpclaw_report` 模式下使用 `respond_directly` 直接展示 CPClaw 结果。`partial` 是终态，不得因表达不够完整而自动补查。只有 `needs_input`、可信确认票、用户新增约束或 `task.retryable=true` 的技术失败允许继续。

## 10. 身份、记忆与确认

外部 OpenClaw 的安装标识不是用户身份。跨会话记忆、确认和写操作必须绑定可信 `externalPrincipal + tenantId`。无可信主体时只开放只读和确认计划。

记忆分层为 `SYSTEM / USER / SESSION / TASK`。设置页只暴露 `SYSTEM`（仅超级管理员）和 `USER`（当前用户）两类可管理长期记忆；`SESSION` 仍落在 `agent_memories` 用户记忆存储表中，但仅绑定当前 `conversationId + ownerPrincipal + tenantId`，只供运行时上下文召回，不返回设置接口，也不提供人工编辑入口。`TASK` 按任务生命周期管理。所有可管理记忆需具备优先级、TTL、来源和审计。

写操作确认票绑定：

```text
planHash + metadataVersion + permissionSnapshot + principal + expiresAt + idempotencyKey
```

第二次 MCP 工具调用不能作为人工确认。

## 11. P0 验收矩阵

1. 复合任务初始只发起一次领域委派；
2. 同轮重复调用 replay 原终态，云枢调用次数保持 1；
3. OpenClaw 能依据证据包完成最终风险表达；
4. 缺少负责人或风险字段时明确返回缺口，不伪造；
5. 用户补充后使用 continuation 继续同一任务；
6. Web 与 OpenClaw 的事实、范围、口径和警告一致；
7. 项目、商机或其他实体替换不新增 Java 业务分支；
8. 无可信身份时不允许外部写入；
9. 断线后可回放事件和最终结果；
10. 云枢数据文本不能改变任务策略。

## 12. 实施顺序

1. 冻结本方案与 `DelegationSpec / EvidenceBundle / EvidenceCompletion / TaskExperienceEnvelope v2`；
2. 抽取统一 `TaskGateway`、任务状态和事件回放；
3. 增加 `TurnExecutionLease` 与 continuation token；
4. 将 `McpSemanticTaskService` 和 `AgentOrchestrator` 降为适配器；
5. 将云枢查询、分析、操作和流程改为标准 Skill Provider；
6. 增加身份/租户/确认票；
7. 实现 SYSTEM / USER / SESSION 记忆；
8. 增加 Markdown Skill 安装治理；
9. 按真实云枢契约逐项开放写入、流程处理、Action 和导入。

## 13. 本轮代码落地与边界

本轮已落地：

- `SemanticTaskRun` 持久化规范化后的 `TaskSpec`、`completion` 和 `evidence`；
- 同一 `clientRequestId` 或 `turnId` 的终态直接 replay；同一轮已有 running 任务时返回 `blocked/turn_in_flight`，不再次访问云枢；
- MCP 同时暴露推荐名称 `cpclaw_cloudpivot_agent` 和兼容名称 `yunshu_handle_request`，两者使用同一执行链；
- 当执行器尚未提供逐项证据时，运行时将结构化任务标记为 `partial` 并列出证据缺口，不把“有一张结果表”伪装成复合任务完成。

已补齐基础实现：`TaskGateway` 已成为 MCP 和 Web 的生命周期入口；Web 通过 `WebTaskExperienceAdapter` 将统一 `TaskExperienceEnvelope` 映射为旧 `AgentResponse` 展示 DTO，不再保留第二条云枢执行链；V22/V23/V24 提供任务幂等、并发冲突保护、签名 continuation ticket、父子任务关系、任务状态/事件/取消 API，以及默认用户和分层记忆存储；新增 `cli/cpclaw.mjs` 复用 Remote/MCP API。云枢复合任务的真实风险信号/关系补查仍需按真实元数据逐步完善，Skill Markdown 的持久化发布工作台仍需继续建设。

## 14. 当前无登录阶段的默认身份

在正式登录/OIDC 接入前，系统通过可替换的 `PrincipalContextService` 提供默认用户：

```text
principalId: huangj
username: huangj
displayName: 黄杰
phone: 18124691161
tenantId: default
```

该身份用于用户记忆、任务幂等、审计和确认归属；它不等同于云枢密码，也不允许从姓名或电话号码推导云枢凭据。登录接入后只替换身份解析器，不改变任务协议和存储模型。
