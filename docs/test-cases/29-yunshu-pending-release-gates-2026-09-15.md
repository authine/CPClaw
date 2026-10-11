# 云枢 Skill 未完成项测试验证（2026-09-15）

本用例集用于验证尚未达到投产的能力，明确区分“安全阻断通过”和“真实能力通过”。测试不得把 Mock Provider 或候选接口当作真实云枢写入证据。

| 编号 | 未完成项 | 当前验证 | 结论 |
|---|---|---|---|
| PG-01 | 执行账本创建 | `YunshuOperationExecutionServiceTests.beginCreatesDurableStartedExecutionWithScopedIdempotency` | 通过：创建前记录 STARTED |
| PG-02 | 相同幂等键重放 | `beginReplaysExistingExecutionInsteadOfStartingAnotherOperation` | 通过：不重复创建执行记录 |
| PG-03 | 空幂等键 | `blankIdempotencyIsRejectedBeforeProviderExecution` | 通过：拒绝不安全写命令 |
| PG-04 | 完成/未知状态 | `completeAndUnknownPersistTerminalEvidence` | 通过：账本能保存 COMPLETED/UNKNOWN |
| PG-05 | 未验证运行时表单写入 | `CloudPivotRuntimeFormContractTests.defaultConnectorKeepsRuntimeFormWritesFailClosedUntilPayloadIsVerified` | 通过：默认连接器不发送猜测写请求 |
| PG-06 | 无记录标识响应 | `aSuccessfulTransportWithoutRecordIdentityCannotBeReportedAsCompleted` | 通过：无记录标识不得报告成功 |
| PG-07 | 真实 save/submit payload | `docs/test-cases/32-real-provider-write-reconcile-2026-09-19.md`：真实 `test/test_multi_branch` 字段创建与提交已执行 | 已完成（受控测试数据） |
| PG-08 | 真实创建及回查 | 同一用例记录真实 `recordRef`、刷新恢复和 `remoteReconciled=true` | 已完成（受控测试数据） |
| PG-09 | 超时后的 UNKNOWN 回查 | `YunshuUnknownReconciliationSchedulerTests.reconciliationTransportFailureLeavesUnknownAndNeverWritesAgain`：传输异常不完成任务、不消费票据、不重试创建；新增数据库原子租约，未领取记录会跳过，租约过期可接管；真实网络中断注入仍待执行 | 多实例安全行为通过；真实故障演练未完成 |
| PG-10 | WorkBuddy/OpenClaw 生产身份 | 已有 HMAC 与 OIDC introspection resolver 及定向测试；真实企业 IdP、WorkBuddy 密钥托管和跨租户身份矩阵 E2E 仍待目标环境执行 | 适配器代码通过；生产联调未完成 |

## 发布判定

PG-01 至 PG-08 已有代码或受控真实环境证据；PG-09 的真实网络故障演练和 PG-10 的目标环境身份联调仍未完成，因此本用例集不能作为投产通过证据。生产发布仍必须补齐真实网络超时注入、外部宿主身份矩阵和端到端验收。
