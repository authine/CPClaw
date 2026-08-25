# CPClaw 技术设计蓝图

> 文档类型：技术蓝图｜状态：当前技术基线。它定义稳定边界和事实口径；实现细节、接口契约和历史问题分别由专项技术设计、测试记录和历史评审维护。

## 1. 设计目标与硬约束

CPClaw 将自然语言业务目标转为受约束的云枢操作：本地元数据负责定位，模型辅助理解与规划，后端负责权限、确认、执行、审计和持久化。模型不能生成不存在的对象、字段或 API，不能直接发起任意 HTTP 请求，也不能绕过云枢权限或写操作确认。

MySQL 是运行时权威库；Flyway 是唯一 schema 变更入口，H2 仅限测试。数据库、加密密钥和可选向量库均由部署环境配置，系统设置页不编辑这些基础设施参数。

## 2. 当前运行架构

```text
Vue 3 工作台
  → Spring Boot API
    → 会话 / 设置 / 附件 / 元数据 / Agent / 审计
      → MySQL（配置、凭据密文、元数据、图谱、会话、确认、审计、记忆）
      → CloudPivot（连接测试、元数据同步、运行态查询与已确认操作）
      → 可选 pgvector（可重建的语义召回增强）
      → OpenAI-compatible 模型（受约束规划与表达）
  CPClaw Web / OpenClaw 兼容客户端
    → Host Adapter（Conversation / MCP）
    → Semantic Task Runtime（计划、状态、可见事件、结果体验）
    → Skill Registry → 云枢契约与运行态连接器
```

前端只访问后端 API；后端持有云枢与模型凭据。MySQL Metadata Index 是可执行对象的唯一权威来源；pgvector、未来的 OpenSearch/Elasticsearch/Milvus 都只能增强召回，不能产生或替换 `schemaCode`。

## 3. Agent 边界

受限 Agent 的实际链路是：输入与会话上下文 → 内部记忆召回 → 本地元数据候选 → 模型或确定性计划 → 元数据/API/权限/风险校验 → 查询或确认预览 → 执行与 Reflection 校验 → 脱敏审计和业务化响应。

ReAct/Reflection 是内部控制模型。统一运行时向宿主输出版本化的结果体验协议：任务状态、业务化结论、产物、下一步交互和可见任务摘要。对话只显示动态的业务任务摘要，例如理解目标、拆解任务、核验范围、获取数据、汇总、生成确认和校验结果；不得显示记忆、私有推理、Prompt、编码、接口路径或原始载荷。模型不可用或计划不合规时退回确定性路径，并明确标记“受限计划”。

## 4. 数据与安全边界

- 配置、模型、云枢元数据、图谱、会话、结果引用、确认、审计和受限记忆持久化到 MySQL；凭据只保存 AES-GCM 密文或引用。
- 云枢元数据同步在管理员连接下进行；普通用户执行仍以其运行态权限为准。
- 查询结论必须包含数据来源、范围和受限口径。`sequenceStatus`、流程/单据生命周期等系统状态不得当作业务阶段。
- 写入、删除、流程、Action 和附件上传必须绑定具体计划、影响范围、有效期和一次性确认；未验证连接器不开放。

## 5. 实施状态矩阵

| 主题 | 当前状态 | 权威专项 |
| --- | --- | --- |
| 持久化、迁移、密钥门禁 | 已实现并验证 | `details/12-persistent-database-runtime.md` |
| 元数据同步、图谱、检索 | 已实现；向量索引可选 | `details/07-cloudpivot-metadata-sync.md`、`09-graphify-all-applications-knowledge-graph.md` |
| 受限模型规划、记录引用、内部记忆 | 已实现但受身份/范围模型限制 | `details/02-agent-design.md` |
| 智能问数与分析报告 | 第一阶段已实现，受规模/关系深度限制 | `details/08-intelligent-data-inquiry-report-engine.md` |
| 通用写入、流程、Action、附件填单 | 规划/按白名单准入 | `details/10-conversational-operation-architecture.md` |
| 统一语义任务运行时 / 云枢 MCP | 已接入 DelegationSpec、EvidenceBundle、EvidenceCompletion 和同轮重放门禁；统一 TaskGateway、continuation token、Web/MCP/CLI 共用执行内核仍在增量实施 | `details/18-openclaw-delegation-evidence-contract.md`、`details/02-agent-design.md`、`16-cloudpivot-mcp-gateway.md`、`17-cpclaw-cloudpivot-mcp-delivery-architecture.md` |

## 6. 文档治理

- `01` 系统边界与部署；`02` Agent；`03` 云枢连接器；`04` 数据模型；`05` 安全；`06` 技术策略；`07/09` 元数据与图谱；`08` 问数语义；`10` 对话操作准入；`11/12` 设置与持久化运行；`13` 真实数据验证方法；`16/17` 云枢 MCP 与对外交付架构。
- `07-intent-action-output-chain-review-2026-07-02.md` 是历史评审，不得作为当前设计依据；其中结论须由当前专项文档和测试记录重新确认。
- 任何产品、接口、数据模型、确认策略或验收变化，先更新对应唯一权威文档，再更新测试和进度记录。
