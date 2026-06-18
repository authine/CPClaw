# CPClaw 文档目录

本目录用于存放 CPClaw 的产品设计、技术设计和测试用例文档。所有文档在提交 Git 仓库前都需要经过用户确认。

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
  technical-design/
    00-technical-blueprint.md
    details/
      01-system-architecture.md
      02-agent-design.md
      03-cloudpivot-integration.md
      04-data-model.md
      05-security.md
  test-cases/
    07-test-plan.md
    08-mvp-test-cases.md
    09-mvp-validation-record.md
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

阅读方式：先读产品设计蓝图，理解项目要解决什么问题、面向哪些用户、核心体验是什么；再进入需求和产品规划细节。

## 技术设计

位于 `technical-design/`。

- `00-technical-blueprint.md`：技术设计蓝图，从整体上说明技术目标、总体架构、组件边界、元数据知识图谱、混合检索、Agent、模型调用、附件和安全路径。
- `details/01-system-architecture.md`：系统架构详细设计，描述 Vue 3 前端、Spring Boot 后端、MySQL、Agent、模型网关、元数据服务和云枢连接器的整体结构。
- `details/02-agent-design.md`：Agent 详细设计，描述 ReAct + Reflection、意图类型、工具集、执行计划和高风险操作策略。
- `details/03-cloudpivot-integration.md`：云枢集成详细设计，描述登录、Token、设计态元数据同步、运行态操作、附件上传和浏览器兜底策略。
- `details/04-data-model.md`：数据模型详细设计，描述 MySQL 表结构、应用级知识图谱、检索索引、附件、记忆和审计。
- `details/05-security.md`：安全详细设计，描述凭据存储、环境变量、日志脱敏、Prompt 安全、操作确认、附件安全和 GitHub 安全要求。

阅读方式：先读技术设计蓝图，理解项目整体技术路径；再按组件或专项进入详细设计。

## 测试用例

位于 `test-cases/`。

- `07-test-plan.md`：测试计划，描述单元测试、集成测试、E2E 测试、安全验收和 MVP 验收标准。
- `08-mvp-test-cases.md`：MVP 测试用例，按可执行用例拆分 MVP 验收项。
- `09-mvp-validation-record.md`：MVP 验证执行记录，记录后端测试、前端构建、运行态验证和提交推送状态。

## 推荐阅读顺序

1. `product-design/00-product-blueprint.md`
2. `technical-design/00-technical-blueprint.md`
3. `product-design/details/01-requirements.md`
4. `product-design/details/02-product-plan.md`
5. `technical-design/details/01-system-architecture.md`
6. `technical-design/details/02-agent-design.md`
7. `technical-design/details/03-cloudpivot-integration.md`
8. `technical-design/details/04-data-model.md`
9. `technical-design/details/05-security.md`
10. `test-cases/07-test-plan.md`
11. `test-cases/08-mvp-test-cases.md`
12. `project-management/00-project-restart-plan.md`
13. `project-management/01-team-work-plan.md`
14. `project-management/02-progress-dashboard.md`

## 提交规则

- 文档内容需要先由用户确认，再提交到 Git 仓库。
- 文档、配置样例、提交记录中不得包含明文密码、Token、Cookie、API Key。
- `.env`、本地 Claude 配置、日志、浏览器状态和密钥文件不得入库。
