# Yunshu Skill 模拟验证用例（2026-09-01）

> 目的：在真实云枢写入契约尚未接通前，以持久化 FormTask Runtime 和 Stub Provider 验证协议、安全边界和宿主体验。通过不代表真实云枢生产验收。

## 环境与边界

- 被测协议：`yunshu-skill/1.0`
- 被测入口：`POST /api/yunshu-skill/tasks/command`
- 被测运行时：`PersistentFormTaskRuntime`
- Provider：测试替身；任何 `confirm` 或真实写入必须返回 `CONTRACT_UNVERIFIED`
- 主体范围：`tenantId + subjectId + hostId`

## 模拟用例

| 编号 | 场景 | 输入/步骤 | 期望状态与断言 |
| --- | --- | --- | --- |
| SIM-01 | 少信息填单 | `start`，目标“新增线索”；Stub Schema 定义名称、联系人为必填 | 创建稳定 `taskId`，返回 `needs_input`，只询问阻塞字段；不调用写 Provider |
| SIM-02 | 多轮补充 | 对 SIM-01 `taskId` 发送 `input` | `stateVersion/draftVersion` 递增，已提供字段保留，不创建子任务 |
| SIM-03 | 字段校验 | 补丁提供非法枚举或空必填字段 | `validation_error`，`validation.issues` 包含字段级错误，不静默修正 |
| SIM-04 | 乐观并发 | 使用旧 `expectedStateVersion` 修改草稿 | `STATE_VERSION_CONFLICT`，返回最新草稿，不覆盖新版本 |
| SIM-05 | 取消 | 对未提交任务发送 `action(cancel)` | 同一 `taskId` 进入 `cancelled`，后续写动作不允许执行 |
| SIM-06 | 确认安全边界 | 对可提交草稿发送 `action(confirm)` | 返回 `CONTRACT_UNVERIFIED`；无 CloudPivot 写入、无伪造 `recordRef` |
| SIM-07 | 范围隔离 | 主体 B 查询主体 A 的 `taskId` | 返回不可探测的拒绝/无结果，不泄露草稿、确认或事件 |
| SIM-08 | 宿主认证 | 无 `X-Yunshu-Host-Id` 或传入未注册 `workbuddy` | HTTP 401 + `PERMISSION_DENIED`；不信任请求体身份字段 |
| SIM-09 | Web 宿主适配 | CPClaw Web 发送 `X-Yunshu-Host-Id: cpclaw-web` | HTTP 请求进入 Canonical Gateway；响应无 `open_cpclaw_confirmation` / `cpclaw_authenticated_ui` |
| SIM-10 | 通用对话隔离 | 普通闲聊不走 `yunshu_task` | 云枢 Provider 调用计数为 0；不创建 FormTask |
| SIM-11 | 未知态规则 | Stub Provider 返回写入后本地超时 | 设计状态为 `unknown`，禁止自动二次写入；当前真实写入未接通时以 `CONTRACT_UNVERIFIED` 证明安全阻断 |
| SIM-12 | 前端降级展示 | Envelope 依次为 `needs_input`、`validation_error`、`confirmation_required`、`unknown` | `YunshuTaskCard` 在当前宿主展示对应交互；没有跳转 CPClaw UI 的动作 |

## 判定规则

- SIM-01 至 SIM-10 为当前实现必须通过的 Mock/协议门槛。
- SIM-11 的真实回查/对账为 Provider 接通后的必测项；在当前阶段不能报告为已通过。
- SIM-12 至少需 TypeScript 构建通过；运行态浏览器验证依赖后端数据库环境恢复。
- 任何响应包含凭据、内部 API/Schema 编码、`open_cpclaw_confirmation` 或 `cpclaw_authenticated_ui`，均判定 P0 失败。
