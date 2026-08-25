# 对话式系统操作技术方案（V1）

**文档类型：专项技术方案｜状态：P0 部分已实现，P1–P5 为规划。** 本方案将自然语言任务约束为可验证、可审计、可重放防护的执行契约。已实现能力不能被夸大为通用操作平台，规划条目也不能被误写为当前功能。

| 范围 | 当前状态 |
| --- | --- |
| 本地元数据约束、受限模型规划、查询/分析、确认状态与哈希、结果集引用、审计 | 已实现或已接入当前主链 |
| 删除确认执行 | 已实现但仅覆盖已验证删除路径 |
| `ExecutablePlan` 全字段、API Registry、通用新增/更新、流程、Action、附件补偿、跨对象写 DAG | 规划，未准入不得执行 |

## 1. 总体架构

```text
User message/attachment
  → Goal Understanding + Skill Catalog
  ├─ No skill match → LLM direct response → Audit
  ├─ Skill match but unclear → Clarification → Audit
  └─ Skill match and clear → Skill Executor → Policy/Metadata/Permission validation
  → ExecutablePlan（单步或 DAG）
  → Policy/Schema/Version Validator
  → Read Executor 或 Confirmation Preview
  → One-shot Write Executor
  → Audit Events + Result Composer + Context Snapshot
```

现有 `AgentOrchestrator`、`MetadataExecutionPlanner`、`MetadataSearchService`、`CloudPivotRuntimeService` 和 `AuditService` 作为迁移基础；不得让模型直接构造并执行 HTTP 请求。

### 1.0 对话/任务路由

`ConversationRoute` 是进入 ReAct 前的受限决策，输出仅允许 `conversation`、`task`、`clarify`，并在 `task` 下返回一个已注册的 `skillId`：

- `conversation`：由专用的通用对话模型调用生成自然语言回复；`AgentOrchestrator` 不创建元数据搜索、执行计划或云枢连接器调用，只记录脱敏的模型调用审计。
- `task`：先通过 `SkillCatalog` 验证 `skillId`，再进入该 Skill 的受限 ReAct + Reflection 流程；技能或工具只能由服务端白名单选择，模型不可直连云枢。
- `clarify`：模型未能可靠区分且又不属于高置信问候时，返回澄清，不读取云枢。

路由采用统一的对话模式协议，不为问候或闲聊词堆叠独立分支。服务端先用确定性业务信号排除明确任务，再对其余低风险输入调用一次结构化路由模型；模型返回 `conversation` 时同时提供通用回答，直接结束本轮，不再二次调用回答模型，也不进入元数据、Skill 或任务规划链。显式删除、写入、流程处理以及明显的问数/业务对象表达进入 `task`，不确定时进入 `clarify`。模型不存在、凭据不可读、超时、JSON 不合规或通用回答为空时，路由结果视为不可用并进入安全澄清或通用降级。当前 `SkillCatalog` 仅登记 `yunshu-business-system`，其执行器复用现有元数据、云枢运行态、确认与审计服务。Token 通过既有 `ModelUsageContext` 汇总，模型回复和路由结论均需脱敏后写入审计。

路由同时构造上下文连续帧：上一轮用户目标、上一轮助手回答摘要、当前反馈状态和本轮追加要求。短句修订（如“再详细设计一下”“上一版不够完整”）在存在上一轮主题时进入同一主题的改进回答；上下文帧会传给路由模型和通用回答模型。仅有“合同/客户”等业务名词不构成云枢任务证据，必须结合查询、统计、列表、详情、写入或明确业务问题信号后才绕过通用路由，避免把“设计合同管理系统”误判为云枢数据查询。

## 1.1 ReAct、Reflection 与记忆闭环

Agent 采用有界 ReAct 状态机：`Observe → Think → Act → Reflect`。需要继续执行时，Reflection 只能从白名单工具和显式计划节点中选择下一步；达到最大步数、超时、置信度不足、工具失败或风险升级时停止并澄清。Reflection 是程序化检查，不等同于暴露模型私有思维链。

### 1.2 可见过程与内部执行分层

`AgentProgressListener` 事件分为内部审计事件和对话可见事件。会话/长期记忆召回、记忆写入门禁、Prompt、模型私有推理、`schemaCode`、接口 URL、原始参数和响应只写入审计结构，不向 SSE 的业务时间线发送。前端时间线仅接收模型根据本轮目标和可用能力实际生成并真实执行的业务事件，不预设“理解目标/拆解任务”等固定标题；模型不可用时仅发送一次安全规则降级说明。

规划阶段优先调用已启用模型的 `planIntent`，输入为脱敏问题、会话上下文摘要和本地元数据候选；模型输出只能补充受约束的意图、范围、维度和任务节点，之后仍由元数据、API、权限与风险校验器裁决。模型不可用、超时或输出不合规时退回确定性元数据计划，并在可见状态中标注安全规则降级，禁止把规则路径标注成 AI 推理。

模型网关统一根据 `ModelConfig.supportsThinking` 与本轮 `thinkingEnabled` 组装请求：支持思考的配置发送 OpenAI 兼容扩展 `enable_thinking`，开启时附带 `reasoning_effort=high`；`extra_body_json` 在默认项之前合并，供不同供应商覆盖字段或扩展语义。业务服务不得自行拼装该参数。响应中的 `reasoning_content` 仅在结构化结果没有业务化 `reasoning` 时作为候选依据，并经过脱敏、长度限制和业务摘要后写入任务时间线；原始推理内容不返回前端、不进入普通消息正文。

记忆分层：

- 短期记忆：会话消息、最近结果集和记录引用，仅在会话/TTL 内有效。
- 执行记忆：Agent Run、计划、工具调用、结果和失败原因，用于审计与后续判断。
- 长期记忆：用户纠正、稳定业务别名和经人工/用户确认的对象映射；必须保存来源、置信度、版本、过期和可删除标记。

记忆写入门禁：成功执行或明确纠正且脱敏后才可写入；低置信度猜测、失败路径、凭据、Cookie、Token 和附件敏感原文禁止写入。召回结果只能作为候选证据，不能覆盖精确编码、显式应用路径、权限和最新元数据。

## 2. 统一执行计划

目标统一 `ExecutablePlan`（当前计划摘要与确认哈希已持久化；以下完整契约仍待补齐）：

```json
{
  "planId": "...", "intent": "update_data", "risk": "high",
  "metadataSnapshot": "...", "principal": "...",
  "target": {"appCode": "...", "schemaCode": "...", "recordIds": ["..."]},
  "api": {"apiCode": "update_record", "method": "PUT", "path": "..."},
  "filters": [], "relationPath": [], "fieldChanges": [],
  "contextReferences": [], "expectedImpact": {"count": 1},
  "idempotencyKey": "...", "expiresAt": "..."
}
```

计划只允许引用本地已同步元数据中的对象、字段、关系和 API。执行前必须校验：计划哈希、元数据版本、用户/组织、权限、字段 schema、记录版本、风险策略和幂等键。

## 3. 记录级上下文与多轮引用

结果集引用已通过 `query_result_references` 落地；以下是目标完整契约：

`conversationId, messageId, agentRunId, queryRunId, appCode, schemaCode, recordId, rowIndex, displaySnapshot, filterHash, permissionScopeHash, recordVersion, expiresAt`。

“第一条”只能解析为上一轮同一结果集中的 `rowIndex + recordId`；结果集不存在、排序/过滤已变化、引用过期、权限范围变化或记录版本不一致时，必须重新查询或澄清，禁止全局重新搜索后删除。

## 4. API 注册与连接器边界

目标是建立 API Registry/白名单，记录 `apiCode、method、path、inputSchema、outputSchema、operationType、riskLevel、requiresConfirmation、idempotency、compensation、verifiedAt`。当前只有已接入并通过验证的运行态路径可以执行；其他 API 即使出现在元数据中也不代表已启用。

连接器按能力拆分：

- 查询：列表、详情、分页、结构化过滤。
- 数据写入：create/update/delete，统一输入校验和幂等。
- 流程：待办/已办、节点详情、可用动作、提交/驳回/转交。
- Action：动作参数 schema、前置状态和结果校验。
- 附件：提取、字段映射、上传绑定和失败补偿。

当前 `ConfirmedOperationExecutor` 仅支持删除；新增/更新/流程/Action 必须在连接器和契约验证完成后分别接入，不得用“元数据已登记”代替“接口已可执行”。

## 5. 确认、权限与审计

当前确认单已保存计划标识、哈希、会话、风险、影响摘要、变更摘要、状态和 TTL，并阻断过期/重复确认。用户/组织主体、权限快照、元数据版本、记录版本与幂等键是完整契约的待补项；在这些字段落库并接入校验前，不得扩大高风险操作白名单。

审计事件至少覆盖：意图、候选、元数据版本、计划、确认、权限结果、API 请求摘要、响应摘要、执行结果、错误、补偿和最终消息。日志、Prompt、附件提取结果和审计均不得包含明文凭据。

## 6. 五类能力的技术路径

### 6.1 问数与关联分析

结构化 `filters/groupBy/orderBy/metrics`；跨对象只沿图谱中唯一关系路径生成 DAG。关联指标必须使用真实关联键逐条验证，记录全量/样本口径；超过预算或关系不确定时降级为并列比较并提示。

### 6.2 填单

附件或文本提取 → 字段候选与来源 → 字段置信度 → 必填/格式/枚举/权限校验 → 用户可编辑草稿 → 生成 create/update 计划 → 确认 → 幂等执行。建议补齐表单、字段映射、提取结果和执行补偿的数据表与服务。

### 6.3 流程

查询当前用户待办/已办 → 获取流程实例、节点、版本和动作 → 校验意见规则与权限 → 生成确认计划 → 执行 → 回查状态。节点已变化或动作不再可用时，计划失效并要求重新获取。

### 6.4 列表后操作

查询结果写入引用表；自然语言引用解析为唯一记录 ID；确认摘要展示对象、记录、字段差异/删除影响和 API；执行后回查并写入审计。

### 6.5 通用对话操作

未知动作进入澄清；已准入动作按固定 input schema 生成计划。浏览器自动化只能作为单独验证的适配器，不能绕过 API 白名单、权限和确认。

## 7. 失败、并发与回滚

- DAG 节点失败时停止所有依赖写节点，并保留已完成读节点与错误证据。
- 网络超时只允许对明确幂等的读请求重试；写请求必须依赖幂等键和结果查询，禁止盲目重放。
- 创建成功但附件上传失败时返回部分成功并进入补偿队列，不得伪造完整成功。
- 取消与提交竞争时以单一终态为准，提交阶段后不可标记为已取消。

## 8. 实施顺序与验证门禁

1. **P0**：统一计划/白名单/权限重验/确认快照/记录引用/审计幂等底座。
2. **P1**：问数结构化过滤与受限关联 DAG；保留现有问数回归。
3. **P2**：白名单对象的记录级删除、更新和新增。
4. **P3**：附件提取、字段映射和填单补偿。
5. **P4**：待办/已办与单节点流程动作。
6. **P5**：更多 Action 和跨对象写 DAG。

每阶段必须通过后端测试、前端构建、API 契约测试、真实测试租户联调、安全故障注入和审计追溯；未通过不得扩大白名单或进入下一阶段。代码实现必须待本技术方案与需求方案评审确认后开始。
