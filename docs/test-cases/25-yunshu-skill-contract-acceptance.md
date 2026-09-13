# Yunshu Skill 协议与跨宿主验收契约

> 版本：v1.0｜状态：开发前验收基线｜本文件用于验证 24 号实施契约，不等同于真实云枢生产验收。

## 1. 测试环境

| 环境 | 用途 |
| --- | --- |
| Mock Yunshu Skill Gateway | 验证独立启动、协议和认证边界 |
| Mock CloudPivot Provider | 注入成功、校验错误、超时、Schema 漂移和幂等场景 |
| CPClaw Web Host | 验证结构化卡片、草稿和确认回调 |
| Mock WorkBuddy Host | 验证 MCP/JSON 降级、宿主内确认和任务恢复 |
| 双实例 + 可重启数据库 | 验证任务事实源、worker 和事件回放 |

## 2. 核心用例

| 编号 | 输入/动作 | 预期结果 | 禁止行为 | 必留证据 |
| --- | --- | --- | --- | --- |
| YS-01 | `start`：“新增线索” | `needs_input`，返回候选表单和最小必填问题 | 猜测表单或直接写入 | request/response、taskId |
| YS-02 | `input` 补充字段 | 同一 taskId，draftVersion 递增 | 丢失已填字段、创建子任务 | 两次 Envelope、版本变化 |
| YS-03 | 提交非法枚举/关联 | `validation_error`，逐字段错误 | 静默修正或猜测 | validation.issues、Provider 调用记录 |
| YS-04 | 修正草稿 | `ready_to_submit`，planHash 更新 | 复用旧确认票 | 草稿修订、哈希变化 |
| YS-05 | 宿主确认 | `submitting → submitted`，返回 recordRef；票据绑定 task/tenant/subject/host/state/draft/planHash 且只能消费一次 | 依赖 CPClaw UI、用 `confirmed=true` 代替票据、未验证 Provider 写入 | 签名票据、原子消费审计、回执 |
| YS-06 | 重复 `clientRequestId` | 返回相同结果，下游只调用一次 | 重复创建记录 | 调用计数、结果对比 |
| YS-07 | 云枢超时且结果未知 | `unknown`，进入回查/对账 | 立即重试写入 | UNKNOWN 事件、对账结果 |
| YS-08 | 进程重启/SSE 断线 | 原 taskId 可恢复，事件有序 | 重新创建任务或丢失草稿 | 重启前后 Envelope、eventSequence |
| YS-09 | 跨租户/主体读取 | 不可探测错误 | 返回任务、草稿或确认票 | 安全审计 |
| YS-10 | 普通闲聊 | `general_conversation`，不调用云枢 | 触发元数据探查 | Provider 调用计数 |
| YS-11 | 未审核 Markdown Skill | `blocked` | 执行 Skill 内容 | Skill 审核状态、审计 |
| YS-12 | 同一目标 Web/WorkBuddy | 状态、草稿、错误、planHash、回执一致 | 宿主自行重建业务逻辑 | 两端 Envelope 差异报告 |

## 3. 通过标准

- YS-01 至 YS-12 全部通过；
- 写操作下游重复调用次数为 0；
- 响应中不存在 CPClaw URL、CPClaw UI 动作、凭据、Token、内部 API/Schema 编码和原始思维链；
- 所有状态转移符合 24 号协议；非法转移返回明确错误码；
- 失败用例必须输出复现步骤、日志/回执、缺陷等级和是否阻塞发布。

## 4. 证据包格式

每个用例保存：`caseId`、版本、环境、主体/租户指纹、请求摘要、响应 Envelope、事件序列、Provider 调用计数、审计引用、截图或文本结果、结论和缺陷清单。禁止保存明文凭据、原始业务敏感数据和模型思维链。

## 5. 门禁判定

- 仅 Mock 用例通过：允许进入分阶段开发，不允许生产交付；
- Mock + 沙箱录制回放 + 真实云枢只读通过：允许灰度准备；
- G0–G6 全部通过且完成真实写入、身份、跨宿主和回滚演练：才可标记 `release-ready`。
