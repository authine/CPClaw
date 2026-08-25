# 云枢 MCP（CPClaw 智能任务入口）

CPClaw 默认通过 SSE 发布云枢 MCP 服务，不需要在终端安装 Node、下载适配器或填写 CPClaw 安装目录。云枢访问地址由 CPClaw 服务端统一配置；终端用户的账号和密码只配置在 MCP 客户端，不写入 CPClaw 数据库。

当前未接入登录模块时，CPClaw 使用默认系统用户：`huangj`（黄杰，18124691161）。该身份只用于用户级任务、记忆和审计归属；云枢密码仍必须通过已配置的云枢凭据提供，不能由默认身份推导。

MCP 客户端配置示例：

```json
{
  "mcpServers": {
    "CloudPivotMCP": {
      "type": "sse",
      "url": "https://cpclaw.example.com/api/mcp/cloudpivot/sse",
      "headers": {
        "x-cpclaw-installation-id": "CloudPivotMCP",
        "x-cpclaw-cloudpivot-username": "终端用户自己的云枢账号",
        "x-cpclaw-cloudpivot-password": "通过客户端安全配置填写"
      }
    }
  }
}
```

管理员只需在 CPClaw 的“系统设置 → 集成与发布 → 云枢 MCP 服务”发布服务，并通过 `CPCLAW_MCP_PUBLIC_BASE_URL` 配置对外可访问的 HTTPS 地址。终端用户在 OpenClaw 兼容 MCP 客户端配置自己的账号和密码；无需重复配置云枢访问地址。使用远程 CPClaw 时必须配置 HTTPS，避免在非本机网络中传输密码。

## 宿主 Skill 调用约束

将本 MCP 作为“CPClaw 云枢领域智能体”接入宿主，而不是一组云枢原子接口。宿主调用高阶工具 `cpclaw_cloudpivot_agent`（旧客户端可继续调用 `yunshu_handle_request`），并传入用户目标和可选交付要求：

```json
{
  "request": "分析我可见范围内的在建项目情况，并说明风险",
  "taskSpec": {
    "goal": "分析在建项目并说明风险",
    "deliverables": [
      {"id": "summary", "required": true, "description": "项目整体情况"},
      {"id": "risk_basis", "required": true, "description": "风险依据"}
    ]
  },
  "context": ["上一轮只要求看今年数据"],
  "conversationId": "宿主稳定会话标识",
  "turnId": "宿主当前轮次标识",
  "clientRequestId": "本轮稳定幂等标识",
  "presentation": {"prefer": "summary_table_chart"}
}
```

CPClaw 在服务端完成意图理解、元数据验证、Skill 选择、关联分析、确认和审计。返回的 `content.text` 已是可直接展示的完整 Markdown；支持结构化结果的宿主还可读取 `structuredContent.output.artifact` 中的 KPI、图表、结论、口径警告和追问。不得让宿主猜测或请求 `schemaCode`、`apiCode`、字段编码或云枢访问地址。

OpenClaw 以 CPClaw 返回的 `completion` 和 `evidence` 为云枢事实边界：可以自然组织最终回答，但不得修改数字、口径、范围、风险信号或警告。`partial` 是终态，不因表达不够漂亮而自动重试；只有 `needs_input`、可信确认或用户新增约束且带有效 continuation 才能继续。同一 `turnId` 的重复调用由 CPClaw 服务端 replay 原结果，不重新访问云枢。任何写入、删除、流程处理或导入在确认前均不会执行。

仅配置 MCP 服务地址不足以让宿主稳定遵循结果呈现契约。请同时加载同目录的 `CPClaw-Yunshu-Skill.md`；它定义了唯一工具、上下文传递及四类 `hostAction` 的处理规则。
