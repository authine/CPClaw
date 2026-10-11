# Yunshu Skill Gateway 接入包

本目录用于让 WorkBuddy、OpenClaw 和其他 AI 宿主**直接**调用 Yunshu Skill Gateway。CPClaw Web 是同一 Skill 的一个宿主，不是外部 AI 的对话、MCP 或确认中转层。

## 接入边界

- 入口协议：`yunshu-skill/1.0`
- 高阶工具：`yunshu_task`
- HTTP 命令入口：`POST /api/yunshu-skill/tasks/command`
- 宿主身份：Gateway 的已注册验证器从可信断言生成 `tenantId + subjectId + hostId`
- 确认：在当前宿主内完成，回传一次性确认票；不跳转 CPClaw

不要配置 `/api/mcp/cloudpivot/sse`、`cpclaw_cloudpivot_agent`、`yunshu_handle_request`、安装实例 ID 或云枢账号密码作为新接入路径。这些仅属于已废止的 CPClaw MCP 迁移链路。

## 命令示例

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "operation": "start",
  "goal": "新增一条线索",
  "clientRequestId": "host-stable-request-id"
}
```

后续 `input`、`action`、`get`、`events` 必须携带原 `taskId`、`expectedStateVersion` 和需要时的 continuation token。完整字段和状态处理见 [CPClaw-Yunshu-Skill.md](CPClaw-Yunshu-Skill.md)。

## 标准 MCP stdio 适配器

`cpclaw-yunshu.mjs` 是一个最小标准 MCP stdio Server：实现 `initialize`、`notifications/initialized`、`ping`、`tools/list` 和 `tools/call`，只暴露 `yunshu_task` 一个业务工具。工具调用会转换为 `yunshu-skill/1.0` Canonical Command，再转发至独立 Yunshu Skill Gateway；它不存储或转发云枢账号密码，不连接 CPClaw 对话或旧 MCP。

配置：

```text
YUNSHU_SKILL_GATEWAY_URL=https://yunshu-skill.example.com
YUNSHU_HOST_ID=由已注册宿主使用的 hostId
# 内置 CPClaw HMAC Gateway 模式：
YUNSHU_TENANT_ID=企业租户标识
YUNSHU_PRINCIPAL=当前宿主用户标识
YUNSHU_SHARED_SECRET=部署方安全注入的共享密钥
# 外部 OIDC/JWT Gateway 模式可改用：
YUNSHU_AUTHORIZATION=宿主安全存储提供的可信身份断言
```

CPClaw 内置 Gateway 的 OIDC introspection 模式由服务端配置控制，不把 client secret 放到 WorkBuddy 工具参数中：设置 `CPCLAW_YUNSHU_IDENTITY_MODE=oidc`，并注入 `CPCLAW_YUNSHU_OIDC_INTROSPECTION_URL`、`CPCLAW_YUNSHU_OIDC_CLIENT_ID`、`CPCLAW_YUNSHU_OIDC_CLIENT_SECRET`。服务端只信任 introspection 返回的 `active=true`、`sub` 和租户 Claim（`tenant_id`/`tenantId`/`org_id`），不接受请求头自报的主体或租户。HMAC 与 OIDC 适配模式互斥。

外部宿主必须有相应的 Gateway 身份验证器；未注册的宿主会被拒绝，而不是回退使用 CPClaw 默认主体。

当配置 `YUNSHU_SHARED_SECRET` 时，适配器按每个请求生成 HMAC-SHA256 签名，签名原文为：
`hostId + "\\n" + tenantId + "\\n" + principal + "\\n" + Unix 时间戳`。
共享密钥只从进程环境读取，不写入文件、任务参数或 MCP 输出。

适配器会校验 `start` 必须有 `goal`，非 `start` 操作必须有原 `taskId`，并将 Gateway 的结构化响应作为 MCP `structuredContent` 返回。网关不可用时返回 `isError=true` 和可重试错误；不会自动重试写入操作。

## 依据

- WorkBuddy 实施手册：[WORKBUDDY-INTEGRATION-GUIDE.md](WORKBUDDY-INTEGRATION-GUIDE.md)

- `docs/technical-design/details/22-yunshu-skill-executable-architecture.md`
- `docs/technical-design/details/23-security-provider-release-gates.md`
- `docs/technical-design/details/24-yunshu-skill-implementation-contract.md`
