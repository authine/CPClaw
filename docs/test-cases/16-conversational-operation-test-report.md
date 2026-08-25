# 对话式系统整体测试报告（P0）

## 1. 测试范围

覆盖后端 API、Agent 执行链、元数据匹配、智能问数、关联分析基础、会话记忆、敏感信息脱敏、结果集引用、删除确认、确认计划完整性、重复提交阻断、审计时间线和前端生产构建。测试使用 Spring Boot + H2 + Mockito 模拟 CloudPivot，不等同于真实租户联调。

## 2. 可复现命令

```text
server/  C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11\a2d47e15\bin\mvn.cmd -q test
server/  C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11\a2d47e15\bin\mvn.cmd -q -Dtest=CpClawApiTests test
web/     npm run build
```

结果：后端 12 个测试类、28 个用例，0 failures、0 errors、0 skipped；专项 API 测试通过；前端构建通过。仅存在既有 Rollup PURE 注释和 chunk size 警告。

## 3. 用例矩阵

| ID | 场景 | 期望 | 实际 | 状态 |
| --- | --- | --- | --- | --- |
| TC-01 | 单对象计数问数 | 调用运行态 API，返回 total/returned | 通过 | PASS |
| TC-02 | 明细查询 | 返回真实记录摘要和来源接口 | 通过 | PASS |
| TC-03 | 多轮对象承接 | 继承上一轮 schemaCode，不混淆对象 | 通过 | PASS |
| TC-04 | 关联分析 | 仅沿已同步关系生成分析 | 通过 | PASS |
| TC-05 | ReAct/Reflection | 时间线包含理解、规划、执行、校验 | 通过 | PASS |
| TC-06 | 记忆召回 | 读取会话级有效记忆并显示事件 | 通过 | PASS |
| TC-07 | 敏感记忆隔离 | 凭据、失败猜测不写入 | 通过 | PASS |
| TC-08 | 无结果引用删除 | 澄清且不创建确认单 | 通过 | PASS |
| TC-09 | 列表后删除第一条 | 绑定当前会话实际 recordId | 通过 | PASS |
| TC-10 | 删除确认 | 仅确认后调用 delete API | 通过 | PASS |
| TC-11 | 重复确认 | 不重复调用 delete API | 通过 | PASS |
| TC-12 | 篡改确认计划 | SHA-256 校验失败并阻断 | 通过 | PASS |
| TC-13 | 会话删除清理 | 同步清理消息、引用和记忆 | 通过 | PASS |
| TC-14 | 新增/更新/填单 | 形成可执行闭环 | 未实现，按设计拒绝执行 | N/A |
| TC-15 | 待办/已办流程 | 当前用户权限下查询和处理 | 未接入真实流程 API | N/A |
| TC-16 | 版本/ETag 冲突 | 并发修改时阻断 | CloudPivot 契约未验证 | BLOCKED |
| TC-17 | 用户权限重验 | 按实际用户权限执行 | 当前为系统级凭据 | BLOCKED |

## 4. 测试结论

P0 的本地受控试点通过：问数、透明执行、记忆脱敏和“查询结果引用→确认→删除”闭环可验收。N/A 项不得视为交付；BLOCKED 项必须完成真实 CloudPivot 测试租户、用户主体、权限、版本和幂等契约验证后才能关闭。
