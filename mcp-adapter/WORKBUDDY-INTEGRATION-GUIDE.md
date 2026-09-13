# WorkBuddy 接入 Yunshu Skill 指导手册

> 适用对象：WorkBuddy 及其他支持 MCP 或 HTTP Tool Calling 的 AI 宿主。  
> 协议版本：`yunshu-skill/1.0`  
> 重要边界：WorkBuddy 直接调用 Yunshu Skill Gateway，不调用 CPClaw 对话 API、CPClaw 页面或 CPClaw 旧 MCP 服务。

## 1. 接入后的使用效果

用户在 WorkBuddy 中直接描述业务目标，例如：

```text
分析一下本月线索转商机情况
```

WorkBuddy 调用 `yunshu_task` 后，Yunshu Skill 会自动完成：

- 定位云枢中的业务对象、表单、字段和关联关系；
- 按元数据获取主对象及关联数据；
- 统一统计口径，生成指标、趋势、异常和数据范围；
- 信息不足时只追问必要内容，并保留已获得的信息；
- 填单时生成草稿、逐字段校验，并在当前 WorkBuddy 内请求确认；
- 确认后调用云枢真实接口，回查结果并返回记录引用。

WorkBuddy 负责对话呈现和最终语言组织；Yunshu Skill 负责云枢对象识别、数据获取、业务计算、权限、校验、写入和审计。

## 2. 选择接入通道

| WorkBuddy 能力 | 接入方式 | 建议 |
|---|---|---|
| 支持远程 MCP | Remote MCP/Streamable HTTP | 生产首选 |
| 支持 HTTP Action/Function | Canonical HTTP | 最容易跨平台联调 |
| 只能启动本地 MCP 命令 | stdio MCP Adapter | 需使用标准 MCP 实现 |
| 只能执行脚本 | CLI/JSON-line | 开发、自动化和测试使用 |

四种通道必须映射到同一个 `yunshu_task` 和同一个任务运行时，不能在 WorkBuddy 侧实现云枢字段、分页、关联或统计逻辑。

## 3. 前置条件

实施前确认以下信息已由 Yunshu Skill Gateway 管理员提供：

1. Gateway 地址，例如 `https://yunshu-skill.example.com`；
2. WorkBuddy 已注册的 `hostId`；
3. WorkBuddy 用户的可信身份方式，优先使用 OIDC/OAuth2/JWT；
4. 该用户对应的云枢组织、数据范围和 Skill 权限；
5. 当前开放能力（只读、分析、草稿、写入、流程处理）；
6. 云枢 Provider 的真实 Schema、校验、创建/更新/提交/回查契约已通过验收。

没有可信身份或 Provider 写入契约时，只能使用查询、分析、草稿和确认计划，不能宣称自动提交已可用。

## 4. Remote MCP 接入（推荐）

### 4.1 在 WorkBuddy 中新增 MCP 服务

在 WorkBuddy 的 MCP/工具设置中新增远程服务。不同版本的字段名称可能略有不同，核心配置如下：

```json
{
  "name": "YunshuSkill",
  "transport": "streamable-http",
  "url": "https://yunshu-skill.example.com/mcp",
  "authentication": "OAuth2/OIDC"
}
```

如果 WorkBuddy 当前只支持 SSE，则由 Gateway 提供兼容 SSE 端点；仍应指向独立 Yunshu Skill Gateway，不能指向 `/api/mcp/cloudpivot`。

### 4.2 工具检查

连接成功后，在 WorkBuddy 工具列表确认只使用高阶业务工具：

```text
yunshu_task
```

工具描述应允许传入自然语言目标和任务续接信息，不应要求用户或模型填写 `schemaCode`、`apiCode`、字段编码、云枢账号或密码。

### 4.3 首次验证

依次发送：

```text
有哪些可分析的业务数据？
```

```text
分析一下本月线索转商机情况，给出图表、趋势、异常、数据范围和分析依据。
```

验证 WorkBuddy 是否展示了结构化结果或 Markdown 降级结果，并确认没有要求用户跳转 CPClaw。

## 5. HTTP Tool 接入

如果 WorkBuddy 不支持 MCP，但支持 HTTP Action/Function，则配置一个业务工具调用：

```text
POST https://yunshu-skill.example.com/api/yunshu-skill/tasks/command
Content-Type: application/json
Authorization: Bearer <由宿主安全存储注入的令牌>
X-Yunshu-Host-Id: <已注册的 hostId>
```

首次请求：

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "operation": "start",
  "goal": "分析一下本月线索转商机情况",
  "clientRequestId": "workbuddy-unique-request-id"
}
```

后续请求必须复用服务端返回的 `task.id`，并携带：

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "operation": "input",
  "taskId": "原任务ID",
  "input": {"questionId": "用户回答"},
  "expectedStateVersion": 3,
  "continuationToken": "服务端返回的续接票据",
  "clientRequestId": "新的用户动作ID"
}
```

## 6. 对话与任务状态处理

WorkBuddy 应按服务端状态处理，不要根据文本自行猜测下一步：

| 服务端状态 | WorkBuddy 应做什么 |
|---|---|
| `needs_input` | 展示问题，收集用户补充后继续原 `taskId` |
| `draft_ready` | 展示草稿和字段来源，允许受控修改 |
| `validation_error` | 展示字段级错误，不静默改值 |
| `confirmation_required` | 在 WorkBuddy 内展示对象、字段差异、影响范围、风险和确认操作 |
| `submitted` | 展示真实云枢回执和记录引用 |
| `unknown` | 提示正在对账，禁止重复提交 |
| `failed` | 只有 `retryable=true` 时才允许重试 |
| `manual_review` | 结束自动流程，转人工处理 |

同一任务从追问、草稿、确认到断线恢复都必须保持同一个 `taskId`。版本冲突时先读取最新任务，再重新应用用户动作。

## 7. 智能填单验证

使用真实云枢元数据中的表单和字段进行验证，不要在 WorkBuddy 中硬编码字段：

```text
新增一条线索
```

通过标准：

1. 自动识别正确的云枢表单；
2. 只追问当前缺失且影响提交的字段；
3. 已填写字段在多轮对话中保留；
4. 非法值返回真实字段校验错误；
5. 确认前不调用写入接口；
6. 确认后调用真实创建接口并回查记录；
7. 网络超时进入 `unknown`，不会重复创建记录。

## 8. 安全要求

- 不把云枢账号、密码、Token 写入 Prompt、任务、日志或 Markdown Skill；
- `tenantId`、`subjectId` 和权限必须由可信身份断言生成，不能由用户输入或普通请求头决定；
- 确认必须由 WorkBuddy 当前宿主产生一次性确认票；模型说“确认”不等于可信确认；
- 写入前必须重验主体、权限、元数据版本、草稿版本和计划哈希；
- 云枢超时不得直接重试，必须先通过回查和对账确定远端结果。

## 9. 常见问题

### 连接返回 401

通常表示 `hostId` 未注册、令牌无效，或 Gateway 没有对应的身份验证器。不能改用 CPClaw 默认用户绕过认证。

### 工具列表出现 `cpclaw_cloudpivot_agent` 或 `yunshu_handle_request`

这是旧迁移链路。新的 WorkBuddy 接入应使用 `yunshu_task`；旧工具只能作为兼容别名，不能成为新的部署入口。

### 填单只能生成草稿，无法提交

通常是 Provider 的真实创建/提交/回查契约尚未登记或未通过发布门禁。此时返回确认计划是正确的安全行为，不代表提交失败。

### 断线后重新创建了任务

WorkBuddy 没有保存或复用原 `taskId`、`continuationToken` 或 `clientRequestId`。应先调用任务查询/事件接口恢复，不得重新发起同一个目标。

## 10. 接入验收清单

- [ ] WorkBuddy 直接连接 Yunshu Skill Gateway；
- [ ] 工具名称为 `yunshu_task`；
- [ ] 不调用 CPClaw 对话 API、UI 或旧 MCP 地址；
- [ ] 可信身份、租户和数据范围验证通过；
- [ ] 分析任务能自动获取关联数据并输出图文结果；
- [ ] 不完整输入能追问，完整输入能继续执行；
- [ ] 填单任务能完成草稿、校验、宿主内确认和真实回查；
- [ ] 重试不重复写入；
- [ ] 断线可按原 `taskId` 恢复；
- [ ] `unknown` 能通过对账收敛；
- [ ] 日志和响应不泄露凭据、Token 或内部编码。

## 11. 当前 CPClaw 代码边界

当前仓库已具备 Canonical HTTP 入口和协议设计，但 Remote MCP 独立服务、外部宿主 OIDC/JWT、真实云枢写入回查和跨实例恢复仍需完成生产验证。`mcp-adapter/cpclaw-yunshu.mjs` 当前是 JSON-line 转发器，不应直接当作完整标准 MCP Server 使用。

权威设计依据：

- `docs/technical-design/details/22-yunshu-skill-executable-architecture.md`
- `docs/technical-design/details/23-security-provider-release-gates.md`
- `docs/technical-design/details/24-yunshu-skill-implementation-contract.md`
