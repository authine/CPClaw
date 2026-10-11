# Yunshu Skill 实施契约与交付判定

> 版本：v1.0｜状态：开发准入补充｜本文件把 22 号架构基线转化为可拆分、可测试、可验收的实施契约。它不替代 23 号生产发布门禁。

## 1. 两级判定

| 判定 | 含义 | 必须满足 |
| --- | --- | --- |
| `implementation-ready` | 方案足够明确，可以开始分阶段开发 | 本文件第 2–8 节契约冻结；Mock Provider 和协议测试可执行 |
| `release-ready` | 可以进入指定环境/生产交付 | 23 号 G0–G6 全部有真实证据，且无 P0/P1 缺陷 |

设计文档不能代替真实云枢、身份、网络和宿主验证。未达到 `release-ready` 时，功能必须按能力开关限制为只读、草稿或预览。

## 2. Canonical Protocol

所有 MCP、HTTP、CLI 入口都必须映射到以下同一命令模型：

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "operation": "start|input|action|get|events",
  "taskId": "opaque-task-id",
  "goal": "用户目标，仅 start 使用",
  "input": {"questionId": "answer or field patch"},
  "action": {"type": "confirm|update_draft|cancel", "confirmationTicket": "..."},
  "clientRequestId": "stable-id",
  "expectedStateVersion": 3,
  "continuationToken": "signed-token"
}
```

约束：

- `start` 不带 `taskId`；其他操作必须带原 `taskId`。
- `input`、`action` 必须带 `expectedStateVersion`；冲突返回 `409 STATE_VERSION_CONFLICT` 和最新 Envelope。
- `clientRequestId` 在同一重试窗口复用；新用户动作生成新值。
- 旧工具名只能映射到同一命令模型，不得保留旧状态或旧确认动作。

## 3. Envelope 必填字段

服务端响应必须满足以下最小结构，字段缺失视为协议错误：

```json
{
  "protocolVersion": "yunshu-skill/1.0",
  "task": {"id":"...","kind":"form","status":"needs_input","stateVersion":3,"retryable":false},
  "interaction": {"type":"ask_user","questions":[]},
  "draft": {"formRef":{},"metadataVersion":"v1","draftVersion":1,"fields":[]},
  "validation": {"issues":[]},
  "next": {"requiredInputSchema":{},"continuationToken":"...","expiresAt":"..."},
  "completion": {"state":"incomplete","missing":[]},
  "output": {},
  "evidence": {},
  "hostAction": {"type":"ask_user"},
  "auditRef": {"taskId":"..."}
}
```

禁止字段/值：CPClaw URL、CPClaw UI 动作、内部 API/Schema 编码、凭据、Token、原始思维链。

## 4. 状态转移与错误码

### 4.1 外部状态

```text
accepted → needs_input → draft_ready → validation_error → ready_to_submit
ready_to_submit → confirmation_required → submitting
submitting → submitted | failed | unknown
unknown → submitted | failed | manual_review
accepted/needs_input/draft_ready/validation_error/ready_to_submit/confirmation_required → cancelled
```

### 4.2 错误分类

| 错误码 | 语义 | 宿主动作 |
| --- | --- | --- |
| `NEEDS_INPUT` | 缺少用户信息 | 只追问 |
| `AMBIGUOUS_TARGET` | 对象/表单不唯一 | 展示候选并追问 |
| `VALIDATION_ERROR` | 字段值不合法 | 展示字段级错误 |
| `STATE_VERSION_CONFLICT` | 草稿或任务版本过期 | 读取最新 Envelope 后重放用户动作 |
| `PERMISSION_DENIED` | 无权限 | 结束并说明，不重试 |
| `CONTRACT_UNVERIFIED` | Provider 契约未验证 | 仅草稿/预览 |
| `CONFIRMATION_INVALID` | 确认票无效、过期或重放 | 重新生成预览 |
| `REMOTE_UNKNOWN` | 云枢结果未知 | 进入对账，不自动重试 |
| `RETRYABLE_FAILURE` | 可安全重试的技术失败 | 仅按 `retryable=true` 重试 |

确认票是服务端签发且持久化的一次性能力票，必须同时绑定 `taskId`、`tenantId`、`subjectId`、`hostId`、`skillVersion`、`formRef`、`metadataVersion`、`stateVersion`、`draftVersion`、`planHash`、`idempotencyKey` 与 `expiresAt`。确认执行时必须重验以上绑定及可信宿主上下文，并以原子状态转换消费票据；任何版本、计划哈希、身份或宿主变化都返回 `CONFIRMATION_INVALID`。Provider 写入契约未验证时只能展示确认计划，不得签发执行票据或调用写入 Provider，必须返回 `CONTRACT_UNVERIFIED`。

## 5. 实施工作包与完成条件

| 工作包 | 交付物 | 完成条件 |
| --- | --- | --- |
| WP1 Gateway | 独立启动配置、健康检查、认证适配器 | 不依赖 CPClaw UI；可单独启动和调用 |
| WP2 Protocol | JSON Schema、MCP/HTTP/CLI 适配器 | 同一输入产生一致 Envelope |
| WP3 Task/FormTask | 任务、草稿修订、事件、CAS、幂等表 | 重启、并发冲突、重复请求测试通过 |
| WP4 Runtime | 路由、表单发现、槽位收集、校验状态机 | Mock Provider E2E 通过 |
| WP5 Provider | 元数据、表单 Schema、读写契约适配器 | 契约登记和录制回放通过 |
| WP6 Identity | OIDC/JWT、租户/主体/宿主绑定 | 越权、重放、票据失效测试通过 |
| WP7 Host Adapters | CPClaw Web、WorkBuddy 模拟宿主 | 事实和状态逐状态一致 |
| WP8 Operations | outbox、worker、回查、对账、监控 | UNKNOWN 可收敛；多实例通过数据库租约领取，租约过期可恢复，告警和审计可查 |

## 6. Mock Provider 必测场景

Mock Provider 必须可注入：成功、缺字段、枚举错误、关联不存在、权限变化、Schema 漂移、超时、远端已成功但本地超时、重复幂等键和重复确认票。每个场景都要验证最终状态、下游调用次数、Envelope 和审计事件。

## 7. Definition of Ready（进入开发）

同时满足以下条件即可进入分阶段开发：

1. 22、23、24 三份文档评审通过且无互相冲突的 Canonical Protocol、状态和确认术语；
2. JSON Schema、错误码和状态转移测试已具备；
3. Mock Provider 能完成“追问→草稿→校验→确认→提交/未知态→恢复”闭环；
4. 工作包、数据责任、接口责任、验收用例和回滚边界已分配；
5. 明确当前代码限制，不把默认主体、旧确认动作和同步 SSE 当作生产能力。

## 8. Definition of Done（进入交付）

只有在 23 号 G0–G6 全部通过、真实云枢写入和 WorkBuddy 跨网络 E2E 有证据、无 P0/P1 缺陷，并完成灰度/回滚演练后，才可标记 `release-ready`。否则只能交付只读、草稿或预览能力。
