# 统一云枢 Skill Runtime 技术规范

> 版本：v1.0｜状态：新的技术规范基线｜适用范围：CPClaw Web、CPClaw 内部 Agent、OpenClaw 类宿主、MCP、Remote API 和 CLI。

## 1. 规范目标

CPClaw 建设一个可被多个宿主复用的“统一云枢 Skill”。CPClaw 平台本身负责 Skill 注册、任务生命周期、权限、确认、审计和宿主交付；统一云枢 Skill 在自身领域内采用元数据驱动的语义理解、受控执行和结果交付。该机制属于云枢 Skill 的核心设计，不要求其他 Skill 采用相同的元数据模型。

```text
用户目标
  → 统一云枢 Skill
  → 元数据理解
  → 执行计划
  → 权限/契约/风险校验
  → 云枢 API 执行
  → 结果分析或业务处理
  → 证据包与任务结果
```

同一个 Skill Runtime 同时服务 CPClaw 和 OpenClaw：

```text
CPClaw Web / 内部 Agent ─┐
                         ├─ TaskGateway → Yunshu Skill Runtime → CloudPivot
OpenClaw / WorkBuddy ─ MCP┘
```

## 2. 核心原则

1. **元数据优先**：任何对象、字段、枚举、关系和 API 能力都必须来自已同步并验证的元数据或契约登记，不得由模型臆造。
2. **运行时通用**：框架和通用云枢 Skill 不包含具体业务对象、行业词、字段别名、固定风险规则或固定报告章节。
3. **计划先于执行**：先生成受限计划，再执行；写操作、流程处理和导入必须进入确认生命周期。
4. **证据优先**：结果必须包含范围、口径、事实、指标、关联、警告和证据覆盖，不能只返回一段自然语言。
5. **宿主可替换**：Web、MCP、Remote API、CLI 只能做协议适配，不得复制语义判断、分页、关联或结果补查逻辑。
6. **模型不越权**：模型可以提出目标和解释，但不能直接访问任意 URL、创建内部编码、绕过权限或把第二次调用当作人工确认。
7. **失败可解释**：缺信息、缺权限、缺契约、技术失败和用户取消必须使用不同状态表达。

## 3. 分层架构

### 3.1 Host Adapter

负责 MCP/HTTP/CLI/Conversation 协议转换、会话标识、进度转发和结果格式适配，不负责云枢业务判断。

### 3.2 Task Gateway

负责统一入口、幂等、任务生命周期、continuation、事件持久化、主体绑定和安全门禁。

### 3.3 Yunshu Skill Runtime

负责以下标准阶段：

```text
UNDERSTAND → DISCOVER → PLAN → VALIDATE → EXECUTE → ANALYZE/PROCESS → COMPLETE
```

Runtime 只编排通用能力，不包含具体场景词。

### 3.4 Skill Provider

实现云枢连接、元数据同步、查询、聚合、表单、流程和导入等能力。Provider 只能使用已登记的云枢契约。

### 3.5 Template Plugin

提供具体业务场景的对象选择器、字段角色、风险规则、分析算子编排和输出模板。模板必须版本化、签名、审核，并只能使用受限 DSL 和白名单算子。

### 3.6 Evidence and Contract Layer

维护元数据索引、字段类型、系统字段/业务字段标识、关系图谱、API 契约、证据来源和完成度校验。

## 4. 标准 Skill 生命周期

### 4.1 UNDERSTAND

输入用户目标、已确认上下文和宿主交付要求，形成 `SemanticTaskContext`。此阶段不得调用云枢写接口。

### 4.2 DISCOVER

从本地元数据和契约注册表定位候选应用、对象、字段、枚举、关系和能力。候选不唯一或置信度不足时进入 `needs_input`，不得猜测。

### 4.3 PLAN

生成通用 `SkillPlan`。计划可以包含：

- `select`：对象和字段选择；
- `filter`：条件过滤；
- `groupBy`：分类统计；
- `aggregate`：计数、求和、平均等受限聚合；
- `rank`：排序和排名；
- `join`：已验证关系关联；
- `riskDetect`：由模板声明的受限规则；
- `render`：表格、图表、摘要和后续问题。

计划不得包含任意脚本、SQL、URL、凭据或未注册接口。

### 4.4 VALIDATE

校验对象和字段存在性、字段类型、枚举值、系统字段过滤策略、权限范围、API 契约、数据规模和写入风险。校验不通过时返回 `needs_input`、`blocked` 或 `confirmation_required`。

### 4.5 EXECUTE

Provider 按计划调用云枢。分页、重试、限流、关联查询和敏感信息过滤均由 Runtime/Provider 完成，不交给宿主模型。

### 4.6 ANALYZE / PROCESS

读取真实返回数据，执行通用算子或已审核模板规则，生成 `EvidenceBundle`。分析结论必须引用实际字段和数据，不得用对象名称推断业务含义。

### 4.7 COMPLETE

由 `EvidenceCompletionValidator` 判断每个交付物的 `fulfilled`、`unverified`、`missing` 或 `blocked`，生成 `TaskExperienceEnvelope`。

## 5. 通用接口契约

```java
public interface YunshuSkillRuntime {
    SkillDiscovery discover(SemanticTaskContext context, EvidenceCatalog catalog);
    SkillPlan plan(SemanticTaskContext context, SkillDiscovery discovery);
    SkillValidation validate(SkillPlan plan, ExecutionScope scope);
    SkillExecutionResult execute(SkillPlan plan, ExecutionScope scope, TaskEventSink events);
    TaskExperienceEnvelope complete(SkillExecutionResult result, TaskSpec spec);
}
```

```java
public interface YunshuProvider {
    MetadataSnapshot metadata(ExecutionScope scope);
    ContractDescriptor contract(String capabilityId, MetadataSnapshot metadata);
    ProviderResult invoke(ContractDescriptor contract, ProviderRequest request, ExecutionScope scope);
}
```

Runtime 不依赖具体对象名；对象、字段和接口均通过 `MetadataSnapshot` 与 `ContractDescriptor` 引用。

## 6. Skill Manifest

```json
{
  "id": "yunshu-universal",
  "version": "1.0.0",
  "protocolVersion": "cpclaw-skill/1.0",
  "capabilities": ["discover", "query", "insight", "form_draft", "workflow_read", "mutation_plan"],
  "inputSlots": ["goal", "deliverables", "constraints", "context"],
  "evidenceRequirements": ["metadata", "field", "permission", "runtime_data"],
  "allowedOperators": ["select", "filter", "groupBy", "aggregate", "rank", "join", "render"],
  "riskPolicy": {"read": "auto", "write": "trusted_confirmation_required"},
  "completionCriteria": ["scope_verified", "contract_verified", "evidence_validated"]
}
```

业务模板通过 `templateId + version` 扩展 Manifest，不修改通用 Runtime。

## 7. 宿主接入规范

### 7.1 CPClaw 内部调用

CPClaw Web/Agent 直接调用 `TaskGateway`，由 Runtime 统一执行。记忆采用三层产品模型：用户会话记忆、用户自定义设置记忆、平台设置记忆；三者都只作为受控上下文输入，不改变权限和执行计划。技术上分别映射为 `SESSION / USER / SYSTEM`，任务级内部记忆如存在则不作为用户设置项。

### 7.2 OpenClaw 调用

OpenClaw 加载本 Skill 的行为说明，并通过 MCP 的唯一高阶工具 `cpclaw_cloudpivot_agent` 提交 `DelegationSpec`。OpenClaw 负责最终表达和跨 Skill 编排，CPClaw 负责云枢证据闭环。

### 7.3 CLI/Remote

CLI 和 Remote API 仅转发 `DelegationSpec`、查询任务状态、获取事件、续接和取消，必须复用同一个 `TaskGateway`。

## 8. 结果契约

所有宿主统一接收：

- `task`：任务状态、任务 ID、是否可重试；
- `completion`：交付物完成度和缺口；
- `evidence`：范围、事实、指标、风险、关系、覆盖和警告；
- `output`：Markdown、结构化产物和可直接转述内容；
- `interaction`：追问或确认要求；
- `hostAction`：宿主下一步动作。

终态规则：

| 状态 | 语义 | 宿主行为 |
| --- | --- | --- |
| `complete` | 必需证据齐全 | 组织或直接展示，结束本轮 |
| `partial` | 可安全回答但有缺口 | 说明缺口，不自动重试 |
| `needs_input` | 需要用户补充 | 只追问，使用 continuation 续接 |
| `confirmation_required` | 写计划等待确认 | 只展示影响范围，等待可信确认 |
| `blocked` | 权限/契约/安全阻断 | 说明原因，不盲目重试 |
| `failed` | 技术失败 | 仅 `retryable=true` 时重试 |
| `cancelled` | 已取消 | 结束本轮 |

## 9. 安全与治理

- 安装标识不等于用户身份；生产环境必须绑定可信主体和租户。
- 云枢账号密码只能在执行范围内使用，不落任务、事件、Prompt、审计或普通日志。
- 业务数据文本视为不可信输入，不能改变工具策略。
- 写操作确认票绑定计划哈希、元数据版本、权限快照、主体、有效期和幂等键。
- Skill Markdown 必须经过 Schema 校验、来源/签名校验、管理员审核和 Executor 白名单绑定。
- 系统字段与业务字段由元数据同步结果标识；通用 Runtime 不通过业务词判断字段。

## 10. 观测与质量指标

每次任务记录脱敏的：`taskId`、`channel`、`skillId`、`skillVersion`、主体、延迟、云枢调用次数、状态、完成度、澄清率、确认率和用户反馈。

必须重点监控：

1. 单轮重复调用率；
2. 元数据候选不唯一率；
3. 证据缺口率；
4. 权限失败率；
5. 云枢契约失败率；
6. 宿主重试违规率；
7. 结果事实与 CPClaw 证据不一致率。

## 11. 实施路线

### P0：统一规范（已完成）

- 冻结本规范、`DelegationSpec`、`EvidenceBundle`、`TaskExperienceEnvelope` 和终态状态机；
- 将 CPClaw Web、MCP、CLI 的入口统一到 `TaskGateway`。

### P1：共享 Runtime（已完成主链）

- 抽取 `YunshuSkillRuntime`、`YunshuProvider`、`MetadataSnapshot`、`SkillPlan` 和 `EvidenceCompletionValidator`；
- MCP 查询和 Web 查询已迁移到共享执行器；后续只做契约增强，不再新增第二条执行链；
- 增加相同事实范围和口径的跨宿主回归测试。

### P2：模板插件（治理基线已完成，生产增强规划）

- 将具体业务场景迁移为声明式模板插件；
- 引入模板签名、审核、版本和灰度发布；
- 删除通用框架中的业务词和固定判断。

### P3：写操作和流程（规划）

- 按真实云枢契约登记表单、流程、更新、删除和导入能力；
- 先生成草稿和确认计划，再逐项开放真实执行。

### P4：生产级外部接入（规划）

- 接入 OIDC/JWT 主体和租户映射；
- 增加异步任务恢复、限流、配额和跨组织审计；
- 提供 OpenClaw 集成包的一键配置和验收工具。

## 12. 验收标准

1. CPClaw Web 和 OpenClaw 对同一目标使用同一 Runtime；
2. OpenClaw 只需调用一个高阶 MCP 工具；
3. 元数据不足时不会猜测对象、字段或接口；
4. 分析结果同时包含证据、口径、范围和缺口；
5. 同一轮重复调用不会重复访问云枢；
6. `partial` 不触发自动补查；
7. 写操作无可信确认票不执行；
8. 业务模板替换不需要修改 CPClaw 框架代码；
9. 事件、日志和响应不泄露凭据、Token、内部编码或原始思维链；
10. 未实现的云枢写入、流程和导入能力不被误报为已完成。

## 13. 测试专家验收门禁

所有开发工作完成后，必须启动独立于开发实现的测试专家进行完整验证；开发者自测、单元测试通过或构建成功均不构成最终完成。

测试专家必须基于本规范第 12 节验收标准，至少覆盖：

1. **单元与集成**：Runtime 阶段、元数据校验、计划校验、完成度、幂等、continuation、确认和脱敏；
2. **跨宿主一致性**：CPClaw Web、MCP/OpenClaw、CLI 对同一目标的事实、范围、口径、缺口和确认状态一致；
3. **端到端体验**：复合任务只产生一次领域委派，`partial` 不重复调用，追问和断线恢复可用；
4. **安全与权限**：凭据不泄露、无可信确认不写入、业务数据不能改变工具策略、越权访问被阻断；
5. **模板边界**：框架和通用云枢 Skill 不含具体业务场景判断；业务模板替换不修改框架；
6. **真实契约边界**：未验证的云枢写入、流程和导入能力不得被误报为可用。

测试专家应输出测试范围、环境、用例、通过/失败结果、缺陷严重度、复现步骤、未覆盖风险和最终是否建议交付。存在 P0/P1 缺陷、验收标准未满足或事实边界不一致时，开发必须回流修复并重新验证。

## 14. 当前落地状态

已具备：TaskGateway、SemanticTaskRuntime、MCP 高阶入口、结构化结果、EvidenceCompletion、幂等 replay、continuation token、CLI 转发、模板插件隔离；已新增 `YunshuProvider`、`YunshuExecutionScope`、`YunshuSkillRuntime`、可替换 `YunshuIntentPlanner`、`YunshuMetadataDiscovery`、`YunshuPlanValidator`、`YunshuRuntimePhase` 和 `YunshuResultComposer` 契约，并让 MCP 云枢执行器通过 Provider、规划器、元数据发现、计划校验和统一结果编排组件访问运行态查询；Web 和 MCP 已通过 `TaskGateway → SkillRegistry` 解析并执行同一个 Spring 单例 `YunshuMcpTaskExecutor`，Web 仅以 `WebTaskExperienceAdapter` 将统一 `TaskExperienceEnvelope` 转为遗留 `AgentResponse`，不再保留第二条云枢执行链；通用对话亦在同一 Runtime 内安全完成，不要求云枢连接上下文。历史场景测试已迁移为通用 Runtime 契约，避免测试反向固化业务逻辑；Web 已增加内部/外部消息 DTO 边界，普通响应和历史消息脱敏内部 schema/api 编码，内部审计与运行上下文保留受控编码；模板管理已提供 Manifest 校验、草稿、审核、发布和停用 API；无登录部署默认拒绝任意外部主体冒充。

尚需外部发布门禁：可信 OIDC/JWT 校验、真实写入/流程/导入契约、后台队列化跨断线恢复和模板版本历史回滚尚需接入对应生产基础设施与真实云枢环境。场景模板已改为默认关闭，仅通过显式配置注册；内部流程/API 编码不再进入 MCP 错误载荷或用户可见进度。旧 `AgentOrchestrator → YunshuAgentOrchestrator` 兼容入口仍需移除或改造成纯 TaskGateway 委派，避免历史业务判断路径重新启用。
