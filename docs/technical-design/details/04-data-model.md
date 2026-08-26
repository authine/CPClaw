# 数据模型详细设计

> 文档类型：专项技术设计｜状态：Flyway V1–V30 为 schema 唯一事实源；下表是当前模型的逻辑分组，不替代迁移脚本。

## 1. 模型原则

- MySQL 持久化运行态与审计事实；Flyway 只增不改地管理结构演进。
- 凭据只保存密文或安全引用；消息和审计只保存必要的脱敏摘要。
- 元数据、计划、确认、结果引用与任务事件必须可关联，以支持追溯与重放保护。
- 向量/搜索索引可重建，不能作为可执行元数据的权威来源。

## 2. 逻辑域

| 域 | 主要表 | 责任 |
| --- | --- | --- |
| 配置与凭据 | `system_settings`、`encrypted_credentials`、`model_configs` | 系统设置、密文凭据、模型配置 |
| 会话与附件 | `conversations`、`messages`、`conversation_contexts`、`attachments`、`attachment_extractions` | 对话、消息元数据、上下文、附件处理 |
| 元数据图谱 | `cloudpivot_apps`、`cloudpivot_entities`、`cloudpivot_data_items`、`cloudpivot_entity_relations`、`cloudpivot_forms`、操作表、图谱投影表 | 同步的应用、对象、字段、关系、系统/业务字段分类和图谱 |
| 检索 | `metadata_search_documents`、`metadata_vector_documents` | 可重建的全文/向量召回增强 |
| 任务与委派 | `semantic_task_runs`、`semantic_task_events` | 任务、幂等键、主体/安装实例、委派规范、完成度、证据、续接与事件 |
| 结果与确认 | `query_result_references`、`confirmations` | 记录级引用、计划哈希、影响范围和一次性确认 |
| 记忆 | `agent_memories` | 会话、个人、全局范围及主体/租户隔离 |
| 审计 | `agent_runs`、`tool_calls`、`agent_model_calls`、`message_feedback_events`、`mcp_tool_call_audits` | 受控输入输出摘要、模型调用、反馈和 MCP 审计 |
| 模板与安装 | `report_skill_templates`、`mcp_installations`、`mcp_installation_bindings` | 模板 Manifest/发布状态与外部安装绑定 |

## 3. 关键完整性约束

- `semantic_task_runs` 以 `channel + installation_key + external_principal + client_request_id` 约束幂等；`turn_id` 约束同一外部轮次。
- 任务事件按 `task_id + event_sequence` 有序；续接记录父任务、主体、安装实例和一次性消费状态。
- `query_result_references` 绑定来源消息/任务、对象编码、记录 ID、行号和过期时间，作为后续写风险操作的唯一记录锚点。
- `confirmations` 绑定计划哈希、执行开始时间和脱敏影响范围；过期、变更或主体不一致必须失效。
- `agent_memories` 使用 `memory_scope`、`owner_principal`、`tenant_id` 和优先级区分会话、个人和全局记忆。
- `report_skill_templates` 的 Manifest、发布状态和签名由 V29 引入；V30 清除内置领域触发提示。

## 4. 元数据语义

同步时保存中文描述、字段类型与 `field_category`。系统字段可用于标识、生命周期和技术过滤，默认不作为分析指标或业务维度；业务字段是洞察和模板字段角色匹配的主要来源。分类规则必须由云枢元数据同步器维护，不能在框架层硬编码对象专用字段。

## 5. 生命周期与保留

会话、任务、审计、确认和模板需要按企业数据保留政策设置清理与导出策略；当前代码未声明通用的数据保留任务。生产上线前必须补充保留期限、删除审批、备份恢复和隐私访问流程。
