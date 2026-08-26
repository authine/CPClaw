# 会话侧边栏生命周期测试用例

## 目标

验证新对话、流式输出、完成未读、已读确认和异常终态在侧边栏中的一致表现。

## 自动化覆盖

| 用例 | 预期 | 验证 |
|---|---|---|
| 新建 `DRAFT` 会话 | 不出现在会话列表 | `ConversationLifecycleApiTests.draftIsHiddenUntilOutputStartsAndReadEndpointIsIdempotent` |
| 首个有效输出 | `RUNNING`，进入侧边栏并显示等待信号 | `ConversationLifecycleServiceTests.outputLifecycleMovesDraftToRunningThenCompletedAndUnread` |
| 有内容完成 | `COMPLETED + unread=true` | 同上 |
| 空输出失败 | 保持 `DRAFT`，不进入列表 | `ConversationLifecycleServiceTests.failureAndCancellationDoNotExposeEmptyDraftButKeepCompletedOutputUnread` |
| 已读接口 | 重复调用仍成功并清除未读 | API 测试与 `markReadIsIdempotentAndClearsUnread` |

## 浏览器验收

1. 点击“新建对话”，确认在模型产生第一个非空回答块之前，左侧“最近对话”没有新增条目。
2. 首个回答块出现后，确认会话加入列表，标题右侧显示主题色旋转等待信号。
3. `final` 到达后，确认等待信号变为绿色小圆点；刷新页面后状态仍存在。
4. 点击该会话，或在当前消息区滚动到距离底部不超过 24px，确认绿色小圆点消失。
5. 在输出期间点击停止或制造请求失败，确认不会遗留 `RUNNING`；有过可见输出时显示终态未读，没有输出时不显示会话。

## 约束

- 当前版本保持单个活动流式会话，输出期间不支持切换到另一个会话。
- 真实登录启用后，`unread` 必须按用户主体隔离；当前默认用户上下文仅用于 V1 无登录环境。
