# Yunshu Skill 可执行架构基线与开发准入

> 版本：v1.2｜状态：implementation-ready（设计层）｜实施契约见 `24-yunshu-skill-implementation-contract.md`，协议验收见 `docs/test-cases/25-yunshu-skill-contract-acceptance.md`，安全、Provider 与发布要求由 `23-security-provider-release-gates.md` 强制补充。与旧文档存在冲突时，以本文件为准；旧文档只能用于历史追溯。

## 1. 结论与准入

三位独立评审（产品体验、平台架构、安全与云枢集成）确认总体架构方向可行。设计契约已闭合，可进入受控的分阶段开发；真实写入和生产交付仍必须通过下列 G0-G6：

1. G0：独立 Yunshu Skill Gateway 可脱离 CPClaw DB/UI 单独启动、认证、发布和审计；
2. G1：宿主无关的任务、确认、续接、错误码协议完成契约测试；
3. G2：可信主体、租户、权限映射与四点权限重验完成验证；
4. G3：真实云枢表单 Schema、校验、创建/更新/提交/回查契约完成 E2E 验证；
5. G4：双实例、重启、断线、并发重放、远端超时 `UNKNOWN` 与对账恢复演练通过；
6. G5：Markdown Skill 签名、审核、撤回、资源限制和执行器治理验证；
7. G6：模拟 WorkBuddy Host 完成追问、草稿、校验、确认、提交、断线恢复和幂等测试。

任一 Gate 失败，不能进入生产交付，相关能力必须限制为只读、草稿或预览；不得宣称“智能填单已完成”。进入开发的条件以第 24 号 Definition of Ready 为准。

## 2. 不可违反的边界

```text
CPClaw Web Host ─┐
WorkBuddy Host ──┼──> Yunshu Skill Gateway ──> Yunshu Task Runtime ──> CloudPivot
OpenClaw Host ───┘
```

- Yunshu Skill Gateway 是独立产品能力，可与 CPClaw 同仓，但必须支持独立进程、配置、认证、版本和发布。
- CPClaw 只是宿主，不是外部 AI 的对话中转层；外部宿主不得调用 CPClaw 对话 API、UI 或确认页。
- 协议中禁止出现 `open_cpclaw_confirmation`、`cpclaw_authenticated_ui` 等宿主专属动作。
- 宿主只负责渲染、收集用户输入和转发动作；所有对象匹配、权限校验、分页、关联、校验和写入由 Skill Runtime 完成。

## 3. 统一协议

唯一 Canonical Protocol 为 `yunshu-skill/1.0`，对外首选 MCP 高阶工具 `yunshu_task`；HTTP/CLI 只是同一协议的适配器，必须通过同一 JSON Schema 与一致性测试。`cpclaw_cloudpivot_agent`、`yunshu_handle_request` 仅可在迁移期作为无语义差异的兼容别名，不能增加第二条执行链、不同状态或不同确认模型。

工具参数固定为：

```json
{"operation":"start|resume|input|action|get|events","taskId":"...","goal":"...","input":{},"action":{},"clientRequestId":"..."}
```

`TaskExperienceEnvelope` 必须包含：

```json
{
  "protocolVersion":"yunshu-skill/1.0",
  "task":{"id":"...","kind":"form|query|analysis|workflow|import","status":"needs_input","stateVersion":3,"retryable":false},
  "interaction":{"type":"ask_user|edit_draft|request_confirmation|none","questions":[]},
  "draft":{"formRef":{},"metadataVersion":"...","draftVersion":2,"fields":[]},
  "validation":{"issues":[]},
  "next":{"requiredInputSchema":{},"continuationToken":"...","expiresAt":"..."},
  "completion":{"state":"incomplete|ready|submitted|failed","missing":[]},
  "output":{},"evidence":{},"hostAction":{"type":"ask_user|edit_draft|request_confirmation|respond|report_failure"},"auditRef":{}
}
```

内部阶段与对外状态必须分离。内部阶段仅用于编排：

```text
UNDERSTAND → DISCOVER → COLLECTING → VALIDATING → SUBMITTING → RECONCILING
```

对外状态与合法转移固定为：

```text
needs_input → draft_ready → validation_error | ready_to_submit
ready_to_submit → confirmation_required → submitting
submitting → submitted | failed | unknown
unknown → submitted | failed | manual_review
任意未提交状态 → cancelled
```

`unknown` 不是失败或可直接重试；只有 Provider 回查证实未写入后才能生成新的执行尝试。`manual_review` 是需要人工处置的终态。

### 3.1 宿主能力协商与体验等价

初始化阶段宿主必须声明 `HostCapabilityProfile`，至少包括：

```json
{
  "structuredForm": true,
  "fieldPatch": true,
  "actionButton": true,
  "confirmationCallback": true,
  "streamingEvents": true,
  "taskResume": true,
  "maxQuestionOptions": 5
}
```

Skill Runtime 根据能力声明选择交互表达，但不改变任务语义、状态机、校验和安全门禁。CPClaw Web、WorkBuddy、OpenClaw 的验收标准是**能力和事实等价**（同一任务状态、字段值、缺口、风险、确认票和回执），不是要求各宿主具有相同的视觉组件。宿主能力不足时必须按以下顺序降级：结构化卡片 → 受约束 JSON → 可解析 Markdown 文本；降级不得通过跳转 CPClaw 页面补足能力，也不得让宿主自行重建业务判断。

### 3.2 交互对象的最小契约

`interaction.questions[]` 中每个问题必须包含稳定的 `id`、`slotRefs`、`type`、`prompt`、`required` 和可选 `options`、`validation`。宿主提交补充信息时只能引用 `question.id` 或 `fieldCode`，并携带原 `taskId`、`stateVersion` 和 continuation token。草稿修改必须是服务端可校验的字段补丁；服务端以 `draftVersion` 做乐观并发控制，版本不匹配时返回新的草稿和冲突信息。

`request_confirmation` 必须返回人类可读的对象、字段差异、影响范围、风险、数据来源、`planHash`、过期时间和一次性确认票。确认回调只能在当前宿主完成，服务端重新校验主体、权限、元数据版本和草稿版本后才允许提交。

### 3.3 稳定任务标识、并发与事件

对外 `taskId` 在澄清、草稿编辑、确认、断线恢复和对账期间必须稳定；内部执行使用独立的 `attemptId/runId`，不能以创建子任务替代续接。`FormTask` 在同一任务聚合中以 `draftVersion + stateVersion` 的 CAS 更新，旧版本输入返回冲突及最新可见草稿。

任务幂等键至少包含 `tenantId + subjectId + hostId + conversationId + turnId/clientRequestId`；远端写入幂等键另包含 `taskId + operation + draftVersion`。任务事件使用单调 `eventSequence`，并在持久化层强制 `(task_id, event_sequence)` 唯一约束。所有读取、续接、确认和取消均按 `tenantId + subjectId + hostId` 进行不可越权过滤。

## 4. 表单任务一等建模

新增 `FormTask` 聚合，至少持久化：`taskId`、`attemptId`、`tenantId`、`subjectId`、`hostId`、`skillVersion`、`formRef`、`metadataVersion`、`stateVersion`、`draftVersion`、`slots`、`missingSlots`、`validationIssues`、`planHash`、`confirmationId`、`idempotencyKey`、`auditRef`。草稿修订必须可追溯，建议采用 append-only revision 加当前版本索引。

每个字段槽位至少包含：`fieldCode`、显示名、类型、值、来源（用户/附件/上下文/默认/AI 建议）、置信度、必填标记、是否已确认、校验状态和错误信息。模型只能提出候选值，服务端依据 Schema 接受或拒绝。

## 5. 运行时职责

1. `DISCOVER`：基于已同步元数据定位应用、对象、表单、字段、枚举和关系；候选不唯一必须追问。
2. `COLLECTING`：只追问阻塞提交的最小字段集合，保留已填值。
3. `VALIDATING`：执行类型、枚举、关联、权限、联动和业务规则校验。
4. `READY_TO_SUBMIT`：生成字段差异、影响范围、风险、来源和口径预览。
5. `SUBMITTING`：重验权限和元数据版本，使用远端幂等键写入并回查真实回执；超时或无法判断结果时转入 `unknown`，而非直接重试。
6. `ANALYSIS`：根据关系元数据自动获取主对象与关联对象，确定性计算指标，模型只解释结果。

## 6. Provider 真实契约

在真实云枢环境完成并登记以下契约后，才允许开放写入：

```text
loadFormSchema(formRef)
validateDraft(formRef, draft, metadataVersion)
createRecord(formRef, draft, idempotencyKey)
updateRecord(recordRef, patch, idempotencyKey)
submitAction(actionRef, payload, idempotencyKey)
getOperationResult(idempotencyKey)
```

契约登记必须包含请求结构、响应结构、错误码、权限要求、超时语义、幂等行为、回查方式和回滚/补偿方式。未验证契约只能返回草稿或确认计划。

## 7. 身份、确认与可靠性

- 生产请求必须携带可信 `tenantId + subjectId + scopes`，由 OIDC/JWT 或等价签名断言提供；用户可控参数不得决定主体。
- 确认票绑定主体、租户、宿主、Skill/表单/元数据/草稿版本、计划哈希、有效期和一次性消费标记。
- 远端成功而本地超时必须进入 `unknown`，通过回查、对账和补偿收敛，禁止直接重试造成重复写入。
- 任务、草稿和事件必须存储在可跨实例、重启和断线恢复的事实源；SSE 只做进度传输。异步执行须采用持久化 outbox/队列、worker lease、心跳和 watchdog；重启后由 worker 恢复或进入回查流程，不能依赖单机 SSE 会话。

## 8. Markdown Skill 与通用对话

- 通用对话不访问云枢；Skill 选择由 Manifest 能力声明和上下文决定。路由顺序必须固定为：显式 Skill/宿主工具约束 → 已安装且已发布的 Markdown Skill → Yunshu Skill 能力匹配 → 通用对话兜底。没有明确能力命中时不得尝试云枢探查；普通闲聊、无法识别的目标和被停用 Skill 必须产生可审计的 `general_conversation` 或 `blocked` 结果。
- Markdown Skill 只能声明输入、输出、风险和已审核 Executor；不得携带任意脚本、URL、凭据或代码。
- 草稿、审核、发布、停用、版本回滚均属于控制面，未发布版本不可执行。

## 9. 开发前可行性 Gate 用例

模拟 CPClaw Web Host 和模拟 WorkBuddy Host 使用同一协议完成：

```text
“新增线索”
→ needs_input（表单/必填字段）
→ input（补充字段）
→ draft_ready
→ validation_error（逐字段）
→ input/update_draft
→ ready_to_submit
→ request_confirmation（宿主内）
→ action(confirm)
→ submitted（真实/模拟 recordRef）或 unknown → 对账收敛
→ 重复请求不重复写入
→ 断线后按 taskId 恢复
```

验收必须证明：响应无 CPClaw URL/UI 动作；外部宿主不调用 CPClaw 对话 API；普通闲聊不访问云枢；未审核 Markdown Skill 不执行；分析任务能按关系元数据取齐关联数据。

跨宿主体验验收还必须使用同一组目标和用户输入，对 CPClaw Web 与模拟 WorkBuddy 逐状态比对：`needs_input` 的问题和字段引用、草稿字段及版本、逐字段校验错误、确认计划哈希、提交回执和断线恢复结果必须一致；允许按钮、卡片和文本的呈现不同。宿主能力不足的降级响应必须仍可被机器解析并继续同一 `taskId`，不得要求用户重新描述目标。

Gate 的威胁模型、OIDC/JWT 参数、确认票字段、Provider 登记项、Markdown 治理和 No-Go 条件以 `23-security-provider-release-gates.md` 为强制细则。每一 Gate 必须输出环境、用例、日志或回执、缺陷清单和明确结论；任何 P0/P1 缺陷或证据缺失均回到方案阶段。

## 10. 专家复核结论

| 视角 | 判定 | 开发前硬条件 |
| --- | --- | --- |
| 产品体验 | 有条件可行 | 以能力与事实等价替代跨宿主 UI 完全一致；验证 HostCapabilityProfile 与降级链路 |
| 平台架构 | 有条件可行 | 独立 Gateway、唯一协议、稳定 taskId、异步恢复和多实例可靠性 |
| 安全与云枢集成 | 有条件可行 | 可信身份、签名确认、真实 Provider、UNKNOWN 对账和 Skill 治理 |

综合判定：**Architecture Feasible / Implementation-Ready by Design / Release No-Go Pending G0-G6**。是否进入开发按第 24 号 Definition of Ready；是否进入生产交付按第 24 号 Definition of Done。

## 11. 开发顺序

先完成协议与模拟 Gate，再按“控制面与身份 → Task/FormTask 持久化 → Runtime 状态机 → Provider 只读 → Provider 写入 → 宿主适配器 → 观测与回归”分阶段开发。每阶段有独立验收证据，未通过不得进入下一阶段。
