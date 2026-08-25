# CPClaw 文档目录

本目录是 CPClaw 的产品、技术和验收事实源。文档先于实现定义边界，代码事实和测试结果用于更新“实施状态”，历史评审不得替代当前规范。

## 文档状态与职责

文档中的状态只允许使用以下四类：

- **已实现并验证**：代码、自动化测试或真实环境结果均有证据。
- **已实现但有限制**：主链路存在，仍有环境、规模或权限边界。
- **方案/规划**：已定义目标和验收，尚未作为当前能力承诺。
- **历史评审**：记录某个时间点的问题与建议，不直接代表当前实现。

职责边界：`00-*` 只维护稳定蓝图；`details/01` 维护规范需求；`details/02` 维护版本节奏；其余专项文档只维护一个主题；`test-cases/` 维护可执行验收证据；`project-management/` 维护过程状态。重复内容必须链接到唯一权威文档。

## 文档层级

CPClaw 文档采用“总蓝图 + 专项详细设计 + 测试验收”的层级结构：

```text
docs/
  README.md
  CLAUDE.md
  project-overview.md
  product-design/
    00-product-blueprint.md
    details/
      01-requirements.md
      02-product-plan.md
      03-conversational-operation-requirements.md
      04-system-settings-workbench.md
      05-persistent-configuration-and-metadata.md
      06-architecture-review-and-real-data-validation.md
  technical-design/
    00-technical-blueprint.md
    details/
      01-system-architecture.md
      02-agent-design.md
      03-cloudpivot-integration.md
      04-data-model.md
      05-security.md
      06-key-technical-strategy.md
      07-cloudpivot-metadata-sync.md
      07-intent-action-output-chain-review-2026-07-02.md (历史)
      08-intelligent-data-inquiry-report-engine.md
      12-persistent-database-runtime.md
  test-cases/
    07-test-plan.md
    08-mvp-test-cases.md
    09-mvp-validation-record.md
      15-conversational-operation-acceptance-matrix.md
      16-conversational-operation-test-report.md
  project-management/
    00-project-restart-plan.md
    01-team-work-plan.md
    02-progress-dashboard.md
```

## 项目管理

位于 `project-management/`。

- `00-project-restart-plan.md`：项目重启总体计划，说明当前代码基线、阶段目标、关键任务、风险和验证记录。
- `01-team-work-plan.md`：团队工作计划，按项目经理、产品经理、后端工程师、前端工程师、UI 设计师和测试工程师拆分任务与进度。
- `02-progress-dashboard.md`：项目进度看板，汇总总体进度、员工进度、功能进度、R0 看板和阻塞风险。

## 产品设计

位于 `product-design/`。

- `00-product-blueprint.md`：产品设计蓝图，从整体上说明 CPClaw 的产品愿景、定位、体验原则、核心用户旅程、MVP 边界和演进路径。
- `details/01-requirements.md`：需求说明，描述 CPClaw 的目标、范围、核心能力和安全要求。
- `details/02-product-plan.md`：产品规划详细设计，描述用户角色、页面、功能域和产品原则。
- `details/03-conversational-operation-requirements.md`：对话式系统操作 V1 需求方案，定义五类能力、分阶段范围、验收标准和非目标。
- `details/04-system-settings-workbench.md`：系统设置工作台的信息架构、连接测试、元数据分层查看和日志分析入口。
- `details/05-persistent-configuration-and-metadata.md`：配置、凭据、元数据与图谱的持久化边界及重启验收标准。
- `details/06-architecture-review-and-real-data-validation.md`：架构评审、自动化测试和真实环境验证的流程与证据规则。

阅读方式：先读产品设计蓝图，理解项目要解决什么问题、面向哪些用户、核心体验是什么；再进入需求和产品规划细节。

## 技术设计

位于 `technical-design/`。

- `00-technical-blueprint.md`：技术设计蓝图，从整体上说明技术目标、总体架构、组件边界、元数据知识图谱、当前非向量检索、Agent、模型调用、附件和安全路径。
- `details/01-system-architecture.md`：系统架构详细设计，描述 Vue 3 前端、Spring Boot 后端、MySQL、Agent、模型网关、元数据服务和云枢连接器的整体结构。
- `details/02-agent-design.md`：Agent 详细设计，描述 ReAct + Reflection、意图类型、工具集、执行计划和高风险操作策略。
- `details/03-cloudpivot-integration.md`：云枢集成详细设计，描述登录、Token、设计态元数据同步、运行态操作、附件上传和浏览器兜底策略。
- `details/04-data-model.md`：数据模型详细设计，描述 MySQL 表结构、应用级知识图谱、检索索引、附件、记忆和审计。
- `details/05-security.md`：安全详细设计，描述凭据存储、环境变量、日志脱敏、Prompt 安全、操作确认、附件安全和 GitHub 安全要求。
- `details/06-key-technical-strategy.md`：关键技术策略，固化配置持久化、云枢元数据驱动意图匹配、非向量检索、澄清收敛、流式输出和真实数据原则。
- `details/07-cloudpivot-metadata-sync.md`：云枢设计态元数据采集、数据项和关联关系识别。
- `details/08-intelligent-data-inquiry-report-engine.md`：智能问数、确定性指标、报告图表和数据语义边界。
- `details/09-graphify-all-applications-knowledge-graph.md`：Graphify 全应用元数据图谱设计，描述版本化图投影、稳定键、可信边、邻域查询、兼容导出和全应用验收标准。
- `details/07-intent-action-output-chain-review-2026-07-02.md`：历史评审记录；不得作为当前规范，结论需回填现行专项文档。
- `details/10-conversational-operation-architecture.md`：对话式系统操作 V1 技术方案，定义统一执行计划、记录引用、确认审计、白名单和实施门禁。
- `details/12-persistent-database-runtime.md`：MySQL 运行时门禁、Flyway、密钥稳定性和安全迁移恢复方案。
- `details/11-system-settings-workbench.md`：系统设置工作台接口契约、模型预检与已保存模型复测实现。
- `details/13-architecture-review-and-real-data-validation.md`：真实环境验证的技术门禁、证据要求和停止条件。

阅读方式：先读技术设计蓝图，理解项目整体技术路径；再按组件或专项进入详细设计。

## 测试用例

位于 `test-cases/`。

- `07-test-plan.md`：测试计划，描述单元测试、集成测试、E2E 测试、安全验收和 MVP 验收标准。
- `08-mvp-test-cases.md`：MVP 测试用例，按可执行用例拆分 MVP 验收项。
- `14-graphify-all-applications-validation-2026-07-18.md`：Graphify 全应用元数据图谱的自动化、真实 MySQL、稳定键、跨应用关系和兼容导出验收记录。
- `09-mvp-validation-record.md`：MVP 验证执行记录，记录后端测试、前端构建、运行态验证和提交推送状态。
- `15-conversational-operation-acceptance-matrix.md`：对话式系统操作 V1 的阶段门禁、场景用例和横向回归口径。
- `16-conversational-operation-test-report.md`：P0 整体测试执行记录、用例矩阵、结果和遗留阻塞项。
- `18-model-pre-save-connection-validation.md`：新增 Agent 模型的预检、不落库、字段变更失效与保存门禁用例。

## 推荐阅读顺序

1. `product-design/00-product-blueprint.md`
2. `technical-design/00-technical-blueprint.md`
3. `product-design/details/01-requirements.md`
4. `product-design/details/02-product-plan.md`
5. `product-design/details/03-conversational-operation-requirements.md`
6. `product-design/details/04-system-settings-workbench.md`
7. `product-design/details/05-persistent-configuration-and-metadata.md`
8. `technical-design/details/01-system-architecture.md`
9. `technical-design/details/02-agent-design.md`
10. `technical-design/details/03-cloudpivot-integration.md`
11. `technical-design/details/04-data-model.md`
12. `technical-design/details/05-security.md`
13. `technical-design/details/06-key-technical-strategy.md`
14. `technical-design/details/07-cloudpivot-metadata-sync.md`
15. `technical-design/details/08-intelligent-data-inquiry-report-engine.md`
16. `technical-design/details/09-graphify-all-applications-knowledge-graph.md`
17. `technical-design/details/10-conversational-operation-architecture.md`
18. `technical-design/details/11-system-settings-workbench.md`
19. `technical-design/details/12-persistent-database-runtime.md`
20. `technical-design/details/13-architecture-review-and-real-data-validation.md`
21. `test-cases/07-test-plan.md`
22. `test-cases/08-mvp-test-cases.md`
23. `test-cases/15-conversational-operation-acceptance-matrix.md`
24. `test-cases/16-conversational-operation-test-report.md`
25. `project-management/00-project-restart-plan.md`
26. `project-management/01-team-work-plan.md`
27. `project-management/02-progress-dashboard.md`

## 当前实现状态摘要

| 能力 | 当前口径 | 证据入口 |
| --- | --- | --- |
| MySQL 持久化、Flyway V1–V10 | 已实现并验证；H2 仅测试 | `technical-design/details/12-persistent-database-runtime.md` |
| 元数据同步、应用级图谱、确定性检索 | 已实现并验证；向量检索为可选增强 | `technical-design/details/07-cloudpivot-metadata-sync.md`、`09-graphify-all-applications-knowledge-graph.md` |
| Agent 模型规划、受限 ReAct/Reflection、记忆 | 部分实现；模型规划与内部记忆已接入，通用 DAG/长期组织记忆仍规划 | `technical-design/details/02-agent-design.md` |
| 问数与咨询式分析 | 第一阶段已实现但受数据规模、身份映射和关系深度限制 | `technical-design/details/08-intelligent-data-inquiry-report-engine.md` |
| 写入、流程、Action、附件填单 | 按白名单分阶段开放；当前不能宣称覆盖所有云枢场景 | `product-design/details/03-conversational-operation-requirements.md` |

上述摘要是导航，不是新的需求；具体验收以链接文档和测试记录为准。

## 提交规则

- 文档内容需要先由用户确认，再提交到 Git 仓库。
- 文档、配置样例、提交记录中不得包含明文密码、Token、Cookie、API Key。
- `.env`、本地 Claude 配置、日志、浏览器状态和密钥文件不得入库。

## 设计变更同步规则

所有产品设计、交互设计、Agent 行为、接口契约、数据模型、测试验收口径和项目流程的变更，都必须同步更新文档后再交付。代码实现不能成为唯一事实来源。

- 产品体验或用户流程变化：同步 `product-design/` 下的蓝图、需求或产品规划。
- Agent 能力、意图识别、澄清策略、执行计划、工具调用和确认策略变化：同步 `technical-design/details/02-agent-design.md`。
- 云枢集成、元数据同步、运行态 API 或浏览器兜底变化：同步 `technical-design/details/03-cloudpivot-integration.md`。
- 表结构、索引、审计、记忆或上下文数据变化：同步 `technical-design/details/04-data-model.md`。
- 验收标准或回归场景变化：同步 `test-cases/`。
- 每次任务进展、验证结论和阻塞变化：同步 `project-management/PROGRESS.md`；涉及团队角色状态时同步 `project-management/SUBAGENTS.md`。

CPClaw 的核心设计目标是足够智能地理解用户自然语言意图，能基于本地云枢元数据和会话上下文定位任务对象；信息不足时主动澄清并引导用户补充；意图明确后执行查询、分析或经确认的业务操作；执行后解释结果、依据、风险和下一步建议。
