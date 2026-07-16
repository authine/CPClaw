# CPClaw 技术设计蓝图

## 1. 技术目标

CPClaw 的技术目标是构建一个安全、可审计、可扩展的云枢对话式操作平台。系统通过 Vue 3 前端提供对话入口，通过 Java Spring Boot 后端承载会话、设置、Agent、云枢连接、元数据同步、检索、附件、审计和安全能力。

核心技术路径：

1. 云枢应用元数据先同步到本地 MySQL。
2. MySQL 作为应用级知识图谱的权威主库。
3. 当前阶段采用 MySQL Metadata Index 确定性检索 + PostgreSQL pgvector 向量语义召回的混合检索。精确名称、别名、编码、应用路径、全文关键词、复合业务词和上下文继承仍是主信号，向量检索只作为语义召回增强。
4. Agent 只基于本地 Metadata Index 做意图匹配和能力定位，不实时连接云枢检索。
5. 云枢实时连接只用于连接测试、元数据同步和经确认的运行态操作。
6. 普通用户配置自己的云枢访问账号和密码、模型 API，用于对话式操作自己有权限访问的云枢应用。
7. 管理员配置自己的云枢账号和密码，用于读取目标云枢环境元数据并初始化本地知识图谱和检索索引。
8. AI 模型调用统一采用 OpenAI 兼容格式，通过系统设置配置模型。
9. 高风险操作由后端确认机制拦截，不依赖模型自觉。
8. 凭据、日志、Prompt、附件和 Git 全链路不保存明文敏感信息。

## 2. 技术栈

### 2.1 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Markdown 渲染组件
- 附件上传组件
- SSE 或流式响应预留

### 2.2 后端

- Java 21
- Spring Boot 3
- Spring Web MVC
- Spring Data JPA
- Flyway
- MySQL
- Playwright Java
- OpenAI 兼容模型网关

### 2.3 中间件

MVP 推荐：

- MySQL 8.x：权威业务配置、会话、审计、云枢元数据和应用级知识图谱。
- PostgreSQL + pgvector：元数据语义向量索引，作为 MySQL Metadata Index 的可重建增强索引。
- Elasticsearch 8.x 或 OpenSearch 2.x：后续用于 BM25、中文分词和混合检索增强；当前阶段不作为核心依赖。
- 本地文件存储或 MinIO：附件存储。

后续可选：

- Milvus：后续大规模向量检索替换或扩展方案。
- Redis：短期会话状态、任务进度、锁。
- 消息队列：异步元数据同步、附件解析、索引重建。

## 3. 总体架构

```text
Vue 3 Web UI
  -> Spring Boot Backend API
    -> Conversation Service
    -> Settings Service
    -> Model Gateway
    -> Attachment Service
    -> Agent Orchestrator
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
    -> Metadata Service
       -> MySQL Knowledge Graph
       -> MySQL Metadata Index
       -> PostgreSQL pgvector Metadata Vector Index
       -> Optional Elasticsearch/OpenSearch Index
    -> CloudPivot Connector
       -> API Connector
       -> Browser Automation Connector
    -> Audit Service
    -> Credential Service
```

## 4. 核心模块边界

### 4.1 Web UI

负责对话、模型选择、思考模式开关、附件上传、候选选择、执行计划预览、风险确认、结果展示和历史会话。前端不保存明文凭据，不直接调用云枢。

### 4.2 Backend API

统一对外提供系统设置、会话消息、附件、元数据同步、检索调试、确认和审计接口。

### 4.3 Model Gateway

封装 OpenAI 兼容模型调用。普通用户可配置自己的模型 API 地址、API Key、模型名称、是否支持思考模式、默认思考模式。API Key 运行时解密，仅用于请求 Header，不写日志。

### 4.4 Metadata Service

负责在管理员授权下连接目标云枢环境并同步元数据，标准化为应用级知识图谱，生成可解释检索文档并写入 MySQL Metadata Index，同时在向量检索启用时将检索文档写入 PostgreSQL pgvector。MySQL 是权威主库，pgvector、Elasticsearch/OpenSearch 等索引都属于可重建增强项。

### 4.5 Agent Orchestrator

负责意图理解、槽位抽取、本地检索、候选排序、澄清推荐、DAG 执行计划、风险确认、工具执行、Reflection 和响应生成。

### 4.6 CloudPivot Connector

封装云枢登录、连接测试、设计态元数据同步、运行态查询/写入/Action/流程/附件上传。API 优先，Playwright Java 浏览器兜底。

### 4.7 Attachment Service

负责附件上传、类型校验、存储、文本/表格/OCR 解析、结构化抽取、字段映射和附件上传云枢前的确认预览。

### 4.8 Memory Service

负责会话级上下文、用户级记忆、组织级记忆、纠错记忆和业务别名。记忆参与候选排序，但不能绕过元数据图谱、权限和确认机制。

### 4.9 Audit & Security

记录 Agent run、工具调用、确认记录、模型调用摘要和脱敏错误。所有输入输出落库前脱敏。

## 5. 云枢元数据知识图谱

云枢元数据按应用维度组织为知识图谱：

- 应用：图谱根节点。
- 数据实体：对应云枢业务模型。
- 实体字段：对应业务模型数据项。
- 实体关系：通过关联表单字段建立实体间边。
- 视图操作：来源于业务模型视图中的操作按钮。
- 表单操作：来源于应用设计中表单设计的操作按钮。
- 报表和流程：作为后续能力节点扩展。

Agent 检索和规划必须先定位应用，再在应用图谱内定位实体、字段、关系路径和操作能力。

## 6. 当前混合检索技术路径

当前阶段基于 MySQL Metadata Index 和 PostgreSQL pgvector 实现确定性优先、语义增强、可解释、可降级的混合检索：

1. Query rewrite：结合会话上下文和记忆改写用户目标，剥离“有多少、情况怎么样、全部”等非对象词。
2. 多路召回：按精确名称、业务别名、编码、应用路径 `graphPath`、全文关键词和复合业务词召回候选。
3. Graph filter：约束候选必须属于可达应用、实体、字段和操作路径。
4. 向量语义召回：通过 OpenAI-compatible Embedding 生成查询向量，从 pgvector 召回语义相近的真实元数据文档。
5. 规则融合排序：综合对象类型、名称命中、显式应用路径、真实 CRM 核心对象保护、辅助对象降权、上下文、记忆权重和有限向量加分。
6. Explanation：输出匹配原因、业务关键词、目标对象、`graphPath`、分数、向量相似度和召回依据，供审计与调试查看。
7. 降级策略：Embedding 服务或 pgvector 不可用时，自动退回确定性检索，不影响元数据同步和业务查询主链路。

向量检索不得决定核心业务对象。最终执行使用的 `schemaCode` 必须来自 MySQL Metadata Index，编码精确匹配、显式应用路径和多轮上下文继承优先级必须高于向量相似度。

## 7. Agent 技术路径

Agent 不应是单个大 Prompt，而是可审计、可测试的流水线：

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

宽泛经营分析在上述主链中进入独立的智能问数报告分支：

```text
Metadata Graph Retriever
  -> Insight Planner
  -> Multi-entity Dataset Executor
  -> Deterministic Aggregation Engine
  -> LLM Report Composer
  -> Structured Insight Renderer
```

详细设计见 `details/08-intelligent-data-inquiry-report-engine.md`。报告规划允许大模型根据用户问题和完整邻接元数据图提出关联对象与指标建议，但所有实体、字段、关系和 API 在执行前必须回到本地元数据库校验；数量、金额和比例由程序计算，大模型不得直接生成事实值。
  -> Memory Writer
```

执行计划需要支持 DAG：查询节点可先执行；新增、修改、删除、流程、Action、附件上传节点必须确认；下游节点只能引用上游显式输出。

## 8. OpenAI 兼容模型调用

系统设置中维护多个模型配置：

- API Base URL
- API Key
- 模型名称
- 是否支持思考模式
- 默认是否启用思考模式
- 扩展参数

对话框可选择模型。如果模型支持思考模式，前端展示思考模式开关；后端校验能力后再转换为对应 OpenAI 兼容供应商的扩展参数。所有模型调用只记录脱敏摘要。

## 9. 附件处理技术路径

```text
Upload
  -> File validation
  -> Store original file
  -> Extract text/table/image/OCR
  -> Structured extraction
  -> Field mapping against metadata graph
  -> Draft form data
  -> User confirmation
  -> CloudPivot create/update + upload attachment
```

附件类型、大小、存储路径、访问权限和解析结果都必须纳入安全控制。附件上传云枢前必须展示字段映射和附件预览。

## 10. 安全边界

- 前端不保存明文密码、Token、Cookie、API Key。
- 后端使用 AES-GCM 加密凭据。
- `.env`、本地配置、日志、浏览器状态、密钥文件不得入库。
- Prompt 不包含凭据、Token、Cookie、附件敏感原文。
- 工具输入输出和模型调用摘要落库前脱敏。
- 写操作、删除、流程、Action、批量导出和附件上传必须确认。
- 不绕过云枢权限体系。

## 11. 文档拆分结构

技术设计文档分为：

- 技术设计蓝图：统一说明技术目标、总体架构、组件边界和关键技术路径。
- 系统架构详细设计：前后端、模块、部署和 API 边界。
- Agent 详细设计：意图、检索、规划、Reflection、确认和记忆。
- 云枢集成详细设计：登录、元数据同步、运行态操作、API 探测和浏览器兜底。
- 数据模型详细设计：MySQL 表、知识图谱、检索索引、附件、记忆和审计。
- 安全详细设计：凭据、日志、Prompt、附件、确认、Git 和运维控制。

## 12. 专家评审沉淀

本轮按六个专家视角对蓝图补强：

- 产品策略视角：总蓝图必须让读者先看到 CPClaw 的完整产品意图和旅程，再进入细节。
- 会话 UX 视角：澄清、推荐、确认、解释和 Markdown 展示需要作为核心交互能力。
- 企业落地视角：设置、权限、审计、灰度、安全治理和运维必须进入设计主线。
- 技术架构视角：MySQL 权威主库 + OpenSearch/Elasticsearch 检索索引 + 可选 Milvus 的边界要清晰。
- Agent 架构视角：本地检索、结构化输出、DAG、多轮上下文、附件和记忆需要可测试。
- 安全运维视角：敏感信息治理必须覆盖文档、文件、日志、Prompt、模型调用、附件和 Git。
