# Yunshu Skill 模拟验证报告（2026-09-01）

> 被测基线：`yunshu-skill/1.0`、`PersistentFormTaskRuntime`、CPClaw Web Host。  
> 结论口径：本报告只验证当前 Mock/协议实现，不把 Provider 默认安全阻断、未启动的后端或文档设计当作真实云枢能力通过。

## 1. 结论

**当前模拟实现未通过进入下一阶段的 Mock Gate。**

- 定向后端单元/HTTP 测试共 10 项通过，前端 `npm run build` 通过；这证明了部分协议骨架、CAS、范围隔离和“未验证时禁止写入”的安全下限。
- 但 SIM-01、SIM-03、SIM-09、SIM-10 未满足用例的完整要求；SIM-11、SIM-12 受 Provider/数据库运行环境阻塞，不能报通过。
- 发现 P0 文档/发布资产冲突：`mcp-adapter/CPClaw-Yunshu-Skill.md` 仍把 OpenClaw 指向 CPClaw MCP，并包含 `open_cpclaw_confirmation`。它与 22/24 号文档规定的“外部宿主直接调用独立 Yunshu Skill、在自身宿主内确认”相冲突。

因此状态为：

```text
Mock protocol foundation: partially verified
Mock E2E gate: NO-GO
Real CloudPivot write / production delivery: NO-GO
```

## 2. 环境与执行证据

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 后端定向测试 | 通过 | 在 `server/` 执行 Maven 3.9.11：`mvn.cmd test -Dtest=YunshuProtocolTests,YunshuSkillHttpControllerTests,PersistentFormTaskRuntimeTests,YunshuProviderSafetyTests`；结果 10 tests、0 failure、0 error、0 skipped。 |
| 前端类型检查和打包 | 通过 | 在 `web/` 执行 `npm run build`，`vue-tsc --noEmit` 与 Vite build 成功。仅有现存大 chunk 警告，不影响本轮结论。 |
| 禁用动作扫描（运行时代码） | 通过 | `server/src/main/java`、`server/src/test/java`、`web/src` 未命中 `open_cpclaw_confirmation` 或 `cpclaw_authenticated_ui`。 |
| 禁用动作扫描（可发布 Markdown Skill） | 不通过 | `mcp-adapter/CPClaw-Yunshu-Skill.md:83` 命中 `open_cpclaw_confirmation`，且该文件仍要求 OpenClaw 调用 `cpclaw_cloudpivot_agent` / CPClaw MCP。 |
| 后端运行态 E2E | 受环境阻塞 | `application.yml` 要求显式 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD`；本次环境未注入这些变量，服务无法连接持久化数据库。 |

## 3. SIM-01 至 SIM-12 判定

| 用例 | 判定 | 实际证据与差异 |
| --- | --- | --- |
| SIM-01 少信息填单 | **不通过** | `PersistentFormTaskRuntimeTests.startInputGetAndCancelUseStableTaskAndCasVersions` 证明可创建 `taskId` 并返回 `needs_input`。但 Runtime 固定创建单一 `form_details` 字段，未通过 Stub Schema 发现“名称、联系人”等最小必填字段，也没有表单候选解析；不满足用例定义。 |
| SIM-02 多轮补充 | **通过（Mock 范围）** | 同一测试证明输入后 `taskId` 稳定、`stateVersion` 递增；`FormDraftService.patchAndValidate` 对未出现在 patch 中的字段保留原值。当前仅覆盖通用字段，不代表真实表单多字段 E2E。 |
| SIM-03 字段校验 | **不通过** | `FormDraftValidator` 已有必填和枚举本地校验代码，但当前 Runtime 未从 Stub/Provider Schema 装载枚举、关联或多字段槽位，且现有定向测试没有非法枚举/关联的 `validation_error` 断言。不能以未被 Runtime 使用的校验器判定通过。 |
| SIM-04 乐观并发 | **通过（Mock 范围）** | `PersistentFormTaskRuntimeTests.staleInputReturnsLatestEnvelopeAndConfirmCannotWrite` 验证旧 `expectedStateVersion` 返回 `STATE_VERSION_CONFLICT`，并返回最新版本草稿。 |
| SIM-05 取消 | **通过（当前无写入安全边界）** | 定向测试验证同一任务进入 `cancelled`。当前任何 `confirm` 都被 `CONTRACT_UNVERIFIED` 阻断，故不会产生写入。尚未验证 Provider 接通后的“取消后不可确认”状态迁移。 |
| SIM-06 确认安全边界 | **通过（安全阻断）** | `PersistentFormTaskRuntimeTests` 和 `YunshuProviderSafetyTests` 均验证 `confirm/createRecord` 不会报告成功，而是 `CONTRACT_UNVERIFIED`；无伪造 `recordRef`。这不是提交能力通过。 |
| SIM-07 范围隔离 | **通过（Mock 范围）** | `scopeIsolationDoesNotRevealTask` 以不同 `subjectId` 读取任务，返回 `PERMISSION_DENIED`；`FormTaskRepository` 查询同时按 tenant/subject/host 过滤。 |
| SIM-08 宿主认证 | **通过（适配器单测）** | `YunshuSkillHttpControllerTests` 验证缺失或未注册 `workbuddy` 返回 HTTP 401 / `PERMISSION_DENIED`，请求头中的外部主体不能被 CPClaw 默认身份采信。生产 WorkBuddy OIDC/JWT Resolver 尚未实现。 |
| SIM-09 Web 宿主适配 | **不通过** | Web Client 静态确认发送 `X-Yunshu-Host-Id: cpclaw-web`，控制器单测证明 Canonical HTTP 入口不回退到 legacy CPClaw flow；但无数据库运行态 E2E。更严重的是可发布 `mcp-adapter/CPClaw-Yunshu-Skill.md` 仍指向 CPClaw MCP 并含废止确认动作，跨宿主发布边界不成立。 |
| SIM-10 通用对话隔离 | **不通过（证据缺失）** | 未找到 Provider 调用计数为 0、未创建 FormTask 的自动化用例；现有测试无法证明普通闲聊绝不进入 Yunshu Runtime。 |
| SIM-11 未知态规则 | **受实现阻塞** | `unknown` 状态和 `REMOTE_UNKNOWN` 枚举存在，但没有实现 Provider 写入、`getOperationResult` 回查、outbox/对账或自动重试抑制的可执行链路。当前仅以 `CONTRACT_UNVERIFIED` 阻断写入，符合安全下限，不能报告 UNKNOWN 闭环通过。 |
| SIM-12 前端降级展示 | **受环境阻塞** | `YunshuTaskCard.vue` 静态实现了 `needs_input`、`validation_error`、`confirmation_required`、`unknown` 的展示，且前端构建通过；但当前 Runtime 不会产生确认/未知态，后端又未启动，尚无浏览器 E2E 或截图证据。 |

## 4. 缺陷与阻塞项

| 等级 | 编号 | 事实 | 影响 | 处置建议 |
| --- | --- | --- | --- | --- |
| P0 | VAL-01 | `mcp-adapter/CPClaw-Yunshu-Skill.md` 仍以 CPClaw MCP 为外部入口，并在第 4 节声明 `open_cpclaw_confirmation`。 | 外部 AI 会被引导与 CPClaw 交互，直接违背独立 Yunshu Skill 和宿主内确认边界。 | 将该 Markdown Skill 标记为已废止或迁移为 `yunshu_task` / `yunshu-skill/1.0`；删除 CPClaw URL、MCP 入口和旧确认动作后再发布。 |
| P1 | VAL-02 | Runtime 使用固定 `form_details` 槽位，`formRef.resolution=unverified`。 | 不能根据云枢元数据发现表单，无法只追问真实阻塞字段。 | 先完成可注入 Stub Provider Schema，再实现表单解析、候选消歧和动态槽位。 |
| P1 | VAL-03 | Schema 枚举、关联校验、`ready_to_submit`、确认票、`confirmation_required` 未形成 Runtime 状态机。 | 智能填单不能从追问进入可确认计划。 | 补齐 Mock Provider 成功/校验错误路径和逐状态测试；确认票必须绑定主体、宿主、版本和计划哈希。 |
| P1 | VAL-04 | 无普通闲聊隔离的 Provider 调用计数测试。 | 不能证明通用对话不会误触云枢。 | 新增通用对话路由的负向测试，断言零 Provider 调用、零 FormTask。 |
| P1 | VAL-05 | 无 UNKNOWN 回查、对账、outbox/worker 和幂等写入测试。 | Provider 接通后可能有重复写入或无法恢复未知态的风险。 | 在 Mock Provider 中注入“远端成功、本地超时”等场景，完成回查与重复提交计数验证。 |
| 环境 | VAL-06 | 未配置数据库连接，后端无法启动。 | 无法做真实 HTTP + Flyway + FormTask 持久化 + Web 浏览器 E2E。 | 提供隔离测试数据库或明确的本地数据库环境后再执行端到端用例。 |

## 5. 开发准入复核

22/24 号文档要求 Mock Provider 走通“追问 → 草稿 → 校验 → 确认 → 提交/未知态 → 恢复”闭环，且外部宿主直接调用 Yunshu Skill。当前结果只覆盖了其中的追问、通用草稿 CAS、取消、范围隔离与未验证写入阻断。

**判定：不满足 24 号文档的 WP4 Mock Provider E2E 完成条件；不得把本轮结果标记为 Mock Gate 通过，更不得标记为 `release-ready`。**

## 6. 复测入口

修复 VAL-01 至 VAL-06 后，至少复测：

```powershell
Set-Location E:\CPClaw\server
& 'C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' test '-Dtest=YunshuProtocolTests,YunshuSkillHttpControllerTests,PersistentFormTaskRuntimeTests,YunshuProviderSafetyTests'

Set-Location E:\CPClaw\web
npm run build
```

随后在配置独立测试数据库、启动后端并启用 Mock Provider 后，保存 SIM-01 至 SIM-12 的请求/响应、Provider 调用计数、事件序列与前端截图；未保存这些证据不得将“运行态 E2E”记为通过。

## 7. VAL-01 复测（同日）

复扫结果：`mcp-adapter/CPClaw-Yunshu-Skill.md` 已迁移为 `yunshu-skill/1.0`，明确外部宿主直连 Yunshu Skill Gateway、使用 `yunshu_task`，并禁止 `open_cpclaw_confirmation`、`cpclaw_authenticated_ui` 及跳转 CPClaw。该 Markdown 行为规范本身已修正。

但 **VAL-01 仍未关闭**：同一 `mcp-adapter/` 发布目录中的 `README.md` 仍配置 `https://cpclaw.example.com/api/mcp/cloudpivot/sse`，仍指导使用 `cpclaw_cloudpivot_agent` / `yunshu_handle_request`；`cpclaw-yunshu.mjs` 也仍转发到 `/api/mcp/cloudpivot`。该目录若整体作为外部接入包发布，宿主仍可能按旧 CPClaw MCP 路径接入。

复测判定：**VAL-01 状态为“部分修复，P0 未关闭”**。关闭条件是同步迁移或明确下线 `mcp-adapter/README.md` 与 `mcp-adapter/cpclaw-yunshu.mjs`，并再次确认发布目录不再包含旧 CPClaw MCP 地址、工具指引或 CPClaw 确认跳转。

### VAL-01 第二次复测（同日，最终）

已重新扫描 `mcp-adapter/` 的 Markdown 与 stdio 转发器。`README.md` 已明确新入口为 `POST /api/yunshu-skill/tasks/command`、高阶工具为 `yunshu_task`；`cpclaw-yunshu.mjs` 实际请求该独立 Gateway 路径。扫描到的 `/api/mcp/cloudpivot`、旧工具名和废止确认动作仅出现在“禁止/已废止/不连接”的否定说明中，不构成可执行目标或接入指引。

最终判定：**VAL-01 已关闭**。此结论仅解除外部接入包的旧 CPClaw MCP/确认跳转冲突；SIM-01、SIM-03、SIM-09、SIM-10 的不通过及 SIM-11、SIM-12 的阻塞结论保持不变。

## 8. 第三阶段表单与确认边界复测（同日）

### 执行证据

在 `server/` 使用 Maven 3.9.11 执行：

```powershell
& 'C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' test '-Dtest=PersistentFormTaskRuntimeTests,ConfirmationTicketServiceTests,YunshuProviderSafetyTests,YunshuProtocolTests,YunshuSkillHttpControllerTests'
```

结果：**13 tests、0 failures、0 errors、0 skipped**。本轮还复扫了 `mcp-adapter/`：转发器实际请求 `/api/yunshu-skill/tasks/command`；旧 MCP 词条和旧确认动作只存在于禁止/废止说明中，VAL-01 的关闭结论维持。

### SIM 状态更新

| 用例 | 本轮判定 | 可运行证据 | 边界 |
| --- | --- | --- | --- |
| SIM-01 少信息填单 | **通过（Mock Schema 范围）** | `providerSchemaDrivesQuestionsAndMultiFieldValidationBeforeConfirmationPlan` 注入 `name`、`level` 两个必填字段的 Stub Schema；`start` 返回 `needs_input`，问题分别精确引用缺失字段。 | Schema 由测试注入；默认 `UnverifiedFormSchemaProvider` 仍只收集通用表单描述，未证明真实云枢元数据发现。 |
| SIM-03 字段校验 | **通过（Mock Schema 范围）** | 同一运行测试提交 `level=C`，断言 `validation_error` 与字段级 `ENUM` 问题；修正为 `A` 后才进入确认计划。 | 未覆盖真实云枢关联校验和业务规则校验。 |
| SIM-05 取消 | **维持“通过（当前无写入安全边界）”** | `startInputGetAndCancelUseStableTaskAndCasVersions` 断言任务进入 `cancelled`；当前 `confirm` 分支不执行 Provider 写入。 | 尚无“已取消任务在 Provider 已验证时再次确认”端到端测试，不把它视为未来写入状态机已验收。 |
| SIM-06 确认安全边界 | **通过（安全阻断）** | `staleInputReturnsLatestEnvelopeAndConfirmCannotWrite` 断言 `confirm` 返回 `CONTRACT_UNVERIFIED`；`YunshuProviderSafetyTests` 断言默认 `createRecord` 不会报告完成。 | 这证明未验证契约下不写入，不代表提交能力通过。 |

### 确认票契约审查

`ConfirmationTicketServiceTests` 的两项运行测试证明：

- Provider 写契约未验证时，签发和执行授权均返回 `CONTRACT_UNVERIFIED`；
- 已签发票据绑定任务、租户、主体、宿主、Skill、表单、元数据、state/draft 版本、`planHash`、幂等键和有效期；
- 票据重放、草稿版本变化和跨宿主使用均返回 `CONFIRMATION_INVALID`；首次授权后票据被标记为 `CONSUMED`。

但该服务当前未由 `PersistentFormTaskRuntime` 注入或调用：Runtime 在草稿校验完成后只生成 `confirmation_required` 与 `planHash`，未向 Envelope 签发 `confirmationTicket`，也未调用 `authorizeExecution` 或 Provider 写入。因此确认票已完成**独立组件契约验证**，尚未完成 **Gateway → Runtime → 票据 → Provider** 的端到端接线验证。

### 保持不变的事实边界

SIM-09、SIM-10、SIM-11、SIM-12 以及数据库运行态 E2E 阻塞均未在本轮复测范围内取得新运行证据，原判定保持不变。真实云枢 Provider、UNKNOWN 回查/对账和实际提交仍不得标记为通过或 `release-ready`。

## 9. 第四阶段确认路径复测（同日）

### 执行证据

再次执行定向 Maven 测试：

```powershell
& 'C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' test '-Dtest=PersistentFormTaskRuntimeTests,ConfirmationTicketServiceTests,YunshuProviderSafetyTests,YunshuProtocolTests,YunshuSkillHttpControllerTests'
```

结果：**15 tests、0 failures、0 errors、0 skipped**（`PersistentFormTaskRuntimeTests` 6、`ConfirmationTicketServiceTests` 2、其余协议/Provider/HTTP 测试 7）。`mcp-adapter/` 复扫仍只发现独立 Gateway 可执行路径，旧 MCP 词条仅为明确禁止/废止说明。

### 复测判定

| 检查项 | 判定 | 证据与事实边界 |
| --- | --- | --- |
| Runtime confirm 路径状态检查 | **通过（有限范围）** | `cancelledTaskRejectsConfirmationBeforeProviderGuard`：已取消任务返回 `CONFIRMATION_INVALID`；`confirmationPlanHashMismatchIsRejected`：计划哈希不匹配返回 `CONFIRMATION_INVALID`。 |
| Provider 未验证时禁止写入 | **通过** | Runtime confirm 返回 `CONTRACT_UNVERIFIED`；Provider 默认 `createRecord` 返回未验证/未完成。 |
| 确认票绑定、防重放、跨主体/宿主、版本校验 | **通过（独立组件）** | `ConfirmationTicketServiceTests` 2 项通过：票据绑定主体、租户、宿主、版本、计划哈希和幂等键；重复消费、草稿版本变化、跨宿主均拒绝。 |
| 确认票真实进入 Runtime confirm 并授权 Provider | **未通过 / 未接线** | `PersistentFormTaskRuntime.action(confirm)` 当前未读取 `confirmationTicket`、未调用 `ConfirmationTicketService.authorizeExecution`，而是在状态/planHash检查后直接返回 `CONTRACT_UNVERIFIED`。因此不存在“有效票授权写入”或“过期/跨主体票据经 Runtime 拒绝”的端到端运行证据。 |
| 无票据 confirm 请求 | **安全阻断，但不是票据验收通过** | 当前无票据请求也会走到 `CONTRACT_UNVERIFIED`；这避免了未验证 Provider 写入，但不能证明生产环境已强制要求一次性确认票。 |

### 结论

本轮仅证明 Runtime 的取消与计划哈希边界、Provider 未验证安全阻断，以及确认票服务自身的签名/绑定/消费语义。**确认票尚未真实接入 Runtime → Provider 执行链，SIM-06 仍只能标记为“安全阻断通过”，不能升级为“确认提交通过”。**

SIM-09、SIM-10、SIM-11、SIM-12 及数据库运行态 E2E 的原有阻塞结论保持不变；真实云枢写入、远端回查/对账和生产交付仍为 No-Go。

## 10. 第五阶段确认票接线复测（同日）

### 执行证据

执行定向 Maven 测试：

```powershell
& 'C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11-bin\6mqf5t809d9geo83kj4ttckcbc\apache-maven-3.9.11\bin\mvn.cmd' test '-Dtest=ConfirmingFormTaskRuntimeDecoratorTests,PersistentFormTaskRuntimeTests,ConfirmationTicketServiceTests,YunshuProviderSafetyTests,YunshuProtocolTests,YunshuSkillHttpControllerTests'
```

结果：**17 tests、0 failures、0 errors、0 skipped**。`mcp-adapter/` 复扫继续确认 stdio 转发器只请求独立 Yunshu Skill Gateway；旧 CPClaw MCP 词条仅作为禁止/废止说明。

### 复测判定

| 检查项 | 判定 | 证据与事实边界 |
| --- | --- | --- |
| Provider 未验证阻断 | **通过** | `ConfirmingFormTaskRuntimeDecoratorTests.providerUnverifiedRejectsWithoutCallingDelegate` 与 `PersistentFormTaskRuntimeTests.staleInputReturnsLatestEnvelopeAndConfirmCannotWrite` 均证明未验证写入时返回 `CONTRACT_UNVERIFIED`，不调用下游写入。 |
| 无确认票拒绝 | **通过（有限范围）** | `PersistentFormTaskRuntimeTests` 的无票 `confirm` 请求在当前未验证 Provider 配置下被安全阻断；装饰器在 Provider 已验证分支对空票据有明确 `CONFIRMATION_INVALID` 代码路径，但没有单独的已验证 Provider 空票运行断言。 |
| 错票/计划哈希失配 | **通过** | `confirmationPlanHashMismatchIsRejected` 返回 `CONFIRMATION_INVALID`；装饰器测试的版本不匹配返回 `STATE_VERSION_CONFLICT`。 |
| 跨主体/跨宿主 | **通过（票据组件 + 装饰器）** | `ConfirmingFormTaskRuntimeDecoratorTests.mismatchedVersionAndCrossSubjectTicketAreRejected` 通过；`ConfirmationTicketServiceTests` 也验证跨宿主票据返回 `CONFIRMATION_INVALID`。 |
| 重复消费 | **通过（票据组件）** | `ConfirmationTicketServiceTests.ticketBindsPlanAndCanOnlyBeConsumedOnce` 首次授权成功，第二次返回 `CONFIRMATION_INVALID`，持久化状态为 `CONSUMED`。尚无真实 Provider 写入后的重复下游调用计数。 |
| 确认票真实接入 Runtime → Provider | **未完成** | 装饰器现在会读取票据并在授权后委托下游；但下游 `PersistentFormTaskRuntime` 自身也包含确认票据校验逻辑，生产 Spring 组合可能出现同一票据被装饰器和下游重复消费。当前测试使用 mock delegate，未覆盖真实组合 Bean，也没有 Provider 写入成功路径。 |

### 第五阶段结论

确认票已进入 Gateway 装饰器的运行路径，空票、哈希/版本不匹配、跨主体/宿主和重复消费具备拒绝证据；Provider 未验证时仍安全阻断。**但真实 Provider 写入尚未接通，且装饰器与下游 Runtime 存在潜在双重授权/消费风险，不能把本轮标记为提交 E2E 通过或 `release-ready`。**

SIM-09、SIM-10、SIM-11、SIM-12、数据库运行态 E2E 和真实云枢契约的原有阻塞结论保持不变。

## 11. 第五阶段复核：确认票双重消费风险（同日最终）

### 代码与测试证据

- `rg` 复扫生产代码显示，`authorizeExecution(` 唯一调用点为 `PersistentFormTaskRuntime.action(confirm)`；`ConfirmingFormTaskRuntimeDecorator` 的 `handle` 已改为透明 `delegate.handle(request, context)`，不再消费确认票。
- `YunshuSkillGatewayConfiguration` 当前未注册 Decorator Bean；Canonical Gateway 不会把 Decorator 叠加到 Persistent Runtime 上。
- `ConfirmingFormTaskRuntimeDecoratorTests` 已更新为验证透传和 `verifyNoInteractions(tickets)`，证明兼容 Decorator 不执行授权/消费。
- 重新执行定向 Maven 测试，结果仍为 **17 tests、0 failures、0 errors、0 skipped**。

### 复核结论

此前第 10 节记录的“Decorator 与下游 Runtime 可能双重消费确认票”风险，基于旧代码，现已被当前实现关闭。当前确认票消费责任唯一归属 `PersistentFormTaskRuntime.action(confirm)`；测试和静态代码证据未发现第二个生产消费点。

本结论只关闭双重消费风险，不改变以下事实：真实 Provider 写入、有效票授权后的提交回执、远端 UNKNOWN 回查/对账、数据库运行态 E2E 以及 SIM-09/10/11/12 仍未完成，不能标记为生产交付通过。
