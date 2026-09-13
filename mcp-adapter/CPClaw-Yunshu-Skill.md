# Yunshu Skill：外部 AI 宿主接入规范

> 版本：`yunshu-skill/1.0`。WorkBuddy、OpenClaw 等外部 AI 宿主直接接入 Yunshu Skill Gateway；CPClaw Web 只是同一 Skill 的一个宿主。

## 强制边界

1. 宿主直接连接 Yunshu Skill Gateway，不调用 CPClaw 对话 API、MCP 地址、UI 或确认页面。
2. 唯一业务工具为 `yunshu_task`；HTTP、MCP、CLI 只是在同一 Canonical Protocol 上的适配。
3. 宿主不猜测云枢对象、字段、表单、接口或内部编码，只提交用户目标和用户动作。
4. 宿主负责呈现问题、草稿、校验和确认；Skill Gateway 负责对象定位、权限、校验、执行和审计。
5. 不显示或传递凭据、Token、内部 API/Schema 编码、原始载荷或模型思维链。

## 宿主能力与身份

连接初始化应声明 `HostCapabilityProfile`：`structuredForm`、`fieldPatch`、`actionButton`、`confirmationCallback`、`streamingEvents`、`taskResume`。

生产宿主必须提供已验证身份断言；Gateway 验证器产生 `tenantId + subjectId + hostId`。宿主不得通过请求头或请求体自行指定主体、租户或权限。无可信身份时，只允许只读、草稿和预览。

能力不足时依次降级为：结构化卡片 → 受约束 JSON → 可解析 Markdown；不得跳转 CPClaw，也不得让用户重新描述目标。

## Canonical Command

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "operation": "start|input|action|get|events",
  "taskId": "仅非 start 操作必填",
  "goal": "仅 start 操作必填的自然语言目标",
  "input": {"questionId": "用户回答或字段补丁"},
  "action": {"type": "confirm|update_draft|cancel", "confirmationTicket": "..."},
  "clientRequestId": "同一重试稳定复用",
  "expectedStateVersion": 3,
  "continuationToken": "..."
}
```

同一任务在澄清、草稿修改、确认、断线恢复和对账中保持相同 `taskId`。收到 `STATE_VERSION_CONFLICT` 时，读取最新 Envelope 后重新应用用户动作，不得覆盖新版本草稿。

## 返回处理

| 状态/动作 | 宿主行为 |
| --- | --- |
| `needs_input` / `ask_user` | 展示问题，用户补充后以原 taskId 发送 `input`。 |
| `draft_ready` / `edit_draft` | 显示草稿和来源，发送受控字段补丁。 |
| `validation_error` | 逐字段显示 `validation.issues`，不静默修正。 |
| `confirmation_required` / `request_confirmation` | 在**当前宿主**展示影响范围和风险，回传一次性确认票。 |
| `submitted` / `respond` | 展示真实回执和记录引用。 |
| `unknown` | 提示正在对账，禁止重复提交；按 taskId 查询。 |
| `failed` / `report_failure` | 仅在 `retryable=true` 时重试。 |
| `cancelled` / `manual_review` | 结束任务并说明原因或人工处置渠道。 |

严禁使用 `open_cpclaw_confirmation`、`cpclaw_authenticated_ui` 或任何跳转 CPClaw 的替代动作。

## 安全与幂等

- 模型第二次工具调用、`confirmed=true` 或自然语言“确认”都不是可信确认；仅当前宿主中人工动作产生的有效确认票可提交写操作。
- 同一 `clientRequestId` 重试返回同一任务结果，不能重复执行下游调用。
- 云枢超时或响应解析失败进入 `unknown` 对账，不能直接重试写入。
- 未验证 Provider 契约时，`CONTRACT_UNVERIFIED` 是安全阻断，不得表示已提交。

## 依据

- `docs/technical-design/details/22-yunshu-skill-executable-architecture.md`
- `docs/technical-design/details/23-security-provider-release-gates.md`
- `docs/technical-design/details/24-yunshu-skill-implementation-contract.md`
- `docs/test-cases/25-yunshu-skill-contract-acceptance.md`
