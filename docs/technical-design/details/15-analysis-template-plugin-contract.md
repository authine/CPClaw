# 声明式分析模板插件契约

> 文档类型：关键技术方案｜状态：Manifest 校验与发布生命周期已实现并验证；版本回滚与生产签名治理为方案/规划。

## 1. 目标

将具象分析场景从 CPClaw 框架和通用云枢 Skill 中移出。框架提供任务生命周期、权限、确认、审计、通用查询/聚合/关联与结果渲染；模板插件以声明式 Manifest 提供领域词汇、字段角色、受限风险规则和输出结构。

## 2. 分层职责

| 层 | 允许内容 | 禁止内容 |
| --- | --- | --- |
| CPClaw 框架 | 生命周期、主体、权限、确认、审计、SSE、通用任务结果 | 业务对象词、固定字段别名、行业规则、场景分支 |
| 云枢 Skill | 元数据同步、字段类型/系统字段语义、Provider、读写契约、计划校验 | 场景词汇、固定报告章节、对象专用风险规则 |
| 模板插件 | 触发提示、对象选择器、字段角色、受限算子、输出章节、风险表达式 | 任意 Java/JS、SQL、URL、凭据、绕过审计的执行器 |

## 3. Manifest 契约

模板以持久化 `manifest_json` 表示，至少包含：

- `id`、`version`、`skillId`、`enabled`、发布状态和内容签名；
- `activation`：触发提示和基于元数据的对象选择器；
- `fieldSelectors`：时间、度量、维度、人员、关联等字段角色；
- `plan`：仅允许 `count`、`filter`、`groupBy`、`aggregate`、`rank`、`join`、`riskDetect`、`render`；
- `output`：章节、图表、最大长度和完成条件；
- `riskRules`：受限 JSON/CEL 表达式，不允许执行任意代码。

不匹配模板时，Runtime 只返回基于真实元数据和数据的通用结构化结果，不猜测领域含义。

## 4. 生命周期与接口

`AnalysisTemplateAdminController` 提供 `/api/templates/analysis`：

| 接口 | 作用 | 服务端约束 |
| --- | --- | --- |
| `GET` | 查看模板与发布状态 | 仅管理员 |
| `POST` | 保存草稿 | 校验 Manifest、白名单算子并生成内容签名 |
| `POST /{id}/review` | 审核通过/驳回 | 仅管理员，记录状态 |
| `POST /{id}/publish` | 发布 | 再校验后才可激活 |
| `POST /{id}/disable` | 停用 | 运行时不再解析 |

Runtime 只解析 `enabled=true` 且 `publicationStatus=approved` 的模板。默认场景模板不会自动注册为 Spring Bean，必须经显式配置和发布治理启用。

## 5. 数据与审计

迁移 `V29__declarative_analysis_template_manifests.sql` 为既有模板增加 Manifest、发布状态和签名字段；`V30__remove_builtin_domain_template_hints.sql` 清除内置领域触发提示。运行记录以 `skillId + templateId + version` 作为可追溯维度。

版本历史、回滚和签名密钥轮换尚未落地，不能视为当前发布能力；上线前应增加不可变版本表、审批记录、回滚指针和密钥轮换策略。

## 6. 验收

1. 框架/通用 Skill 的边界扫描不出现业务对象词、行业风险词或固定字段别名。
2. 未审核、停用或无效 Manifest 不能参与运行时计划。
3. 模板只能生成元数据可验证且在白名单内的计划。
4. 任一结果可关联到 Skill、模板版本、审计和证据。
