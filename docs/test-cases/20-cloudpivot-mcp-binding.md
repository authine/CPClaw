# 云枢 MCP 绑定验收用例（基础）

| 编号 | 场景 | 预期 |
| --- | --- | --- |
| MCP-01 | 打开系统设置的云枢 MCP 服务 | 显示 installationId、未启用状态和客户端配置片段；无密码 |
| MCP-02 | 未配置 CPClaw 云枢环境时发布 | 发布被拒绝，不创建终端凭据 |
| MCP-03 | 发布 MCP 服务 | 状态变为已启用，数据库无新增终端账号或密码 |
| MCP-04 | MCP 客户端缺少账号或密码 | MCP 返回终端配置引导，不调用云枢 |
| MCP-05 | MCP 调用未启用安装实例 | 返回发布引导，不调用云枢 |
| MCP-06 | MCP 调用已启用安装实例 | 使用 CPClaw 环境地址与当前客户端请求凭据；不读取其他实例凭据 |
| MCP-07 | 禁用后再次调用 | 返回未启用，不执行云枢请求 |
| MCP-08 | 写工具调用 | 只返回确认计划；未经用户确认不得执行 |
| MCP-09 | MCP 客户端配置为非本机 CPClaw 地址 | stdio adapter 拒绝启动，不发送账号或密码 |
| MCP-10 | 查询未同步的 schemaCode 或含敏感字段的记录 | 拒绝未同步对象；已同步对象仅返回语义化摘要卡片，不回传原始敏感字段 |
| MCP-11 | `notifications/initialized` 通知 | stdio adapter 不向标准输出写入 JSON-RPC 响应 |
# 云枢 MCP 对接验收补充：OpenClaw 领域委派闭环

## P0 复合任务

输入：`帮我看看今年项目的整体情况，找出风险，并把高风险项目的负责人列出来`

验收要求：

1. OpenClaw 只发起一次 `cpclaw_cloudpivot_agent`（旧客户端可调用 `yunshu_handle_request`）；
2. 请求带 `conversationId`、`turnId`、`clientRequestId` 和 `taskSpec.deliverables`；
3. 返回同时包含 `completion`、`evidence` 和完整 `content.text`；
4. 若风险信号、负责人关系或字段覆盖不足，状态为 `partial`/`blocked`，明确列出 `missingEvidence`，不得伪造高风险项目或负责人；
5. 同一 `clientRequestId` 或同一轮 `turnId` 的重复调用直接 replay，不再次访问云枢；
6. OpenClaw 只能改写表达，不得修改事实、数字、范围、口径、警告和确认条件；
7. 只有用户补充约束、可信 continuation token 或确认票才允许后续执行。

## 当前实现边界

当前代码已经落地任务规格、证据/完成度结果的持久化，以及同轮重放/执行中拦截。真正的云枢复合分析（跨实体分页、关系补查、通用风险信号）和签名 continuation token 仍需后续增量实现；在此之前，测试必须接受 `partial`，不能把它当成失败或完整报告。
