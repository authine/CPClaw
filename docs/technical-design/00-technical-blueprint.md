# CPClaw 技术架构蓝图

> 文档类型：技术架构｜状态：当前技术基线。统一 Runtime 规范以 `details/20-universal-yunshu-skill-runtime-spec.md` 为准；OpenClaw 接入以 `details/19-openclaw-class-ai-tool-integration.md` 为准。

## 1. 架构目标与不可变约束

CPClaw 将自然语言目标变为受约束的 Skill 任务。平台负责 Skill 注册、任务生命周期、权限、计划校验、确认、审计和宿主接入；具体 Skill 负责自身领域的理解与执行适配。云枢 Skill 在其内部采用元数据驱动方式定位应用、对象、字段和操作能力，但该机制属于云枢 Skill 的实现边界，不是 CPClaw 平台层的统一定位。

- 框架层不包含具体业务对象、字段别名、行业规则或场景触发词。
- 模型不能构造未验证的能力、对象、字段或 API，不能直接发起任意 HTTP 请求。
- MySQL 是持久化权威库，Flyway 是唯一 schema 入口，H2 仅用于测试。
- 前端和外部宿主只调用后端；云枢、模型和加密凭据不离开后端安全边界。

## 2. 分层架构

```text
┌─────────────────────────────────────────────────────────────────┐
│ Host adapters: Vue Web / MCP JSON-RPC / CLI / future Remote API  │
│ 协议适配、会话/主体绑定、进度转发、结果展示；不做业务判断         │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ TaskGateway → SemanticTaskRuntime                                │
│ 生命周期、幂等、续接、状态/事件、取消、证据完成度、结果信封       │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ SkillRegistry → SkillExecutor                                    │
│ Skill 解析、能力/权限边界、执行上下文                              │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ YunshuMcpTaskExecutor / YunshuSkillRuntime                       │
│ UNDERSTAND · DISCOVER · PLAN · VALIDATE · EXECUTE · ANALYZE      │
│ 元数据发现、计划校验、证据编排；无场景硬编码                      │
└──────────────────────────────┬──────────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ YunshuProvider / Metadata / Runtime API / Model gateway          │
│ 同步元数据、只读查询、受确认操作、模型流式；Provider 隔离协议     │
└─────────────────────────────────────────────────────────────────┘
```

`ConversationService` 和 `McpGatewayController` 都进入同一 `TaskGateway → SkillRegistry → YunshuMcpTaskExecutor` Spring 单例。`WebTaskExperienceAdapter` 仅把统一 `TaskExperienceEnvelope` 转成旧页面所需的 `AgentResponse`，不做语义解析或云枢执行。

## 3. 控制面、执行面与数据面

| 平面 | 组件 | 责任 |
| --- | --- | --- |
| 控制面 | 设置、主体、Skill 注册、模板治理、权限、确认、审计 | 谁可配置、调用和发布什么能力 |
| 执行面 | TaskGateway、SemanticTaskRuntime、SkillRegistry、Yunshu Runtime | 任务生命周期、计划校验、幂等、续接、结果和可见进度 |
| 数据面 | MySQL/Flyway、元数据图谱、会话/记忆、任务事件、审计 | 可追溯状态、索引、证据和安全保存 |
| 集成面 | YunshuProvider、MCP、CLI、Remote API | 云枢协议隔离与外部宿主适配 |

## 4. 关键数据流

1. Host Adapter 将请求转为 `SemanticTaskRequest` 与受限 `SkillExecutionContext`。
2. `SemanticTaskRuntime` 绑定主体、安装实例、幂等键，记录运行和事件。
3. `SkillRegistry` 解析已批准 Skill；未安装/未授权则返回澄清，不降级为任意 API 调用。
4. 云枢 Runtime 基于同步元数据理解目标、发现候选、生成计划并校验权限/风险。
5. 查询/分析返回证据、口径、缺口和产物；写风险操作只生成确认，确认后才可续接执行。
6. Runtime 生成 `TaskExperienceEnvelope`；Web/MCP/CLI 分别进行协议展示，不能重新执行业务语义。

## 5. 模板插件边界

分析场景模板是控制面管理的声明式扩展：可声明字段角色、受限算子、风险规则和输出结构。框架和通用云枢 Skill 只解释已审核 Manifest；模板不得嵌入任意代码、SQL、URL 或凭据。详见 `details/15-analysis-template-plugin-contract.md`。

## 6. 当前实现状态

| 能力 | 状态 | 事实边界 |
| --- | --- | --- |
| MySQL/Flyway、加密凭据、元数据图谱、会话/审计/记忆 | 已实现并验证 | 生产配置不由业务设置页编辑 |
| 统一任务运行时、Web/MCP 共享执行器、CLI 转发 | 已实现并验证 | 外部生产身份与网络 E2E 未完成 |
| 查询/通用分析、结果引用、确认、证据完成度 | 已实现并验证 | 真实对象/字段/权限依赖同步和云枢环境 |
| 模板 Manifest 生命周期 | 已实现并验证 | 版本回滚/签名轮换未完成 |
| 写入、流程处理、导入、附件填单、复杂 DAG | 方案/规划 | 需要真实契约、补偿、权限和 E2E 证据 |

## 7. 兼容与技术债

旧 `AgentOrchestrator → YunshuAgentOrchestrator` 仍保留为 `/api/agent/preview` 兼容入口，且内部存在历史场景关键词规则。它不在 Web/MCP 默认主链路，却仍是可注入的第二套执行路径，不符合严格的“框架无业务固化”目标。该入口必须在下一次架构收敛中删除或改造成只委派 `TaskGateway` 的无语义适配器；在完成前不得把“遗留路径已清除”写为已实现。

## 8. 发布门禁

正式外部发布前必须补齐可信 OIDC/JWT 主体校验、真实云枢写入/流程/导入契约、后台任务断线恢复、模板版本回滚和跨宿主网络 E2E。Mock 或 H2 测试通过不能替代这些门禁。
