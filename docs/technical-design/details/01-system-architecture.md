# 系统架构详细设计

> 文档类型：专项技术设计｜状态：当前实现架构。总体蓝图见 `../00-technical-blueprint.md`；云枢 Runtime 契约见 `20-universal-yunshu-skill-runtime-spec.md`。

## 1. 部署组件

| 组件 | 技术 | 责任 |
| --- | --- | --- |
| Web | Vue 3 + TypeScript + Vite | 对话、设置、任务过程、结果与确认展示；只调用 `/api` |
| API 服务 | Java 21 + Spring Boot | 主体、会话、任务、Skill、元数据、连接器、审计和 MCP 网关 |
| MySQL | Flyway 管理 | 配置密文、元数据图谱、会话/记忆、任务、确认和审计 |
| 云枢 | 设计态/运行态 API | 元数据同步、连接测试、授权查询和经确认操作 |
| 模型服务 | OpenAI-compatible | 受限规划、自然语言解释和流式输出 |
| CLI/MCP 宿主 | Node CLI / JSON-RPC | 外部委派协议，复用 API 服务 Runtime |

前端默认端口为 5173，后端默认端口为 8080；Vite 仅代理 `/api`。生产部署必须由环境注入数据库、加密密钥和外部连接配置，禁止将凭据写入仓库。

## 2. 运行时调用图

```text
Web ConversationService ─┐
MCP McpGatewayController ├─> TaskGateway ─> SemanticTaskRuntime
CLI / Remote Adapter ────┘                         │
                                                    ▼
                                             SkillRegistry
                                                    ▼
                                      YunshuMcpTaskExecutor
                                                    ▼
                           YunshuProvider / Metadata / Runtime API / Model
```

所有入口使用同一任务标识、主体范围、幂等策略、事件模型和结果信封。Web 兼容层由 `WebTaskExperienceAdapter` 承担 DTO 转换，不能将旧 `AgentResponse` 反向用于执行。

## 3. 服务边界

- `conversation`：会话、消息、SSE 与 Web 展示适配。
- `task`：`SemanticTaskRequest`、生命周期、状态/事件、续接、取消、幂等和完成度。
- `skill`：Skill 注册、执行上下文和通用能力接口。
- `skill.yunshu`：云枢 Runtime、Provider、元数据发现、计划校验和结果编排。
- `metadata` / `cloudpivot`：同步、图谱、检索和云枢 API 连接。
- `identity` / `memory` / `audit`：主体、受控上下文、确认与脱敏审计。
- `mcp` / `cli`：协议适配与安装实例绑定，不实现云枢业务语义。

## 4. 韧性与错误处理

- 未匹配 Skill/元数据、权限不足或信息不足返回澄清，不执行猜测请求。
- 网络/模型错误转换为可解释终态，保留受控审计，不泄露内部编码或载荷。
- 任务使用幂等键、continuation 票据和事件顺序保护重放与重复续接。
- 长任务逐步发送真实进度与流式回答；无法给出 `final` 时明确失败，不把断流伪装为成功。

## 5. 架构限制

当前尚无可信 OIDC/JWT、后台队列化断线恢复和真实云枢写入/流程/导入 E2E。旧 `/api/agent/preview` 兼容入口仍依赖历史 Agent 编排器，详见技术蓝图的技术债说明；它不应成为新功能入口。
