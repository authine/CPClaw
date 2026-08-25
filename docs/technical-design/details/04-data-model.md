# 数据模型详细设计

> 文档类型：专项技术设计｜状态：MySQL 运行时基线已实现，迁移以 Flyway V1–V10 为准。本文描述权威数据边界；可重建索引和规划表不得被误解为已开放业务能力。

> 本文是数据模型专项详细设计。整体技术路径见 `../00-technical-blueprint.md`。

## 1. 设计原则

数据库使用 MySQL。MySQL 是 CPClaw 的权威主库，用于保存系统设置、加密凭据、会话、消息、云枢应用级知识图谱、审计、附件、结果引用和记忆。正常运行时禁止回退至 H2；H2 仅存在于测试 JVM。

当前阶段使用 MySQL 中的 `metadata_search_documents` 作为权威 Metadata Index，并使用 PostgreSQL pgvector 中的 `metadata_vector_documents` 作为可重建语义向量增强索引。Elasticsearch/OpenSearch 是后续检索索引增强，可从 MySQL 重建；Milvus 是后续大规模向量检索替换或扩展方案。

## 2. 系统设置与凭据

### 2.1 system_settings

保存非敏感系统设置。

字段：

- `id`
- `cloudpivot_base_url`
- `cloudpivot_login_url`
- `cloudpivot_admin_url`
- `cloudpivot_portal_url`
- `cloudpivot_app_center_url`
- `cloudpivot_username`
- `search_engine_type`
- `search_engine_url`
- `created_at`
- `updated_at`

### 2.2 encrypted_credentials

保存加密凭据。

字段：

- `id`
- `credential_type`
- `credential_owner_type`
- `credential_owner_id`
- `encrypted_value`
- `iv`
- `auth_tag`
- `expires_at`
- `created_at`
- `updated_at`

凭据类型包括云枢密码、云枢 Token/Cookie、模型 API Key、检索中间件密码等。不得保存明文；数据库与向量库的基础设施密码来自部署环境或密钥管理，不作为系统设置业务表单。

### 2.3 model_configs

保存 OpenAI 兼容模型配置。

字段：

- `id`
- `name`
- `api_base_url`
- `model_name`
- `api_key_credential_id`
- `supports_thinking`
- `default_thinking_enabled`
- `default_temperature`
- `default_max_tokens`
- `extra_body_json`
- `enabled`
- `created_at`
- `updated_at`

## 3. 会话与消息

### 3.1 conversations

字段：

- `id`
- `title`
- `default_model_config_id`
- `default_thinking_enabled`
- `created_at`
- `updated_at`

### 3.2 messages

字段：

- `id`
- `conversation_id`
- `role`
- `content`
- `model_config_id`
- `thinking_enabled`
- `metadata_json`
- `created_at`

存储约束：

- `content` 使用 MySQL `LONGTEXT`，用于保存完整助手回答。阶段分布、运行态分析、大模型总结等回答可能超过 MySQL `TEXT` 的 64KB 上限，不能在入库前静默截断业务结论。
- `metadata_json` 使用 MySQL `LONGTEXT`，用于保存最近运行态对象、真实 `schemaCode`、total、returned、sourceEndpoint 等上下文信息，支撑同一会话内的对象级追问。
- 与消息强相关的审计长文本字段也使用 `LONGTEXT`，包括 `agent_runs.plan_json`、`agent_runs.reflection_json`、`tool_calls.input_json_masked`、`tool_calls.output_json_masked`、`tool_calls.error_message_masked` 以及确认单中的长文本字段，避免右侧“后端处理流程”和审计追踪因工具输出较长而写入失败。

### 3.3 conversation_contexts

保存会话级上下文和可引用对象。

字段：

- `id`
- `conversation_id`
- `message_id`
- `reference_type`
- `reference_key`
- `app_id`
- `entity_id`
- `record_id`
- `display_name`
- `row_index`
- `payload_json`
- `expires_at`
- `created_at`

### 3.4 query_result_references

Flyway V7 新增。保存可审计的列表结果引用：`conversation_id`、`message_id`、`agent_run_id`、`app_code`、`schema_code`、`record_id`、`row_index`、脱敏展示快照和 `expires_at`。当前用于“第一条/刚才那条”到明确记录的绑定；权限范围哈希和记录版本尚未落库，相关高风险能力不得超出已验证删除路径。

### 3.5 message_feedback_events

Flyway V14 新增不可覆盖的回答反馈事件表。字段包括 `conversation_id`、`message_id`、可空的 `agent_run_id`、`actor_type/actor_id`、`action_type`（`set` / `clear`）、`feedback_type`（`like` / `dislike`）、可选脱敏 `reason` 和 `created_at`。当前反馈状态由同一消息主体的最后一个事件投影得出；这避免了覆盖历史，便于分析点赞、点踩、切换和取消。V1 使用本地用户主体，接入身份模型后必须替换为认证用户标识。

## 4. 附件

### 4.1 attachments

字段：

- `id`
- `conversation_id`
- `message_id`
- `original_filename`
- `content_type`
- `file_size`
- `storage_path`
- `sha256`
- `status`
- `created_at`

### 4.2 attachment_extractions

字段：

- `id`
- `attachment_id`
- `extraction_type`
- `text_content_masked`
- `tables_json_masked`
- `structured_json_masked`
- `field_mapping_json`
- `status`
- `error_message_masked`
- `created_at`
- `updated_at`

附件原文不写入 Prompt 或长期记忆。解析结果落库前需按安全规则脱敏。

## 5. 云枢应用级知识图谱

### 5.1 cloudpivot_apps

字段：

- `id`
- `app_code`
- `name`
- `description`
- `raw_json`
- `sync_batch_id`
- `synced_at`

### 5.2 cloudpivot_entities

对应云枢业务模型。

字段：

- `id`
- `app_id`
- `entity_code`
- `name`
- `entity_type`
- `raw_json`
- `synced_at`

### 5.3 cloudpivot_data_items

对应业务模型数据项。

字段：

- `id`
- `entity_id`
- `data_item_code`
- `name`
- `data_type`
- `required`
- `is_reference`
- `reference_entity_id`
- `description`
- `raw_json`
- `synced_at`

说明：历史版本曾使用 `cloudpivot_entity_fields` 和 `field_code` 命名保存同类信息。当前业务语义统一为云枢“数据项”，新同步链路写入 `cloudpivot_data_items`。

存储约束：云枢设计态返回的数据项、实体和关联配置可能包含较长的控件配置、选项集、关联表单映射和子表结构。`cloudpivot_apps.raw_json`、`cloudpivot_entities.raw_json`、`cloudpivot_data_items.raw_json`、`cloudpivot_entity_relations.raw_json` 均使用 MySQL `LONGTEXT`，避免真实元数据初始化时因 `TEXT` 64KB 上限导致 `Data too long for column 'raw_json'`。

### 5.4 cloudpivot_entity_relations

通过关联表单数据项建立实体间关系。

字段：

- `id`
- `app_id`
- `source_entity_id`
- `source_data_item_id`
- `target_entity_id`
- `relation_type`
- `relation_name`
- `raw_json`
- `synced_at`

关系生成约束：`source_data_item_id` 必须能回查到真实云枢数据项；目标实体必须来自已同步的真实实体模型；只能从明确配置了目标模型的关联表单/关联引用数据项生成关系，不能通过原始 JSON 全文碰撞实体编码来推断关系。

### 5.5 cloudpivot_forms

字段：

- `id`
- `app_id`
- `entity_id`
- `form_code`
- `name`
- `raw_json`
- `synced_at`

### 5.6 cloudpivot_view_actions

来源于业务模型视图中的操作按钮。

字段：

- `id`
- `app_id`
- `entity_id`
- `view_code`
- `action_code`
- `name`
- `risk_level`
- `raw_json`
- `synced_at`

### 5.7 cloudpivot_form_actions

来源于应用设计中表单设计的操作按钮。

字段：

- `id`
- `app_id`
- `entity_id`
- `form_id`
- `action_code`
- `name`
- `risk_level`
- `raw_json`
- `synced_at`

### 5.8 metadata_graph_snapshots

保存 Graphify 全应用图谱版本。`status` 使用 `BUILDING / ACTIVE / STALE`，查询只读取当前 `ACTIVE` 版本。核心字段包括来源同步批次、应用数、节点数、边数、未解析边数、覆盖率、导出路径和构建时间。默认保留最近两个版本。

### 5.9 metadata_graph_nodes

保存当前图快照的应用、实体、数据项和 API 节点。`stable_key` 使用应用编码、实体编码和数据项编码组成，与每次元数据同步重新生成的数据库 UUID 解耦；同一快照内 `snapshot_id + stable_key` 唯一。

节点同时保存对象类型、本地对象 ID、应用/实体范围、来源 URI、Graphify community、置信度和扩展属性 JSON。

### 5.10 metadata_graph_edges

保存应用包含实体、实体包含字段、字段引用实体、实体关系和 API 能力边。边通过稳定节点键连接，支持按来源、目标和边类型查询。云枢明确关系标为 `EXTRACTED`，通用 API 适用关系标为 `INFERRED`；未解析引用不生成悬空边，而计入快照完整性指标。

详细投影、接口和配置参见 `09-graphify-all-applications-knowledge-graph.md`。

## 6. 检索索引数据

### 6.1 metadata_search_documents

字段：

- `id`
- `object_type`
- `object_id`
- `app_id`
- `entity_id`
- `form_id`
- `name`
- `code`
- `aliases_json`
- `search_text`
- `embedding_text`
- `graph_path`
- `risk_level`
- `sync_batch_id`
- `indexed_at`
- `created_at`

### 6.2 metadata_vector_documents

存储位置：PostgreSQL + pgvector。该表不是权威元数据来源，只能由 MySQL `metadata_search_documents` 重建。

字段：

- `document_id`：对应 `metadata_search_documents.id`。
- `object_type`
- `object_id`
- `name`
- `code`
- `graph_path`
- `risk_level`
- `embedding_text`
- `embedding_model`
- `embedding_dimension`
- `sync_batch_id`
- `embedding vector(N)`
- `indexed_at`
- `updated_at`

配套表：`metadata_vector_schema`，保存当前向量维度、Embedding 模型和更新时间。若模型返回维度变化，系统可清空并重建 `metadata_vector_documents`。

安全要求：向量库连接、Embedding Base URL、模型名和开关通过环境变量配置；数据库密码和 Embedding API Key 不得写入文档、代码、日志或 Git。

降级要求：pgvector 不可用、Embedding 服务不可用或向量表重建失败时，不影响 MySQL Metadata Index 写入和确定性检索。

### 6.3 后续 Elasticsearch/OpenSearch 索引

建议索引名：`cpclaw_metadata_v1`。

字段：

- `object_type`
- `app_id`
- `entity_id`
- `form_id`
- `name`
- `code`
- `aliases`
- `search_text`
- `embedding_text`
- `embedding`
- `graph_path`
- `risk_level`
- `updated_at`
- `synced_at`

## 7. 记忆与别名数据

### 7.1 agent_memories（已实现）

`agent_memories` 是当前统一的记忆存储表，按 `memory_scope` 区分 `SYSTEM`、`USER`、`SESSION` 和 `TASK`。其中：

- `USER`：当前主体的可管理长期记忆；
- `SYSTEM`：租户级全局记忆，仅超级管理员可在设置页查看、添加和删除；
- `SESSION`：运行时会话记忆，绑定 `conversation_id + owner_principal + tenant_id`，只用于当前任务上下文召回，不是设置项，不返回 `/api/settings/memory`，也不提供人工编辑入口；
- `TASK`：任务级临时记忆，按任务生命周期清理。

会话记忆仍保存在这张用户记忆存储表中，仅保存脱敏的成功映射或明确纠正，默认 TTL 30 天；会话删除时一并删除。设置 API 只返回 `USER` 和（超级管理员可见的）`SYSTEM`。

### 7.2 长期记忆与别名（规划）

未来如果需要拆分 `user_memories`、`organization_memories`、`correction_memories` 或 `business_aliases`，必须保持上述设置/运行时边界不变；当前版本不新增物理表，避免把会话记忆误解为可配置的系统设置。

字段：

- `id`
- `alias`
- `target_type`
- `target_id`
- `app_id`
- `confidence`
- `source`
- `created_at`
- `updated_at`

## 8. Agent 与审计

### 8.1 agent_runs

字段：

- `id`
- `conversation_id`
- `user_message_id`
- `intent_summary`
- `status`
- `plan_json`
- `reflection_json`
- `risk_level`
- `created_at`
- `completed_at`
- `model_config_id`、`assistant_message_id`
- 脱敏输入/输出摘要、输入/输出/总 Token、`duration_ms`、`tool_call_count`（Flyway V10）

### 8.2 tool_calls

字段：

- `id`
- `agent_run_id`
- `tool_name`
- `input_json_masked`
- `output_json_masked`
- `status`
- `error_message_masked`
- `created_at`
- `completed_at`

### 8.3 confirmations

字段：

- `id`
- `conversation_id`
- `agent_run_id`
- `plan_id`
- `risk_level`
- `summary`
- `affected_objects_json`
- `changes_json_masked`
- `attachments_json`
- `status`
- `expires_at`
- `created_at`
- `confirmed_at`

### 8.4 agent_model_calls

Flyway V10 新增模型调用分析投影：关联 Agent Run、模型配置/名称、操作、状态、脱敏输入输出/错误摘要、真实 Provider 返回的 Token 和耗时。Token 未返回时保持空值，不按字符估算；该表用于日志分析，不替代完整审计链。

### 8.5 回答反馈审计

`message_feedback_events` 是用户质量反馈的事件源，不替代 `agent_runs` 或模型调用统计。分析时以 `agent_run_id` 关联模型、业务意图、Skill 和 Token；无关联运行记录的回答仍保留消息级反馈。原因最长 500 字符，写入前执行统一敏感字段脱敏。

## 9. 索引建议

- `messages.conversation_id`
- `conversation_contexts.conversation_id`
- `attachments.conversation_id`
- `cloudpivot_apps.app_code`
- `cloudpivot_entities.app_id, entity_code`
- `cloudpivot_data_items.entity_id, data_item_code`
- `cloudpivot_entity_relations.source_entity_id, target_entity_id`
- `metadata_search_documents.app_id, object_type`
- `agent_runs.conversation_id`
- `tool_calls.agent_run_id`
- `confirmations.conversation_id`

## 10. 安全策略

- 凭据表只保存密文、IV、认证标签。
- 所有工具输入输出落库前脱敏。
- 禁止保存 Cookie、Token、密码、API Key 明文。
- 附件解析结果按敏感字段规则脱敏。
- 检索文档不得包含凭据、Token、Cookie 或附件敏感原文。
## 记忆设置与可见性补充（当前无登录阶段）

记忆分为 `SYSTEM`、`USER`、`SESSION`、`TASK` 四类。设置页只管理 `USER` 和 `SYSTEM`；`SESSION` 仍写入 `agent_memories`，但只作为当前会话的内部运行时上下文，不能通过设置 API 查询、添加或删除。系统全局记忆使用 `SYSTEM`，所有用户可在任务执行时受控使用，但只有超级管理员可以在设置页查看、添加和删除；个人记忆使用 `USER`，仅绑定当前主体和租户。设置 API 为 `/api/settings/memory`，全局写入/删除服务端强制校验超级管理员身份，不能依赖前端隐藏菜单。

当前未接入登录时，默认主体为 `huangj`（黄杰，18124691161），并标记为超级管理员，以便完成单用户部署验证。正式登录后由身份解析器替换，不改变记忆表和接口契约。
