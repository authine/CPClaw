# CPClaw 文档治理与阅读入口

本目录是 CPClaw 的产品、技术和验收事实源。设计先定义边界，代码与测试更新实施状态；历史评审不能替代当前规范。

## 1. 状态字典

- **已实现并验证**：代码路径与自动化测试，或必要的真实环境证据均已具备。
- **已实现但有限制**：主链路存在，但受环境、规模、身份、权限或发布条件限制。
- **方案/规划**：目标与验收已定义，未作为当前能力承诺。
- **历史评审**：某一时间点的发现与建议，不直接代表当前设计。

## 2. 权威文档矩阵

| 主题 | 权威文档 | 用途 |
| --- | --- | --- |
| 项目入口 | [`project-overview.md`](project-overview.md) | 项目定位、运行基线与事实边界 |
| 产品蓝图 | [`product-design/00-product-blueprint.md`](product-design/00-product-blueprint.md) | 定位、用户、体验、范围与状态 |
| 产品概要设计 | [`product-design/details/02-product-plan.md`](product-design/details/02-product-plan.md) | 信息架构、交互对象、路线图 |
| 规范需求 | [`product-design/details/01-requirements.md`](product-design/details/01-requirements.md) | 可验收产品和非功能需求 |
| 技术架构 | [`technical-design/00-technical-blueprint.md`](technical-design/00-technical-blueprint.md) | 分层、控制面/执行面/数据面、技术债 |
| 统一云枢 Runtime | [`technical-design/details/20-universal-yunshu-skill-runtime-spec.md`](technical-design/details/20-universal-yunshu-skill-runtime-spec.md) | 生命周期、接口、结果、宿主契约 |
| 模板插件 | [`technical-design/details/15-analysis-template-plugin-contract.md`](technical-design/details/15-analysis-template-plugin-contract.md) | 场景扩展与治理边界 |
| OpenClaw 集成 | [`technical-design/details/19-openclaw-class-ai-tool-integration.md`](technical-design/details/19-openclaw-class-ai-tool-integration.md) | MCP/CLI 接入和联调规则 |
| 数据与安全 | [`technical-design/details/04-data-model.md`](technical-design/details/04-data-model.md)、[`05-security.md`](technical-design/details/05-security.md) | 数据边界、凭据、审计与确认 |
| 测试基线 | [`test-cases/07-test-plan.md`](test-cases/07-test-plan.md) | 测试层次、门禁和证据 |
| 项目状态 | [`project-management/PROGRESS.md`](project-management/PROGRESS.md) | 变更、验证和遗留风险 |

## 3. 推荐阅读顺序

1. [`project-overview.md`](project-overview.md)
2. [`product-design/00-product-blueprint.md`](product-design/00-product-blueprint.md)
3. [`product-design/details/01-requirements.md`](product-design/details/01-requirements.md)
4. [`product-design/details/02-product-plan.md`](product-design/details/02-product-plan.md)
5. [`technical-design/00-technical-blueprint.md`](technical-design/00-technical-blueprint.md)
6. [`technical-design/details/20-universal-yunshu-skill-runtime-spec.md`](technical-design/details/20-universal-yunshu-skill-runtime-spec.md)
7. [`technical-design/details/15-analysis-template-plugin-contract.md`](technical-design/details/15-analysis-template-plugin-contract.md)
8. [`technical-design/details/19-openclaw-class-ai-tool-integration.md`](technical-design/details/19-openclaw-class-ai-tool-integration.md)
9. [`test-cases/07-test-plan.md`](test-cases/07-test-plan.md) 和相关验收记录
10. [`project-management/PROGRESS.md`](project-management/PROGRESS.md)

## 4. 专项文档路由

- `01-system-architecture.md`：部署、服务边界和运行时调用图。
- `02-agent-design.md`：受限规划与模型边界。
- `03-cloudpivot-integration.md`、`07-cloudpivot-metadata-sync.md`、`09-graphify-all-applications-knowledge-graph.md`：云枢连接、同步和图谱。
- `04-data-model.md`、`05-security.md`、`12-persistent-database-runtime.md`：数据、凭据、安全与运行门禁。
- `06-key-technical-strategy.md`：关键技术方案总览。
- `08-intelligent-data-inquiry-report-engine.md`：通用分析与报告实现边界。
- `10-conversational-operation-architecture.md`：受控写操作和流程的阶段性准入。
- `16/17/18/19/20`：MCP 网关、交付架构、委派证据、宿主接入和统一 Runtime。
- `07-intent-action-output-chain-review-2026-07-02.md`：**历史评审**，不可作为现行规范。

## 5. 变更规则

1. 先更新唯一权威文档，再更新测试与项目进度；代码不能成为唯一事实源。
2. 文档不得包含密码、Token、Cookie、API Key、个人 `.env`、日志或浏览器状态。
3. 框架层不得写具体业务对象、字段别名或行业规则；若出现具体场景，应明确属于模板插件或测试夹具。
4. 文档变更须经用户确认后才能提交 Git 仓库。
5. 能力状态变更必须同时更新蓝图、专项文档、测试证据和 `PROGRESS.md`。
