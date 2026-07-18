# 意图识别-动作执行-输出主链路专家评审与修订方案

评审日期：2026-07-02

## 1. 评审范围

本次评审聚焦 CPClaw 当前主链路：

```text
用户自然语言输入
  -> ConversationService / ConversationController
  -> AgentOrchestrator 意图识别、上下文继承、元数据匹配、执行计划
  -> CloudPivotRuntimeService 查询/聚合/运行态输出
  -> AuditService / Confirmation 写操作确认与审计
  -> ChatView 前端展示、确认与执行详情
```

重点判断：意图识别是否准确、动作执行是否与意图一致、输出结论是否可靠、风险操作是否被正确阻断和确认。

## 2. 总体结论

当前主链路已经形成了较完整的 Observe -> Think -> Plan -> Act -> Reflect 框架，能够覆盖查询、分析、澄清、写操作确认、运行态数据查询和前端执行详情展示。

但评审发现，主链路仍存在若干会直接影响准确性和安全性的缺陷：

1. 写/删操作的目标校验不足，部分删除请求会从确认链路前抛错，无法转入澄清。
2. 运行态查询过滤、状态识别、记录序号解析存在边界错误，可能导致查错、删错或聚合结果错误。
3. 前端确认结果展示没有区分“已确认但未执行”和“已执行成功”。
4. 元数据关系识别和运行态意图分发仍依赖多处硬编码关键词，后续扩展容易出现“识别、执行、输出”不一致。
5. 审计确认缺少状态和过期校验，存在重复确认或过期确认继续执行的风险。

## 3. 专家评审意见汇总

### 3.1 Agent 主链路与安全控制视角

#### 问题 A1：删除意图缺少目标记录时直接抛错

位置：`server/src/main/java/com/cpclaw/agent/AgentOrchestrator.java`

现象：

- `missingSlots` 只校验云枢对象是否可查询。
- 对 `delete_data` 没有强制要求记录 ID、序号或明确上下文记录。
- `writeOperationPlan` 在创建确认单前调用 `cloudPivotRuntimeService.resolveRecordTarget`。
- 如果用户只说“删除商机”“删除客户”，后端会抛出“删除操作缺少明确记录”，而不是生成澄清回复。
- 即便用户说“删除第一条客户”，如果当前运行态查询无法稳定解析到可删除记录或记录 ID，也会在确认单生成前中断。

影响：

- 用户无法获得安全澄清引导。
- 流式对话会进入 error 事件，破坏主链路体验。
- 删除意图的安全控制在“生成确认前”就中断。

修订方案：

1. 在 `missingSlots` 中对 `delete_data` 增加删除目标槽位校验：记录 ID、明确序号、或上一轮结果引用。
2. 如果缺少删除目标，直接转入 `clarify_intent`，复用删除专用澄清文案。
3. `writeOperationPlan` 不应承担用户可见澄清职责；它只处理已经通过槽位校验的可执行计划。
4. 删除确认文案应展示 appCode、schemaCode、bizObjectId、对象名称和风险级别。

---

#### 问题 A2：状态词“取消”被优先识别为删除意图

位置：`AgentOrchestrator.detectIntent`

现象：

- `detectIntent` 先判断 `删除/删掉/移除/作废/取消`。
- “取消的订单有多少金额”“已取消项目数量”等状态统计问题会被识别为 `delete_data`。
- 后续状态聚合逻辑没有机会执行。

影响：

- 读请求误判成写/删请求。
- 可能触发无意义确认或删除目标缺失异常。

修订方案：

1. 在写/删关键词判断前，优先判断“状态 + 统计/金额/数量”组合。
2. 将 `取消` 从无条件删除词中拆出：
   - “取消订单/取消这条/取消第 N 条”才是操作。
   - “取消的订单/已取消订单/状态为取消”应识别为查询或分析。
3. 增加测试用例：
   - `取消的订单有多少金额` -> `analyze_data` 或 `query_data`
   - `删除第1条订单` -> `delete_data`
   - `取消这个订单` -> 写/风险操作，需要确认。

---

#### 问题 A3：运行态筛选只在 Orchestrator 中硬编码 owner 过滤

位置：`AgentOrchestrator.buildRuntimeFilters`

现象：

- 当前只有负责人/销售字段会被转换为 `RuntimeQueryFilter`。
- 状态、日期、省份、客户类型、金额区间等 Planner 识别到的线索没有统一转成运行态过滤。
- RuntimeService 又通过关键词重新判断一次问题类型。

影响：

- 意图识别层和执行层可能不一致。
- 新增业务意图时需要同时修改多个类。
- Trace 可能显示识别到了某个筛选，但运行态没有实际使用。

修订方案：

1. 将过滤条件抽象为 `MetadataExecutionPlan` 的结构化输出，例如：
   - fieldCode
   - fieldName
   - operator
   - value
   - source
   - confidence
2. `AgentOrchestrator` 不再只识别 owner，而是直接传递 planner 产出的 filters。
3. `CloudPivotRuntimeService` 负责执行 filters 和聚合，不再重新猜测用户筛选。
4. 逐步把状态、省份、客户类型、日期范围、金额区间纳入统一 filter 结构。

### 3.2 CloudPivot 运行态执行与输出准确性视角

#### 问题 R1：第 4 条及以上记录序号默认解析成第 1 条

位置：`CloudPivotRuntimeService.requestedRecordOrdinal`

现象：

- `hasRecordOrdinalReference` 接受任意“第...条/个/笔/单”。
- `requestedRecordOrdinal` 只识别第二和第三。
- 其他序号默认返回 1。

影响：

- “查看第4条详情”会返回第1条。
- “删除第4条商机”可能确认并删除第1条。

修订方案：

1. 使用正则解析阿拉伯数字：`第(\d+)条/个/笔/单`。
2. 支持常见中文数字：一、二、三、四、五、六、七、八、九、十。
3. 无法解析时不要默认 1，而是返回空值并要求澄清。
4. 对删除操作限制最大序号和上下文来源，避免跨页误删。
5. 增加测试：第1、2、3、4、10条详情和删除目标解析。

---

#### 问题 R2：服务端过滤后又本地二次过滤，导致 total 被当前页匹配数覆盖

位置：`CloudPivotRuntimeService.query` 与 `filterResultByRuntimeFilters`

现象：

- `runtimeFilters` 已传给 `CloudPivotConnector.queryRecords`。
- 查询返回后又调用 `filterResultByRuntimeFilters`。
- 本地过滤会创建新的 `CloudPivotRuntimeQueryResult`，total 设置为 `matchedRecords.size()`。
- 如果云枢已按条件返回 total=300，但当前页只有 50 条，本地二次过滤后最多显示 50。
- 如果轻量列表没有 owner 字段，本地过滤可能将结果过滤成 0。

影响：

- “张三名下有多少商机”这类问题会输出错误数量。
- 筛选后统计、分析和排行都可能基于错误集合。
- 当远端过滤接口失败后 fallback 到未过滤查询时，如果本地过滤只按字段编码匹配而没有使用 `recordOwner()` 兜底，可能在返回记录中实际存在负责人信息时仍误报 0。

修订方案：

1. 区分“远端过滤已生效”和“需要本地兜底过滤”。
2. 如果 filters 已传给云枢并请求成功，不应无条件本地二次过滤。
3. 只有当远端不支持过滤、接口失败后走兜底查询，才启用本地过滤。
4. 本地过滤时必须明确输出“基于本次返回样本过滤”，不能冒充真实总数。
5. `CloudPivotRuntimeQueryResult` 可增加字段：
   - `filterAppliedByRemote`
   - `filterAppliedLocally`
   - `totalScope`
6. 对 owner count、owner + amount、owner + status 场景补充测试。

---

#### 问题 R3：负责人筛选优先把查询上限限制为 50，截断聚合结果

位置：`CloudPivotRuntimeService.queryRecordLimit`

现象：

- `queryRecordLimit` 首先判断 `requestedOwnerFilter(content).present()`，返回 50。
- 对“张三名下进行中的项目金额多少”这类 owner + status + amount 聚合问题，无法使用后续 20,000 或 500 的聚合上限。

影响：

- 聚合只基于前 50 条 owner 记录。
- 输出看起来是业务总量，实际是截断样本。

修订方案：

1. 查询上限决策先判断聚合/分析复杂度，再叠加 owner filter。
2. owner filter 不应单独降低聚合问题上限。
3. 对 count-only 问题可以使用低 pageSize，但如果需要本地聚合，必须拉取足够记录。
4. 输出必须标注是“全量口径”还是“样本口径”。

---

#### 问题 R4：“未完成”同时命中“进行中”和“已完成”

位置：`CloudPivotRuntimeService.requestedStatusFilters`

现象：

- `未完成` 命中第一段，加入“进行中”。
- 由于字符串中包含“完成”，又命中第二段，加入“已完成”。

影响：

- “未完成项目金额多少”会把已完成项目也纳入统计。
- 状态聚合结果被放大。

修订方案：

1. 状态词匹配按长词和否定词优先：`未完成` 必须先于 `完成`。
2. 将状态识别改为明确 token 分类，避免简单 contains。
3. `已完成` 只匹配 `已完成/完成了/已结项/结项`，不能匹配 `未完成`。
4. 增加测试：未完成、已完成、进行中、已结项、取消/终止。

---

#### 问题 R5：金额单位“万/万元”被删除但没有换算

位置：`CloudPivotRuntimeService.numericValue`

现象：

- `String.valueOf(value).replaceAll("[,，￥¥元万元万]", "")` 会删除“万/万元”。
- `10万元` 被解析为 10，而不是 100000。
- `3.5万` 被解析为 3.5，而不是 35000。

影响：

- 金额合计、平均值、排行严重低估。
- 输出结论不可信。

修订方案：

1. 解析前判断原始文本是否包含 `万` 或 `万元`。
2. 数值解析后按单位换算：
   - 万/万元：乘以 10,000。
   - 亿/亿元：可预留乘以 100,000,000。
3. 保留显示格式时注明单位。
4. 增加测试：`10万元`、`3.5万`、`10000元`、`￥12,345.67`。

### 3.3 元数据关系与执行计划视角

#### 问题 M1：关联字段识别退化为重复中文 literal

位置：`MvpCloudPivotConnector.isReferenceDataItem`

现象：

- 当前逻辑包含多段重复的 `value.contains("关联表单")`。
- 没有覆盖 CloudPivot 常见英文/编码型关系标记，例如：
  - `relevance_form`
  - `reference`
  - `association`
  - `bizObject`
  - `object`

影响：

- 关联字段无法被标记为 reference。
- `CloudPivotEntityRelation` 不完整。
- Agent 无法基于真实关系进行对象定位、关联查询和字段筛选。

修订方案：

1. 建立统一 reference type vocabulary。
2. 覆盖中文、英文、编码型 controlType/dataType/rawJson。
3. 为真实云枢元数据样例补充测试。
4. 在同步结果中输出 relationCount 和被跳过 relation 的原因，方便排查。

### 3.4 审计确认与前端输出视角

#### 问题 C1：确认操作不检查状态和过期时间

位置：`AuditService.confirm`

现象：

- 不检查 confirmation 当前是否 `pending`。
- 不检查 `expiresAt`。
- 不防止重复确认。

影响：

- 双击确认可能重复执行。
- 旧确认单可能被重放。
- 删除类操作存在安全风险。

修订方案：

1. `confirm` 前检查：
   - status 必须为 `pending`。
   - `expiresAt` 必须晚于当前时间。
2. 确认和执行应放在事务中，必要时使用乐观锁或状态条件更新。
3. 执行成功后设置 `executed`；执行失败保留 `confirmed` 或 `failed` 并记录错误。
4. 重复确认返回幂等结果，不再次执行外部 CloudPivot 操作。

---

#### 问题 C2：非删除写操作确认后前端误报成功

位置：`ChatView.confirmLastOperation`

现象：

- `ConfirmedOperationExecutor` 只自动执行 delete。
- create/update 等确认会返回 `executed=false` 和 unsupported message。
- 前端忽略返回值，直接提示“已确认，系统已记录本次操作”。

影响：

- 用户误以为新增/修改已经执行。
- 输出链路把“确认记录”误表达为“操作成功”。

修订方案：

1. `confirmOperation` 的前端类型应包含：
   - executed
   - status
   - message
2. 前端根据 executed/status 展示不同文案：
   - executed=true：操作已执行。
   - executed=false 且 unsupported：当前仅完成确认，尚未执行自动写入。
   - failed：执行失败，展示原因。
3. Agent 对 create/update 在后端未实现前，不应生成“确认后继续执行”的暗示文案；应明确“当前只生成待确认计划”。
4. 若短期不支持新增/修改自动执行，可将此类意图统一转入澄清/能力说明，不创建可确认但不可执行的确认单。

### 3.5 前端执行详情与体验一致性视角

#### 问题 F1：前端 pendingQuestionProfile 复制后端意图启发式

位置：`ChatView.pendingQuestionProfile`

现象：

- 前端根据金额、阶段等关键词提前生成 pending plan。
- 后端真实意图可能是澄清、删除、负责人聚合、省份分析、新老客户分析等。
- 最终 trace 回来前，用户看到的执行计划可能与真实路径不一致。

影响：

- 用户对系统行为产生误解。
- 每次后端意图扩展都需要同步修改前端启发式。

修订方案：

1. 前端 pending 阶段只展示通用状态，例如“正在理解问题”。
2. 具体意图、对象、风险、计划由后端 progress event 推送。
3. 后端在 `AgentOrchestrator` 早期发出结构化 progress：intent/candidate/risk/planStage。
4. 前端不再复制业务关键词判断。

---

#### 问题 F2：SSE 当前是“后端完成后模拟流式输出”，且前端未校验 final 事件

位置：`ConversationController.streamMessage`、`web/src/services/conversationApi.ts`

现象：

- `conversationService.sendMessage` 完整执行完后，才调用 `streamAnswerChunks`。
- answer chunk 是对最终内容进行人为分块和 sleep。
- final event 被延后发送。
- `sendMessageStream` 在读取流 EOF 后没有校验是否收到 `event: final`。

影响：

- 对长答案人为增加延迟。
- 用户以为模型/工具正在流式输出，实际上后端已完成。
- 维护上同时保留 chunk 和 final replacement 两套路径。
- 如果代理、网络或服务端在 progress/chunk 后、final 前关闭连接，前端会把流结束当作成功返回；`ChatView.submit` 随后清空 pending 状态和附件，可能留下本地局部 assistant 消息，但没有持久化的 assistantMessage、agentRunId 或审计元数据。

修订方案：

1. 短期：移除人工 sleep，或只发送 progress + final，不模拟 token 流。
2. 中期：如果需要真实流式，Agent/模型/工具链路应真正边执行边输出。
3. final event 应尽快到达，以便前端拿到审计和确认信息。
4. `sendMessageStream` 应维护 `finalReceived` 标记；EOF 时若未收到 final，应抛出“流式响应未完整结束”，由 `ChatView` 回滚 pending 消息并提示用户重试。
5. 前端只有在收到 final 且完成 pending 替换后，才清空附件和提交状态。

## 4. 优先级修订路线

### P0：必须先修的准确性/安全问题

1. 删除目标缺失进入澄清，不允许直接抛错。
2. 删除/详情序号解析修复，禁止第4条默认第1条。
3. `AuditService.confirm` 增加 pending/expiry/idempotency 校验。
4. 取消/未完成状态识别修复，避免读请求误入删除和错误聚合。
5. 前端确认结果根据 executed/status 展示，不再无条件成功。

### P1：影响业务结论准确性的运行态问题

1. 远端过滤和本地过滤解耦，避免 total 被当前页覆盖。
2. owner + aggregation 查询上限修复。
3. 金额单位换算。
4. reference/relation 元数据识别增强。

### P2：架构收敛与长期可维护性

1. RuntimeFilter 由 MetadataExecutionPlan 统一产出。
2. Owner/status/amount/date/province/customerType 等解析集中到共享 planner/filter 模块。
3. RuntimeService 执行结构化计划，不再重建一套 keyword intent taxonomy。
4. 前端 pending trace 去业务启发式，完全依赖后端 progress。
5. SSE 明确为 progress+final 或升级为真实流式。

## 5. 建议补充测试用例

### 5.1 删除与确认

- `删除商机`：返回澄清，要求记录 ID 或第几条。
- `删除第4条商机`：解析 ordinal=4，不得回落第1条。
- 重复确认同一 confirmationId：不得重复执行 CloudPivot 删除。
- 过期 confirmationId：返回过期错误，不执行。

### 5.2 状态与读写意图区分

- `取消的订单有多少金额`：识别为查询/分析，不是 delete。
- `取消这个订单`：识别为高风险写操作。
- `未完成项目金额多少`：只统计未完成/进行中，不包含已完成。
- `已完成项目金额多少`：只统计已完成。

### 5.3 运行态过滤和聚合

- `张三名下有多少商机`：使用远端过滤 total，不被本地当前页覆盖。
- `张三名下进行中的项目金额多少`：不被 owner filter 限制为 50 条样本。
- 远端过滤失败时：本地兜底过滤输出必须标注样本口径。

### 5.4 金额解析

- `10万元` -> 100000。
- `3.5万` -> 35000。
- `12,345.67元` -> 12345.67。
- `1亿元` 后续可扩展为 100000000。

### 5.5 元数据关系

- dataType/controlType/rawJson 包含 `relevance_form` 时识别为 reference。
- 包含 `reference`、`association`、`bizObject` 时识别为 reference。
- 同步后 relationCount 和 dataItem.reference 正确。

## 6. 建议目标架构

当前链路应从“多处关键词判断”演进为“结构化计划驱动”：

```text
用户输入
  -> AgentObservation：上下文、上一轮 runtime 对象
  -> IntentPlanner：意图、风险、槽位、筛选条件、指标、目标对象
  -> MetadataExecutionPlan：schemaCode、field hints、relation hints、runtime filters、aggregation spec
  -> RuntimeExecutor：只执行计划，不重新猜意图
  -> OutputComposer：根据执行结果和 plan 生成可解释回答
  -> Audit/Confirmation：记录 plan、tool call、reflection、确认状态
  -> Frontend：展示后端真实 progress 和 final trace
```

关键原则：

1. 意图识别结果必须结构化，不依赖输出文案再解析。
2. 执行层只执行计划，不重复猜用户意图。
3. 所有写操作必须先通过槽位完整性和确认状态校验。
4. 所有聚合输出必须说明全量口径或样本口径。
5. 前端不得复制后端业务意图判断，只展示后端事实。

## 7. 验收标准

修订完成后，至少满足：

1. 查询、分析、删除、写操作确认在异常输入下都返回可解释消息，不出现未处理异常。
2. 删除不会在记录目标不明确时生成可执行确认单。
3. `取消的订单有多少金额`、`未完成项目金额多少` 等状态读请求不误入删除链路。
4. owner filter 和状态/金额聚合不会因分页或二次过滤输出错误总数。
5. 前端确认提示与后端执行结果一致。
6. 审计记录能复原真实 Observe/Think/Plan/Act/Reflect 链路。

## 8. 2026-07-15 执行活动流修订

本轮将原有“实时四步模板 + 完成后简略摘要 + 右侧独立审计卡片”统一为一条 Agent 活动时间线。

### 8.1 事件记录

- `AgentProgressListener.recording` 代理所有 `progress`、`thought`、`execution`、`answer_start`、`answer_reset`、`answer_end` 事件。
- 每条事件保留 `phase`、`state`、`kind`、`data` 和相对耗时 `elapsedMs`，重复调用按原始顺序保存。
- 时间线随 `AgentResponse.steps` 返回，并写入助手消息 `metadataJson.executionTimeline`。
- 时间线进入响应和数据库前统一经过 `SensitiveDataMasker`，避免用户输入中的密码、Token、API Key 等敏感值进入执行详情。

### 8.2 前端展示

- `AgentRunTimeline.vue` 同时用于实时执行区、历史消息折叠区和右侧详情栏。
- 同一次 `running -> completed` 调用在界面上合并；不同调用即使标题相同也继续追加。
- 思考节点、工具/API 节点、失败/回退节点使用不同图标和弱边界样式。
- 常用数据以紧凑事实行展示，复杂对象进入“查看请求与返回数据”，保留完整值。
- 右侧栏在桌面端固定为 320-360px、视口内滚动，不再撑高中央对话区；1180px 以下才切换为下方区域。

### 8.3 已知架构边界

当前后端事件尚未形成生产级不可变事件协议。并行工具调用或复杂重试场景仍应补充 `eventId`、`sequence`、`callId`、`parentId`、`startedAt` 和 `completedAt`，前端只允许相同 `eventId` 原位更新，并通过 `callId` 绑定工具请求与结果。
