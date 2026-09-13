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

## stdio 转发器

`cpclaw-yunshu.mjs` 仅把一行一个 Canonical Command 转发至独立 Yunshu Skill Gateway；它不存储或转发云枢账号密码，不连接 CPClaw MCP。配置：

```text
YUNSHU_SKILL_GATEWAY_URL=https://yunshu-skill.example.com
YUNSHU_HOST_ID=由已注册宿主使用的 hostId
YUNSHU_AUTHORIZATION=宿主安全存储提供的可信身份断言（如需要）
```

外部宿主必须有相应的 Gateway 身份验证器；未注册的宿主会被拒绝，而不是回退使用 CPClaw 默认主体。

## 依据

- WorkBuddy 实施手册：[WORKBUDDY-INTEGRATION-GUIDE.md](WORKBUDDY-INTEGRATION-GUIDE.md)

- `docs/technical-design/details/22-yunshu-skill-executable-architecture.md`
- `docs/technical-design/details/23-security-provider-release-gates.md`
- `docs/technical-design/details/24-yunshu-skill-implementation-contract.md`
