# 长任务进度与模型流式输出验证（2026-08-25）

## 目标

避免云枢分析任务在模型总结阶段长期停留在静态步骤；用户始终能看到真实步骤的持续进度，并在模型输出可用时立即看到正文增量。

## 验收点

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| LT-01 | 任一执行步骤超过 5 秒 | SSE 每 5 秒发送同一步骤的 `heartbeat`，包含已耗时；前端更新原步骤，不新增重复步骤。 |
| LT-02 | 大模型返回流式正文 | 服务端发送 `answer_start`、多个 `answer_delta`、`answer_end`，前端直接增量展示正文。 |
| LT-03 | 模型 90 秒未返回正文 | 关闭模型流，发送 `answer_reset`，改用已核验指标生成稳定报告。 |
| LT-04 | 供应商发送 `[DONE]` 但不关闭连接 | 最多等待 750ms 接收 usage，随后收尾，不继续停留在运行状态。 |
| LT-05 | 流结束 | 收到 `final` 或明确 `error`，停止心跳和前端计时。 |

## 自动验证

- `YunshuModelGatewayStreamTests`：3 条通过，覆盖正文增量、未完成流拒绝与 `[DONE]` 后 usage。
- `OpenAiCompatibleModelGatewayStreamTests`：9 条通过。
- `ConversationLifecycleApiTests`：1 条通过。
- 后端 `mvn test`：80 条通过，0 失败。
- 前端 `npm run build`：通过。
