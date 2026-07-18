# CPClaw Agent 详细设计

> 本文是 Agent 专项详细设计。整体技术路径见 `../00-technical-blueprint.md`。

## 1. 设计目标

CPClaw Agent 负责把用户自然语言转换为可审计、可确认、可执行的云枢应用操作。Agent 的匹配和规划只基于本地已同步的 Metadata Index、应用级知识图谱、记忆和会话上下文，不实时连接云枢做应用能力检索。

Agent 的目标不是被动关键词匹配，而是尽可能理解用户想完成的业务任务：能识别查询、分析、统计、写入、流程、附件填单等意图；能根据本地元数据定位任务对象；能在信息不足时发起澄清；能在意图明确后执行查询、分析或经确认的业务操作；能在执行后解释结果、依据、风险和下一步建议。

## 2. 执行模式

CPClaw 采用 ReAct + Reflection，并将 Agent 拆成多阶段流水线，而不是单个大 Prompt。

当前后端已落地 ReAct + Reflection MVP：每次对话会显式经过 `Observe -> Think -> Act -> Reflect` 四段，并把阶段化计划写入 `agent_runs.plan_json`，把反思检查写入 `agent_runs.reflection_json`。响应步骤也会返回 Observe、Think、Act、Reflect 四个阶段。当前 MVP 仍以规则和本地元数据检索为主，尚未完成模型驱动结构化意图、DAG 多节点计划、多工具循环和完整上下文引用对象表。

```text
User Message + Attachments + Conversation Context
  -> Input Normalizer
  -> Memory Retriever
  -> Intent Classifier
  -> Slot Extractor
  -> Metadata Graph Retriever
  -> Candidate Ranker
  -> Clarification Decider
  -> Plan Builder
  -> Risk Checker
  -> Confirmation Manager
  -> Tool Executor
  -> Reflection Checker
  -> Response Composer
  -> Memory Writer
```

## 3. 意图类型

- `query_data`：查询数据。
- `analyze_data`：查询运行态数据后进行业务分析，输出结论、关键发现、风险信号和下一步建议。
- `summarize_data`：汇总数据。
- `create_data`：新增数据。
- `update_data`：修改数据。
- `delete_data`：删除数据。
- `run_action`：执行业务 Action。
- `submit_workflow`：提交流程或审批动作。
- `open_report`：查看报表。
- `sync_metadata`：同步云枢架构。
- `fill_form_from_attachment`：基于附件和描述填单。
- `clarify_intent`：信息不足或低置信度时的澄清意图，不执行工具、不触发高风险确认。
- `unknown`：无法确认，需要追问。

## 4. 结构化意图理解

模型调用采用 OpenAI 兼容格式，并要求返回结构化结果。

示例输出：

```json
{
  "normalizedUserGoal": "给上一轮第一条商机新增跟进记录",
  "intents": [
    {
      "intentType": "create_data",
      "confidence": 0.91,
      "riskLevel": "medium",
      "requiresConfirmation": true,
      "targetObjects": ["商机", "跟进记录"],
      "dependsOnContext": true,
      "attachmentRequired": false
    }
  ],
  "slots": {
    "applicationHints": ["CRM"],
    "entityHints": ["商机", "跟进记录"],
    "recordReferences": ["上一轮第一条"],
    "fieldValues": {
      "跟进内容": "客户下周再沟通"
    },
    "timeRange": null,
    "attachments": []
  },
  "missingSlots": [],
  "clarificationNeeded": false,
  "reasoningSummary": "用户引用上一轮商机列表中的第一条记录，并要求新增跟进信息"
}
```

## 5. 置信度策略

- `>= 0.85`：可进入计划生成，写操作仍需确认。
- `0.60 ~ 0.85`：进入候选推荐，让用户选择应用、实体或功能。
- `< 0.60`：不执行，提示用户补充信息。

ReAct + Reflection MVP 中，`Think` 阶段会识别原始意图、匹配元数据对象、抽取缺失槽位并计算置信度；低置信度、缺少可查询对象、缺少上下文记录或缺少写入必要字段时统一进入 `clarify_intent`。澄清回复必须包含：系统已理解到的内容、当前缺失的信息、可补充的方向，以及本地可见的部分候选对象。

置信度需要综合：模型输出、Top1/Top2 分差、候选数量、字段归属、Action 归属、关系路径唯一性、上下文引用是否明确、记忆是否与当前图谱一致。

## 6. 本地能力检索

### 6.1 检索原则

- 只查询本地 Metadata Index 和检索中间件。
- 不实时连接云枢进行能力匹配。
- 先定位应用，再在应用图谱内定位实体、字段、关系路径、视图操作和表单操作。

### 6.2 当前混合元数据检索

检索链路：

1. Query rewrite：结合会话上下文和记忆改写用户目标。
   - 对“系统有多少商机？”、“商机一共有几条？”这类计数问法，需要先剥离“有多少、多少条、几条、一共、总共、共有、吗、？”等疑问/统计词，保留“商机”等业务对象词参与匹配。
   - 对“系统有多少个客户？”、“每年的客户量情况怎么样？”这类表达，需要识别出动作、业务对象和分析维度：动作分别是计数和按年分析，对象是客户，维度是年份；“情况怎么样、每年、量”等词不应干扰对象匹配。
2. 业务词全量候选召回：当用户表达中能抽取出“商机、客户、合同”等业务对象词时，必须用这些业务词从本地 Metadata Index 全量召回候选，再统一排序；不能只依赖数据库前 10 条直接搜索结果，否则真实 CRM 核心模型可能被同名管理表、分配表、统计表或测试表截断。
3. 精确名称召回：实体名、应用名、表单名或字段名与业务对象词一致时优先召回。
4. 别名召回：使用业务别名表把“销售机会/机会/opportunity/oppor”归一到“商机”，把“潜客/lead”归一到“线索”。
5. 编码召回：用户直接输入 `schemaCode`、应用编码或字段编码时优先命中真实编码。
6. 应用路径召回：通过 `graphPath` 识别用户显式提到的应用或路径，例如“CRM 客户”“项目基础数据里的商机”。显式路径提示应优先于通用业务对象偏置。
7. 全文关键词召回：从 Metadata Index 的 `searchText` 中召回名称、编码、应用描述、模型描述和操作词。
8. 复合业务词召回：对“客户下面的商机”“商机跟进记录”等表达识别最终可执行目标对象，并保留相邻业务对象作为排序线索。
9. Graph filter：校验候选是否在同一应用或可达关系路径中。
10. 向量语义召回：使用 OpenAI-compatible Embedding 将用户目标向量化，从 pgvector 的 `metadata_vector_documents` 中召回语义相近候选，用于补充口语化、同义表达和弱关键词场景。
11. 权威性校验：向量候选必须通过 `document_id` 回到 MySQL `metadata_search_documents`，找不到真实元数据文档的结果直接丢弃。
12. 规则融合排序：融合名称、编码、应用路径、对象类型、真实主对象保护、辅助对象降权、同步时间、记忆权重和有限向量加分。
13. Explanation：生成匹配原因。

向量化语义检索已经作为增强路径接入，但不能作为唯一决策来源。名称精确、编码精确、显式应用路径和上一轮真实 `schemaCode` 继承的优先级必须高于向量相似度；向量检索不能生成或猜测 `schemaCode`，也不能绕过真实云枢 Metadata Index、图谱校验和确认机制。

当前 MVP 的候选排序规则已经针对真实云枢 CRM 场景做了保护：通用商机/客户问题会优先考虑 `zlcsstcrm` 应用下的核心对象，精确名称“商机”“客户”优先于“商机管理、商机分配、区域商机统计、客户经营策略”等辅助对象；`test/测试/管理/分配/变更/转移/统计/报表/查询/修正` 等对象会降权。真实环境验收中，“系统有多少商机？”第一候选为 `zlcsstcrm / 商机 / int_bu_oppor`，“系统有多少个客户？”第一候选为 `zlcsstcrm / 客户 / crm_customer`。

## 7. 澄清与候选推荐

当缺少必要信息时，Agent 进入澄清模式：

- 缺少应用：推荐相近应用。
- 缺少实体：推荐候选实体或表单。
- 缺少记录：要求用户提供条件或从上轮结果选择。
- 缺少必填字段：列出缺失字段。
- 关系路径不唯一：展示可选路径。

澄清模式的设计要求：

- 不执行云枢工具，不修改数据，不触发写操作确认。
- 不仅说“没有理解”，而是说明已理解到的业务对象、动作线索或上下文引用。
- 给用户一个可推进任务的问题，例如“你想查询/分析哪个应用或表单？”、“你想执行查询、统计、分析、修改还是新增？”、“是否有时间、负责人、阶段等筛选条件？”。
- 如果 Metadata Index 有候选对象，展示部分候选对象，帮助用户选择。
- 用户后续补充后，下一轮应尽量承接澄清上下文并继续执行；这是后续多轮上下文增强重点。
- 如果用户表达中已经具备明确动作和对象，例如“系统有多少商机？”，Agent 必须直接执行查询/计数，不应进入澄清模式。

候选推荐使用 Markdown 表格，包含应用、实体/表单、功能、匹配原因、风险等级和建议输入。

### 7.0 ReAct + Reflection MVP 执行记录

当前实现的阶段定义：

| 阶段 | 当前职责 | 输出位置 |
| --- | --- | --- |
| Observe | 读取用户目标、最近会话上下文、最近运行态对象、是否引用上一轮结果 | `steps[0]`、`plan_json.observe` |
| Think | 识别原始意图、最终意图、元数据候选、缺失槽位、置信度 | `steps[1]`、`plan_json.think` |
| Act | 执行元数据检索、云枢运行态查询、分析回答、澄清或创建确认 | `steps[2]`、工具调用审计 |
| Reflect | 检查对象、风险、工具结果、是否需要用户输入、是否等待确认 | `steps[3]`、`reflection_json` |

为了让业务用户聚焦结果，读数据类回答正文默认只展示结论、分析和建议，不再直接展示执行过程。业务问答的强制主线仍然是：用户自然语言 -> 抽取动作、业务对象、维度、筛选条件 -> 从真实云枢 Metadata Index 匹配应用/模型 -> 使用真实 `schemaCode` 查询运行态数据 -> 基于真实返回数据做统计、聚合或大模型分析 -> 输出结论。

当前 MVP 的可审计执行过程保留在调试和审计数据中，而不是默认混入助手正文：

- `AgentResponse.steps`：返回 Observe、Think、Act、Reflect 阶段摘要，供调试视图或开发排查使用。
- `agent_runs.plan_json`：记录原始目标、结构化意图、候选对象、计划动作和风险判断。
- `agent_runs.reflection_json`：记录执行结果、反思检查、是否需要用户补充和是否等待确认。
- `tool_calls`：记录脱敏后的工具输入输出、运行态来源、total、returned、原始摘要和错误信息。
- `assistantMessage.metadataJson.agentRunId`：每条 CPClaw 助手回复必须持久化对应 Agent Run ID；运行态查询回复仍保留 `source=runtime-query`、`entityName`、真实 `schemaCode` 等上下文字段，保证多轮继承不被破坏。

每轮助手消息在正式回答上方恢复真实执行链：当前会话新回复优先使用 SSE 的 `thought / execution` 事件；刷新或打开历史会话后，从助手消息 `metadataJson.executionTimeline` 恢复同一时间线，审计页仍可通过 `agentRunId` 查看 `plan_json`、`reflection_json` 和工具调用记录。历史消息若没有完整时间线，但仍有 `source=runtime-query`、`schemaCode`、total 等旧 metadata，只展示有限运行态摘要，不补造未发生的步骤。

这些内容是对执行链的可审计摘要，不是模型不可验证的隐藏思维。对话流按真实事件展示可核验的步骤过程与结论；更完整的输入输出、对象编码和工具调用仍从审计页、Agent Run 和工具调用记录查看。

助手正文使用 Markdown 作为标准格式，前端必须解析并安全渲染标题、列表、加粗、代码和表格。历史消息中如果已经包含旧版 `### 执行过程` 前置块，前端展示层需要兼容隐藏，只保留业务结论部分。

长耗时任务的前端反馈必须先于最终回答。用户提交后，前端插入不落库的 CPClaw 占位回复，随后只展示后端实时发送的真实 `thought / execution` 事件；所有执行和校验事件结束后，才接收 `answer_start / answer_delta / answer_end` 流式正文，最后由 `final` 回填持久化消息。前端不得预置固定步骤，也不得伪造 `schemaCode`、工具结果或模型结论。

本地 fallback 只能用于连接、同步流程或测试保护，不得作为业务问答的数据来源。在 `sourceEndpoint=local-fallback` 时，Agent 必须停止生成业务数量、分析结论或建议，并提示用户配置真实云枢地址、账号并重新同步元数据。`system_opportunity`、`system_customer` 等演示编码不得出现在用户业务答案中，也不得被描述为真实云枢 `schemaCode`。

典型 Reflection 结果：

- 读数据成功：`status=completed`，`passed=true`。
- 模糊或缺槽位：`status=needs_user_input`，`needsUserInput=true`。
- 写操作：`status=pending_confirmation`，必须等待用户确认。

示例：用户在查询商机后说“给第一条商机写一条跟进记录”，`Observe` 会看到上一轮结果，`Think` 会识别为写操作并匹配“系统商机”，但发现缺少“跟进内容”；`Reflect` 会要求进入澄清，不创建确认、不执行写入。

## 7.1 明确统计查询

统计/计数查询是 `query_data` 的直接执行场景。判定规则：

- 动作词命中“多少、几条、几个、数量、统计、总计、一共、总共、共有”等。
- 业务对象词可从用户话术中提取并命中本地 Metadata Index，例如“商机”。
- 不涉及新增、修改、删除、流程、Action 或附件上传。

示例：用户说“系统有多少商机？”，Agent 应执行：意图识别为 `query_data`，Query rewrite 后保留业务词“商机”，从真实云枢 Metadata Index 匹配 CRM 商机等实体，使用真实 `schemaCode` 调用云枢运行态查询并返回总数。只有真实对象无法匹配、运行态查询失败或统计条件本身会导致明显查错时，才停止生成业务结论并进入澄清/错误说明。

示例：用户说“系统有多少个客户？”，Agent 应执行：意图识别为 `query_data`，业务对象识别为“客户”，匹配真实客户实体，调用云枢运行态查询并直接返回客户总数，不应在真实对象明确时要求用户补充应用或表单。

计数回答正文只展示业务结论，例如“总计 **237** 条”。真实 `schemaCode`、运行态来源和原始数据摘要必须记录在 Agent 步骤和审计工具调用中。如果两个问题在审计记录中返回相同编码、相同原始摘要，或 fallback 环境返回业务总数，应视为 P0 回归缺陷。

## 7.2 分析类任务

分析类任务是查询类任务的增强形态：

1. 识别用户希望分析、诊断、洞察、趋势判断或给建议。
2. 从本地 Metadata Index 匹配业务对象，例如“商机”或“客户”。
3. 抽取分析维度，例如按年、按阶段、按负责人、按行业或按金额区间。
4. 调用云枢运行态查询数据；分析类请求应尽量分页拉取完整数据，当前 MVP 对按年分析采用 `pageSize=200`、最多 20,000 条保护上限，避免只拿第一页样本就给出全量结论。
5. 对结构化维度明确的问题，先做可解释聚合，例如“每年的客户量情况怎么样？”应按记录中的创建时间、登记时间或年份字段汇总客户量。
6. 优先调用用户配置的 OpenAI-compatible 模型进行分析。
7. 模型不可用时返回规则分析兜底，至少包括结论摘要、关键发现、风险信号和下一步建议；按年分析类兜底至少包括年度分布、峰值年份、趋势判断和下一步下钻建议。
8. 如果对象未匹配，进入澄清模式，说明已理解为分析类请求并引导用户补充对象。

示例：用户说“分析系统中的商机信息”，Agent 应识别为 `analyze_data`，匹配真实商机相关实体，先查询商机运行态数据，再生成分析结论，而不是要求用户重新说明“云枢对象”。如果当前没有真实商机模型或运行态查询失败，应说明原因，不得基于演示数据编造分析。

示例：用户说“每年的客户量情况怎么样？”，Agent 应识别为 `analyze_data`，匹配客户实体，查询客户运行态数据，按年份聚合后返回“按年客户量分析”和趋势判断；只有无法匹配客户对象或记录中没有可识别年份字段时，才说明缺失并引导补充。

真实环境验收结果：真实云枢同步后共有 29 个应用、922 个模型和 951 条搜索文档；“系统有多少商机？”命中 `int_bu_oppor` 并返回 total=237；“系统有多少个客户？”命中 `crm_customer` 并返回 total=8694；“每年的客户量情况怎么样？”命中 `crm_customer`，运行态 total=8694、returned=8694 后再做年度聚合。

## 8. DAG 执行计划

执行计划支持单轮多应用、多实体、多步骤 DAG。

```json
{
  "planType": "dag",
  "nodes": [
    {
      "id": "query_customers",
      "intent": "query_data",
      "app": "CRM",
      "entity": "客户",
      "risk": "low",
      "confirmationRequired": false
    },
    {
      "id": "query_opportunities",
      "intent": "query_data",
      "dependsOn": ["query_customers"],
      "relationPath": "客户 -> 商机",
      "risk": "low",
      "confirmationRequired": false
    },
    {
      "id": "create_todos",
      "intent": "create_data",
      "dependsOn": ["query_opportunities"],
      "app": "任务管理",
      "entity": "待办",
      "risk": "medium",
      "confirmationRequired": true
    }
  ]
}
```

规则：

- 查询节点可以自动执行。
- 写节点、删除节点、流程节点、Action 节点、附件上传节点等待确认。
- 下游节点只能引用上游显式输出。
- 任一节点低置信度或关系路径不唯一时进入澄清模式。
- 同一计划内多个写节点合并展示确认摘要。

## 9. 多轮上下文解析

当前 MVP 已落地对象级上下文继承：当上一轮助手消息来自真实云枢运行态查询时，助手消息会保存最近命中的业务对象、真实 `schemaCode`、运行态 total、returned 和来源。下一轮如果用户输入“分别在什么阶段？”“都处于什么阶段？”“按负责人看一下”“金额分布呢？”这类缺少显式业务对象但明显承接上一轮结果的追问，`Observe` 会从最近助手消息的 metadata 中取出上一轮运行态对象，并生成有效目标，例如：

```text
上一轮：系统有多少商机？ -> 命中 商机 / int_bu_oppor
下一轮：都处于什么阶段？ -> 有效目标 int_bu_oppor 商机 都处于什么阶段？
```

这样做有两个要求：

- 继承的只是对象上下文，不复用上一轮业务结论；下一轮仍然要重新经过 Metadata Index 匹配和云枢运行态查询。
- 继承时必须带上真实 `schemaCode`，不能只带对象名称；否则在多个应用存在同名对象时，追问可能被重新排序到默认对象。例如上一轮命中“项目基础数据 / 商机 / business_opportunity”后，追问“分别在什么阶段？”仍应继续使用 `business_opportunity`，不能跳回 CRM 的 `int_bu_oppor`。

对象级继承的审计信息写入 `steps[0]`、`agent_runs.plan_json.observe` 和 `metadata_search` 工具输入中的 `effectiveQuery`。普通对话正文仍只展示业务结论，不展示内部执行过程。

阶段、状态、负责人、金额等追问不能复用上一轮总数结论，必须重新调用云枢运行态接口。当前阶段分布实现会优先读取运行态列表返回字段；当列表字段不足以识别阶段/状态时，只允许受控补充极小详情样本，避免大对象分析无限拖慢。“都处于什么阶段？”这类口语化表达也必须识别为阶段聚合，而不是返回全量明细列表。真实云枢验证中，“系统有多少商机？”返回 `int_bu_oppor` total=237 后，追问“都处于什么阶段？”可以继承 `int_bu_oppor` 并快速返回阶段样本分布。当前为了稳定交互，阶段/状态优先采用轻量样本策略；当列表字段或详情样本不足以支持全量判断时，回答必须说明基于已返回样本，不能伪称全量。后续应基于字段级元数据、列表显示字段配置或云枢聚合接口提升全量统计准确性。

新老客户、客户省份、商机省份等追问也属于对象级上下文继承场景。用户在同一会话中先问“系统有多少个客户？”，再问“新客户多还是老客户多”时，Agent 应继承上一轮 `客户 / crm_customer`，识别分析维度为“新老客户”，在运行态记录中查找 `customerType`、`customer_type`、`客户类型`、`新老客户`、`是否新客户`、`客户属性`、`客户性质` 等字段并聚合；如果字段不存在，应快速说明缺失字段并引导用户确认表结构，不得全量盲扫或编造结论。

连续追问性能策略：普通列表追问只返回少量摘要并不补详情；省份、阶段、新老客户等维度追问优先轻量列表样本；只有明确需要详情且样本量受控时才调用详情接口。任何新维度都必须先考虑响应时间上限和字段缺失兜底，避免第三轮、第四轮对话因为详情补齐或大模型泛分析卡住。

当前尚未完成的是记录级上下文引用：例如“第一条”“刚才那个客户”“给它上传附件”需要绑定到具体记录 ID、行号、过期时间和权限范围。记录级引用仍按下方可引用对象表设计推进。

系统维护可引用对象表：

```json
{
  "referenceId": "ctx_result_001_row_1",
  "conversationId": "...",
  "messageId": "...",
  "appId": "crm",
  "entityId": "opportunity",
  "recordId": "...",
  "displayName": "华东项目商机",
  "rowIndex": 1,
  "expiresAt": "..."
}
```

支持表达：

- “第一条” -> `rowIndex=1`。
- “这个商机” -> 最近关注实体为商机。
- “刚才那个客户” -> 最近用户选择的客户。
- “给它上传附件” -> 最近操作对象。

引用对象不唯一、过期或结果集合过大时，必须重新确认或重新查询。

## 10. 附件驱动填单

附件链路：

```text
Upload
  -> File validation
  -> Text/table/OCR extraction
  -> Structured extraction
  -> Field mapping against metadata graph
  -> Draft form data
  -> Confirmation
  -> CloudPivot create/update + upload attachment
```

Agent 需要把附件解析结果、用户补充描述和表单字段映射合并为执行计划。附件原文中的敏感内容不写入 Prompt 或长期记忆。

## 11. 工具集

### 11.1 云枢 API 工具

- `cloudpivot_login`
- `cloudpivot_sync_metadata`
- `cloudpivot_query_records`
- `cloudpivot_get_record`
- `cloudpivot_create_record`
- `cloudpivot_update_record`
- `cloudpivot_delete_record`
- `cloudpivot_run_action`
- `cloudpivot_submit_workflow`
- `cloudpivot_upload_attachment`

### 11.2 浏览器自动化工具

- `browser_login`
- `browser_navigate`
- `browser_click`
- `browser_fill`
- `browser_extract_table`
- `browser_upload_file`
- `browser_screenshot`

### 11.3 本地能力工具

- `metadata_search`
- `metadata_graph_lookup`
- `memory_lookup`
- `context_reference_resolve`
- `audit_log`

## 12. Reflection 检查项

执行前检查：

1. 意图是否明确。
2. 应用、实体、字段、Action 是否均来自本地图谱。
3. 跨实体关系路径是否存在且唯一。
4. 是否缺少必填字段。
5. 是否涉及高风险操作。
6. 是否需要用户确认。
7. 是否包含附件上传。
8. 是否可能批量影响多条记录。

执行后检查：

1. 查询结果是否为空。
2. 返回字段是否符合预期。
3. 记录数量是否异常。
4. 写操作是否成功。
5. 附件是否上传成功。
6. 云枢返回错误是否已脱敏。
7. 是否需要继续下一步 DAG 节点。
8. 是否需要写入记忆。

## 13. 记忆写入规则

只有以下情况写入长期记忆：

- 用户明确选择候选功能。
- 用户明确纠正系统理解。
- 执行成功且匹配路径稳定。
- 管理员维护业务别名。

不写入：

- 低置信度猜测。
- 失败执行路径。
- 敏感凭据。
- 附件敏感原文。
- 一次性临时上下文。
