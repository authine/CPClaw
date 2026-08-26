# 云枢 MCP Gateway 技术设计

> 文档类型：专项技术设计｜状态：当前 MCP 协议与安全边界；执行链以 `TaskGateway → SemanticTaskRuntime → SkillRegistry` 为准。文中 `McpSemanticTaskService` 仅表示历史适配层名称，不得再承载独立业务执行。

## 1. 身份边界

首期提供 SSE MCP 服务，因此 CPClaw 不假设存在客户端用户或租户 JWT。调用方通过 `x-cpclaw-installation-id` 请求头声明安装实例；该值只标识 MCP 安装实例，不承载身份或权限，不能作为外网访问令牌。

云枢环境地址只由 CPClaw 的“管理员云枢环境”统一提供，个人云枢账号页面不再允许覆盖地址。当前联调阶段终端用户凭据不进入 `mcp_installation_bindings`：客户端通过 `x-cpclaw-cloudpivot-username` 与 `x-cpclaw-cloudpivot-password` 请求头传入，后端仅保存在短生命周期 SSE 会话内存中，不持久化、不回显、不写入普通日志。认证载体抽象为终端凭据注入，后续切换 API Key 时只替换凭据适配器和请求头，不改变 SSE 地址、MCP 工具协议和审计模型。

## 2. 控制面与执行面

控制面 API：

- `GET /api/settings/mcp/cloudpivot`：返回安装标识、状态、客户端配置片段和能力矩阵，不返回秘密。
- `POST /api/settings/mcp/cloudpivot/enable`：校验 CPClaw 已配置云枢环境地址并发布 MCP，不接收终端云枢凭据。
- `POST /api/settings/mcp/cloudpivot/disable`：停止工具调用，可保留绑定以便恢复。

执行面由 `GET /api/mcp/cloudpivot/sse` 建立 SSE 会话，服务以 `endpoint` 事件返回会话消息地址；客户端随后向该地址发送 JSON-RPC，响应以 `message` 事件返回。JSON-RPC 至少实现 `initialize`、`tools/list` 和 `tools/call`。客户端配置从 `CPCLAW_MCP_PUBLIC_BASE_URL` 生成，只包含 `type`、`url`、`headers` 和 `disabled`，不包含安装目录、Node 命令或本地脚本路径。工具调用必须解析安装标识、检查 `ENABLED` 状态，并将调用写入脱敏审计。数据查询仅允许本地已同步的业务对象，并通过字段展示策略返回语义化摘要卡片；首期写工具只能返回确认门禁结果，不能由 MCP 模型自行确认。

## 3. 持久化

Flyway 新增 `mcp_installations` 和 MCP 调用审计表。安装实例只保存发布状态和显示名称；旧版 `mcp_installation_bindings` 迁移表已由后续迁移删除，避免留下终端地址或账号的错误持久化路径。历史审计不删除。

## 4. 已知限制

OAuth、市场发布和企业租户透传尚未有正式契约。SSE 地址对外发布时必须通过 HTTPS 与网关认证保护；安装标识不是访问令牌，不能作为唯一外网安全措施。取得宿主的正式身份契约后再增加用户/租户鉴权，不改变工具和绑定模型。

## 5. 语义任务网关与进度协议

`yunshu_handle_request` 是对外优先使用的自然语言工具。网关创建短生命周期任务标识并进入统一 `TaskGateway`；MCP 适配层可使用 `BoundCloudPivotConnection` 注入临时凭据，但不得调用依赖 CPClaw 本地个人凭据或会话副作用的旧 `AgentOrchestrator.handleMessage` 执行云枢业务。

`McpProgressListener` 将内部的安全过程事件转换为两份输出：

1. 当 `tools/call.params._meta.progressToken` 存在且通道为 SSE 时，通过既有 `message` 事件发送 JSON-RPC `notifications/progress`。`params` 包含令牌、0–100 进度和简短业务摘要。
2. 无论客户端是否支持实时通知，最终 `structuredContent` 都返回 `taskId`、`status`、`understandingSummary`、`matchedMetadata`、`executionTrace` 与结果/澄清/确认数据。

`executionTrace` 只允许“理解结论、元数据匹配范围、实际读取动作、计数与状态”等脱敏事实；不得包含提供商原始推理、提示词、临时账号密码、完整原始记录或内部技术栈。对外 `tools/list` 只返回高阶语义工具；原子工具既不发布也不接受外部调用。内部调用使用编码，但 MCP 返回的 `matchedMetadata`、数据卡片和流程列表不得暴露 `schemaCode`、`apiCode`、内部端点或模型推理。

## 6. 一期边界与后续演进

当前版本已持久化语义任务、脱敏事件、最终结果，并提供受保护部署范围内的状态、事件回放和取消接口；SSE 进度断开后可通过原 `taskId` 恢复。该生命周期接口仍不等同于完整的多租户授权：跨组织生产部署必须接入可信外部主体和网关认证，不能用可猜测安装标识代替身份。

外部 MCP 写操作保持 `confirmation_required`。未来只有在宿主提供可验证的用户身份和人工确认回调（或签名确认票）后，才能将确认计划转换为实际写入；客户端模型的二次 `tools/call` 不是可信人工确认。

## 7. 统一语义任务运行时

MCP 宿主适配层不能承载分析、查询、填单、流程和导入的业务分支。统一运行时负责规划、技能选择、受控执行、可见事件和结果体验：

```text
yunshu_handle_request
  -> SemanticTaskRuntime（意图、上下文、元数据、任务状态）
  -> InsightTaskExecutor | QueryTaskExecutor | FormTaskExecutor
     | WorkflowTaskExecutor | ImportTaskExecutor
  -> CloudPivot contract registry / runtime connector
  -> confirmation, audit, visible event and result
```

新增持久化聚合 `semantic_task_runs` 与 `semantic_task_events`。任务至少保存：任务标识、入口、安装实例、可信外部主体、会话标识、客户端幂等键、脱敏请求、意图与槽位、元数据匹配、可见计划/事件、结果引用、确认单引用、状态、时间戳和失败分类。账号密码、原始提示词、原始推理、完整业务记录不得写入这些表。

对外仍以一个首选业务入口为主，并保留生命周期读取能力作为宿主不渲染进度时的技术降级：

- `yunshu_handle_request`：自然语言目标、受限上下文、`clientRequestId`、可选 `conversationId` 与进度令牌；
- `yunshu_task_status`：查询持久化任务的脱敏进度、追问、确认状态和结果；
- `yunshu_task_cancel`：仅取消尚未提交的任务。

原子查询能力仅作为服务端内部执行组件，不作为模型工具发布。标准 `notifications/progress` 提供实时可见进度，`yunshu_task_status` 与最终 `visibleTrace` 作为客户端不渲染通知、断连或长任务的可靠降级方式。

## 8. 身份、确认与文件边界

安装标识不是用户或租户身份，短生命周期云枢账号密码也不能成为跨会话的持久化身份。查询可以由云枢账号权限保护；但用户级多轮记忆、确认单归属、审计主体和所有外部写入必须接入可信的 `externalPrincipal`（例如宿主 OIDC/JWT 声明或签名身份断言），并按 `tenantId + subjectId` 隔离。未取得该契约前，外部 MCP 仅能生成确认计划，实际确认应回到 CPClaw 已登录界面。

确认执行采用服务器签发的确认单：计划快照、影响摘要、计划哈希、过期时间、确认主体、一次性确认票和幂等键。服务端只接受可验证主体对尚未过期计划的确认；`confirmed=true` 或模型的第二次工具调用不是确认凭据。

Excel 不把二进制文件塞进自然语言参数。客户端先经受保护的附件通道上传，工具参数只引用 `attachmentId`/资源 URI；`ImportTaskExecutor` 读取后产生字段映射、行级校验、预览和可恢复导入作业。当前 `AttachmentService` 仅完成上传，因此在补齐解析、映射、导入作业和真实云枢导入契约前，不得宣称已支持导入。

## 9. 结果体验协议与实施顺序

统一结果使用版本化 `TaskExperienceEnvelope`：`task` 描述状态和重试性，`summary` 记录业务理解与风险，`visibleTrace` 记录脱敏过程，`output` 提供可直接转述的答复与产物，`interaction` 说明无交互、澄清或确认。终态固定为 `completed`、`needs_input`、`confirmation_required`、`failed`、`cancelled`；MCP 还应显式给出 `hostAction`，令宿主直接答复、仅追问、打开 CPClaw 确认页或报告失败，避免宿主再次猜测调用。

MCP 的 `content.text` 和 `structuredContent` 均携带业务化结果；`notifications/progress` 只是增强，发送失败、SSE 断开或未提供令牌不得改变已完成的业务结果。进度与事件必须做敏感字段和内部编码过滤。

## 10. 云枢契约与实施顺序

新增“云枢契约登记/验证”流程，将每一种新增、更新、表单提交、流程动作和批量导入 API 的真实路径、请求模板、响应形态、权限要求和回滚能力以已验证状态登记。禁止根据元数据名称臆造端点。

1. 建立任务表、事件表、外部主体和任务状态接口；实现只读查询、关联分析与可恢复多轮上下文。
2. 将表单、流程和数据操作接入统一的计划/确认/审计生命周期；先仅生成草稿和确认计划。
3. 按真实探查并验证的云枢契约逐项开放表单提交、流程处理、更新/删除。
4. 实现附件解析、映射预览、行级校验和导入作业，再接入已验证的云枢导入契约。
5. 对每个执行器覆盖：歧义追问、权限失败、幂等重试、确认超时、断连查询和秘密不落库测试。
