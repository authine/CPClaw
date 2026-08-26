# CPClaw 项目概览

> 本文是仓库入口摘要，不替代产品、技术或测试规范。

## 项目定位

CPClaw 是多 Skill、元数据驱动、受治理的企业系统对话式任务平台。云枢是首个核心 Skill；平台同时支持其他遵循 Skill 契约的 Markdown/MCP 扩展，并可通过 MCP/CLI 向 OpenClaw 类宿主提供同一云枢能力。

## 当前运行基线

- 后端：Java 21 / Spring Boot 3，默认 8080。
- 前端：Vue 3 / TypeScript / Vite，默认 5173，`/api` 代理到后端。
- 持久化：MySQL + Flyway；H2 仅测试。
- 统一执行：`TaskGateway → SemanticTaskRuntime → SkillRegistry → YunshuMcpTaskExecutor`。
- 对外 MCP：`yunshu_handle_request`，兼容 `cpclaw_cloudpivot_agent`。
- 当前无登录安全模式：固定默认主体 `huangj`；正式主体认证与多租户仍是发布门禁。

## 已验证与未完成

查询/分析、元数据同步与图谱、会话/记忆/审计、Web/MCP/CLI 共享 Runtime、幂等/续接/取消和模板 Manifest 治理已具备自动化证据。真实云枢写入、流程处理、导入、跨断线后台恢复、OIDC/JWT 和跨网络生产 E2E 尚未完成。

## 文档入口

阅读顺序和权威矩阵见 [`README.md`](README.md)。代码与文档不一致时，先记录状态漂移并核对测试证据，再更新规范。
