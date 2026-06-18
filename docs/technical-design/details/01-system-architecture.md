# CPClaw 系统架构详细设计

> 本文是系统架构专项详细设计。整体技术路径见 `../00-technical-blueprint.md`。

## 1. 技术栈

### 1.1 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Markdown 渲染组件
- 附件上传组件
- SSE 或流式响应预留

### 1.2 后端

- Java 21
- Spring Boot 3
- Spring Web MVC
- Spring Data JPA
- Flyway
- MySQL
- Playwright Java
- OpenAI 兼容模型网关

### 1.3 中间件

- MySQL：会话、设置、凭据密文、云枢元数据、应用级知识图谱、审计、记忆。
- Elasticsearch/OpenSearch：全文检索、中文分词、向量检索和融合召回。
- 本地文件存储或 MinIO：附件存储。
- 可选 Milvus：大规模向量检索。
- 可选 Redis：短期状态、锁、任务进度。

## 2. 总体架构

```text
Vue 3 Web UI
  -> Spring Boot Backend API
    -> Conversation Service
    -> Settings Service
    -> Model Gateway
    -> Attachment Service
    -> Agent Orchestrator
    -> Metadata Service
    -> Search Service
    -> Memory Service
    -> CloudPivot Connector
    -> Credential Service
    -> Audit Service
    -> MySQL Persistence
    -> Elasticsearch/OpenSearch
```

## 3. 后端包结构建议

```text
server/src/main/java/com/cpclaw/
  CpClawApplication.java
  common/
    api/
    config/
    security/
    exception/
  settings/
  credential/
  conversation/
  attachment/
  model/
  metadata/
  search/
  memory/
  agent/
  cloudpivot/
  audit/
```

## 4. 模块边界

### 4.1 Web UI

负责用户交互，不处理敏感凭据，不直接调用云枢。

关键组件：

- `ModelSelector`：模型选择。
- `ThinkingToggle`：思考模式开关。
- `AttachmentUploader`：附件上传和解析状态。
- `MarkdownMessage`：Markdown 渲染。
- `CandidateSelector`：候选应用/功能选择。
- `PlanPreviewCard`：执行计划预览。
- `RiskConfirmationCard`：高风险确认。
- `ResultTable`：查询结果表格。
- `FieldMappingTable`：附件填单字段映射。
- `ExecutionTimeline`：执行步骤时间线。

### 4.2 Backend API

负责会话、消息、设置、架构同步、智能体执行、附件和审计。

核心 API：

- `GET /api/settings`
- `PUT /api/settings/cloudpivot`
- `POST /api/settings/cloudpivot/test`
- `GET /api/settings/models`
- `POST /api/settings/models`
- `PUT /api/settings/models/{id}`
- `POST /api/settings/models/{id}/test`
- `PUT /api/settings/search`
- `POST /api/settings/search/test`
- `POST /api/conversations`
- `GET /api/conversations`
- `GET /api/conversations/{id}`
- `POST /api/conversations/{id}/messages`
- `GET /api/conversations/{id}/events`
- `POST /api/conversations/{id}/confirmations/{confirmationId}`
- `POST /api/attachments`
- `POST /api/metadata/sync`
- `GET /api/metadata/apps`
- `GET /api/metadata/apps/{appId}/graph`
- `POST /api/search/metadata`
- `GET /api/audit/agent-runs/{id}`

### 4.3 Model Gateway

统一封装 OpenAI 兼容模型调用。业务代码不直接拼接模型请求。普通用户可配置自己的模型 API 地址、API Key、模型名称和思考模式能力，用于自己的对话式操作。

请求抽象包含：

- `modelConfigId`
- `model`
- `messages`
- `stream`
- `temperature`
- `max_tokens`
- `response_format`
- `metadata`
- `extra_body`

设计原则：

- API Base URL、API Key、模型名称来自 `model_configs`。
- API Key 运行时解密，只用于 HTTP Header。
- 思考模式通过模型能力配置控制。
- 供应商差异放入适配器或 `extra_body`。
- 输入输出只记录脱敏摘要。

### 4.4 Agent Orchestrator

负责从用户消息到云枢操作的完整链路。Agent 只访问本地 Metadata Index 做匹配，不实时连接云枢检索。

### 4.5 Metadata Service

负责管理员授权下的云枢设计态元数据同步、知识图谱构建、检索文档生成、索引写入和索引重建。普通用户对话时只使用已初始化的本地元数据索引，不实时连接云枢做能力检索。

### 4.6 CloudPivot Connector

封装云枢访问：

- 登录与 Token 管理。
- 连接测试。
- 设计态元数据同步。
- 运行态数据查询和写入。
- Action 和流程提交。
- 附件上传。
- 浏览器自动化兜底。

## 5. 部署形态

MVP 推荐单体部署：

```text
cpclaw/
  web/
  server/
  docs/
```

后续可拆分：

- browser-worker：浏览器自动化任务。
- metadata-worker：元数据同步和索引重建。
- attachment-worker：附件解析任务。
- agent-worker：长任务 Agent 执行。

## 6. 安全边界

- 前端不保存明文密码、Token、Cookie、API Key。
- 后端配置加密密钥 `CPC_ENCRYPTION_KEY`。
- MySQL 中只保存凭据密文。
- 日志统一脱敏。
- 高风险工具调用需要用户确认。
- `.env`、本地配置、日志、浏览器状态和密钥文件不得入库。
