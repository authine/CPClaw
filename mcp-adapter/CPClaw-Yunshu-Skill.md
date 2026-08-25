# CPClaw 云枢智能助手 Skill

## 能力定位

这是一个面向 OpenClaw 类宿主的云枢领域委派 Skill。OpenClaw 负责用户整体目标、跨领域思考和最终表达；CPClaw 负责云枢元数据验证、数据准备、关联查询、证据覆盖、风险信号、确认和审计。

宿主不要自行解释云枢元数据，不要拼接云枢接口，也不要要求用户提供 `schemaCode`、`apiCode` 或字段编码。

## 调用规则

仅调用 MCP 工具 `yunshu_handle_request`（兼容名称；新客户端可使用 `cpclaw_cloudpivot_agent`）：

```json
{
  "request": "用户的原始自然语言目标",
  "taskSpec": {
    "goal": "规范化目标",
    "deliverables": [{"id": "required_output", "required": true, "description": "必须满足的交付项"}],
    "constraints": {"period": "用户明确的时间或范围约束"}
  },
  "context": ["同一会话中已经确认的业务上下文"],
  "conversationId": "宿主稳定会话标识",
  "turnId": "当前轮次稳定标识",
  "clientRequestId": "本轮稳定幂等标识",
  "presentation": {"prefer": "summary_table_chart"}
}
```

`context` 只传递与当前任务直接相关的摘要，不传递密码、Token、原始思维链或内部编码。

## 结果处理

- `completion.state=complete`：基于 `evidence` 完成最终表达；可以自然组织语言，但不得修改事实、数字、口径、范围和警告。
- `completion.state=partial`：一次性说明已完成项和证据缺口；不得因为表达不够漂亮而自动重试。
- `completion.state=needs_input`：只向用户提出 CPClaw 返回的问题，用户补充后使用 `continuation` 继续同一任务。
- `completion.state=confirmation_required`：展示影响范围并引导可信确认；确认前不得执行写操作。
- `completion.state=failed`：展示脱敏失败原因，仅在 `retryable=true` 时重试。

优先使用 `structuredContent.evidence`、`completion` 和 `output.artifact` 组织回答；`output.answerDraft` 是安全草稿。如果宿主不支持结构化内容，使用 `content.text`，它仍必须是完整 Markdown 降级结果。

本 Skill 必须被安装/加载到宿主会话中。仅配置 MCP 连接只能发现工具，不能保证宿主遵循上述终态呈现契约。
