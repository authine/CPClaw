# 数据模型详细设计

> 本文是数据模型专项详细设计。整体技术路径见 `../00-technical-blueprint.md`。

## 1. 设计原则

数据库使用 MySQL。MySQL 是 CPClaw 的权威主库，用于保存系统设置、加密凭据、会话、消息、云枢应用级知识图谱、审计、附件和记忆。

当前阶段优先使用 MySQL 中的 `metadata_search_documents` 作为 Metadata Index，先实现非向量确定性检索。Elasticsearch/OpenSearch 是后续检索索引增强，可从 MySQL 重建；Milvus 是后续可选向量库，也应能从 MySQL 和检索文档重建。

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

凭据类型包括云枢密码、云枢 Token/Cookie、模型 API Key、检索中间件密码等。不得保存明文。

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

### 5.3 cloudpivot_entity_fields

对应业务模型数据项。

字段：

- `id`
- `entity_id`
- `field_code`
- `name`
- `data_type`
- `required`
- `is_reference`
- `reference_entity_id`
- `description`
- `raw_json`
- `synced_at`

### 5.4 cloudpivot_entity_relations

通过关联表单字段建立实体间关系。

字段：

- `id`
- `app_id`
- `source_entity_id`
- `source_field_id`
- `target_entity_id`
- `relation_type`
- `relation_name`
- `raw_json`
- `synced_at`

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

### 6.2 后续 Elasticsearch/OpenSearch 索引

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

## 7. 记忆数据

### 7.1 user_memories

保存用户级表达习惯、常用应用和成功选择。

字段：

- `id`
- `user_id`
- `memory_type`
- `content_json`
- `source_type`
- `source_id`
- `confidence`
- `expires_at`
- `created_at`
- `updated_at`

### 7.2 organization_memories

保存组织级别名和常见操作路径。

### 7.3 correction_memories

保存用户纠错产生的映射关系。

### 7.4 business_aliases

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

## 9. 索引建议

- `messages.conversation_id`
- `conversation_contexts.conversation_id`
- `attachments.conversation_id`
- `cloudpivot_apps.app_code`
- `cloudpivot_entities.app_id, entity_code`
- `cloudpivot_entity_fields.entity_id, field_code`
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
