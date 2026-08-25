# Skill 语义边界

## 事实依据

CPClaw 平台需要处理任务生命周期、Skill 注册、元数据约束、权限、确认、证据、审计和 MCP 交付；“商机/项目/客户”等对象词，以及“比较/占比/分布/排名”等业务问法，只对某个接入系统的 Skill 有意义。

## 设计约束

- `AgentOrchestrator`、MCP 网关和平台运行时不维护具体业务对象词、对象别名或业务分析问法。
- 业务 Skill 通过 `SkillQuestionSemantics` 提供意图、维度、筛选、分页策略、元数据搜索扩展和结果聚合语义。
- 当前云枢 Skill 的实现为 `YunshuQuestionSemantics`。后续 Markdown Skill 或其他系统 Skill 可提供自己的实现，不修改平台框架。
- 元数据检索只消费 Skill 提供的搜索扩展；平台保留通用的全文、向量、路径和对象类型排序能力。

## 运行链路

```text
用户目标
  -> 平台选择已注册 Skill
  -> SkillQuestionSemantics 解析业务语言
  -> 平台按通用任务/证据/确认协议执行
  -> Skill 解释数据和生成领域结果
```

## 验收要求

新增业务对象或业务分析问法时，应新增/更新对应 Skill，不得在 `AgentOrchestrator`、MCP Gateway 或通用任务生命周期中增加关键词分支。
