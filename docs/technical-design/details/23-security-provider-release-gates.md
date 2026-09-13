# Yunshu Skill 安全、Provider 与发布门禁补充

> 版本：v1.1｜状态：生产交付门禁，作为 `22-yunshu-skill-executable-architecture.md` 与 `24-yunshu-skill-implementation-contract.md` 的强制约束。本文不表示当前代码已经满足这些要求。

## 1. 评审结论

总体架构方向可行。设计契约满足第 24 号 Definition of Ready 后可标记为设计层 `implementation-ready`；但在安全身份、真实云枢写入契约、跨宿主确认和故障收敛完成前，不得标记为 `release-ready`，也不得在生产环境开放自动写入。

当前代码中的 `default-only` 主体、可由请求头传入的 `externalPrincipal`、仅支持删除的确认执行器，均只能作为开发演示路径；不能作为生产多租户和真实用户授权实现。

## 2. 可信身份与租户隔离

### 2.1 外部主体契约

Skill Gateway 必须校验宿主注册信息和用户身份断言。OIDC/JWT 验证至少固定：

- `issuer`、`audience`、允许的签名算法和 JWKS 来源；
- `exp`、`nbf`、`iat`、时钟偏差及最大 token 年龄；
- `jti`/nonce 重放防护、JWKS 轮换和缓存失效策略；
- `tenantId`、`subjectId`、`scopes` 的声明来源及映射规则；
- `hostId` 与 client 注册绑定，禁止请求参数覆盖已验证主体。

无可信断言时，只允许只读查询、草稿和确认计划，不得执行云枢写入；不得跳转 CPClaw 页面绕过缺口。

### 2.2 强制隔离

`tenantId + subjectId + hostId` 必须进入 Task、FormTask、Draft、Confirmation、Audit、Idempotency 和 Provider 执行上下文。数据库查询、唯一键和索引必须带租户/主体边界；跨租户、跨主体或跨宿主读取/确认/续接必须返回同一类不可探测错误。

云枢组织、账号和数据范围映射必须显式登记。`installationId` 只能标识安装实例，不得作为身份或权限凭证。

## 3. 权限重验与确认票

权限至少在 `DISCOVER → PLAN → CONFIRM → EXECUTE` 四个时点校验。执行前必须重验：字段级读写权限、数据范围、云枢组织、Skill/模板版本、元数据版本和草稿版本；发生权限变化、组织切换或 Schema 漂移时，原计划和确认票自动失效。

确认票应由服务端签名并持久化一次性消费状态，载荷至少包括：

```text
ticketId/jti, kid, alg, aud, taskId, tenantId, subjectId, hostId,
skillVersion, formRef, metadataVersion, draftVersion,
planHash, permissionSnapshot, idempotencyKey, expiresAt
```

确认接口必须同时验证签名、主体、宿主、计划哈希、版本、有效期和一次性 `jti`；数据库应使用原子状态转换防止并发双消费。`confirmed=true` 或模型的第二次工具调用不构成人工确认。

## 4. 云枢 Provider 真实契约

每项写能力在开放前必须登记并由沙箱录制回放和真实 E2E 双重验证：

```text
loadFormSchema
validateDraft
createRecord
updateRecord
submitAction
getOperationResult
```

登记内容必须包含请求/响应 Schema、字段类型和枚举、关联与联动、默认值、必填规则、错误码、权限要求、超时语义、远端幂等键、回查接口、回滚/补偿和版本兼容策略。只读元数据不能推导出写入 API，未验证能力只能生成草稿或确认计划。

## 5. 幂等、未知态与对账

写操作幂等键至少由 `tenantId + subjectId + taskId + operation + draftVersion` 派生，并由 Provider 透传到云枢。超时、断线或响应解析失败不得直接重试写入，必须进入 `UNKNOWN`，通过 `getOperationResult`、outbox/对账任务和人工复核收敛：

```text
SUBMITTING → UNKNOWN → SUBMITTED | FAILED | MANUAL_REVIEW
```

仅在确认远端未执行时允许重试；重复请求必须返回同一操作结果，不能产生第二条记录。对账、重试和补偿事件需可审计并具备告警 SLA。

## 6. Markdown Skill 治理

Markdown Skill 发布包必须经过来源白名单、内容哈希/签名、Schema 解析、管理员审核、Executor 白名单绑定和版本兼容检查。解析和执行需限制大小、深度、耗时、网络访问与资源消耗，禁止任意脚本、外链 URL、凭据、SQL、SSRF、模板注入和提示注入改变工具策略。

撤回、停用、灰度、回滚和版本替换必须立即影响执行面，并保留审批人与审计记录；未审核或已撤回版本不可执行。

## 7. 发布门禁

| Gate | 必须证明 | No-Go 条件 |
| --- | --- | --- |
| G0 威胁模型 | 数据分类、信任边界、滥用场景与应对 | 存在主体伪造、越权或凭据落库路径 |
| G1 协议 | Task Envelope、确认、续接、错误码契约测试 | 宿主需调用 CPClaw UI/API 或自行猜测状态 |
| G2 身份权限 | OIDC/JWT、租户隔离、四点重验 | 默认主体或可控 header 可执行生产写入 |
| G3 Provider | 表单 Schema、校验、写入、回查真实 E2E | 仅有元数据/删除，缺真实 create/update/submit |
| G4 可靠性 | 幂等、UNKNOWN、对账、断线恢复演练 | 超时后可能重复写入或无法判定结果 |
| G5 Skill 治理 | Markdown 签名、审核、撤回、资源限制 | 未审核内容可执行或可改变工具策略 |
| G6 跨宿主验收 | CPClaw Web 与模拟 WorkBuddy 事实/状态一致 | 外部宿主出现 CPClaw 专属动作或结果不一致 |

每个 Gate 必须输出环境、用例、日志/回执和缺陷清单；任一 P0/P1 缺陷或证据缺失均回到方案阶段。通过全部 Gate 后，方可进入分阶段开发和有限灰度。

## 8. 当前实现标注

以下事实必须在发布说明中保持透明：`PrincipalContextService` 当前为默认主体模式；新的 `ConfirmationTicketService` 已具备签名、绑定和持久化一次性消费契约，但尚未接入可信 OIDC 主体、持久化 FormTask Runtime 或真实 Provider 执行链；`AuditService.confirm` 仍未按可信主体、租户和宿主校验确认票；`ConfirmedOperationExecutor` 当前仅实现删除；SSE 会话状态不是跨实例事实源。它们不影响方案方向可行性，但构成生产写入的阻断项。
