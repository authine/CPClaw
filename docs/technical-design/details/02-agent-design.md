# CPClaw Agent 详细设计

> 本文是 Agent 专项详细设计。整体技术路径见 `../00-technical-blueprint.md`。

## 1. 设计目标

CPClaw Agent 负责把用户自然语言转换为可审计、可确认、可执行的云枢应用操作。Agent 的匹配和规划只基于本地已同步的 Metadata Index、应用级知识图谱、记忆和会话上下文，不实时连接云枢做应用能力检索。

## 2. 执行模式

CPClaw 采用 ReAct + Reflection，并将 Agent 拆成多阶段流水线，而不是单个大 Prompt。

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
- `summarize_data`：汇总数据。
- `create_data`：新增数据。
- `update_data`：修改数据。
- `delete_data`：删除数据。
- `run_action`：执行业务 Action。
- `submit_workflow`：提交流程或审批动作。
- `open_report`：查看报表。
- `sync_metadata`：同步云枢架构。
- `fill_form_from_attachment`：基于附件和描述填单。
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

置信度需要综合：模型输出、Top1/Top2 分差、候选数量、字段归属、Action 归属、关系路径唯一性、上下文引用是否明确、记忆是否与当前图谱一致。

## 6. 本地能力检索

### 6.1 检索原则

- 只查询本地 Metadata Index 和检索中间件。
- 不实时连接云枢进行能力匹配。
- 先定位应用，再在应用图谱内定位实体、字段、关系路径、视图操作和表单操作。

### 6.2 混合检索

检索链路：

1. Query rewrite：结合会话上下文和记忆改写用户目标。
2. BM25：关键词召回应用、实体、字段、Action、编码、别名。
3. Vector：语义召回同义表达和相近能力。
4. Graph filter：校验候选是否在同一应用或可达关系路径中。
5. Fusion：融合全文分数、向量分数、对象类型、同步时间、记忆权重。
6. Rerank：后续可用模型重排。
7. Explanation：生成匹配原因。

## 7. 澄清与候选推荐

当缺少必要信息时，Agent 进入澄清模式：

- 缺少应用：推荐相近应用。
- 缺少实体：推荐候选实体或表单。
- 缺少记录：要求用户提供条件或从上轮结果选择。
- 缺少必填字段：列出缺失字段。
- 关系路径不唯一：展示可选路径。

候选推荐使用 Markdown 表格，包含应用、实体/表单、功能、匹配原因、风险等级和建议输入。

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
