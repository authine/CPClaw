# 真实环境只读验证报告（QA-B）

## 1. 范围与安全边界

本轮仅验证当前 `localhost:8080` 实例的数据库运行形态、配置脱敏、元数据/图谱/会话只读接口。未执行服务重启、元数据同步、保存配置、模型测试、写入/删除、流程处理或任何会暴露密码、API Key、业务明细的操作。

## 2. 事实依据

- 当前 8080 实例对应 Java PID 39088；启动日志 `logs/server-h2-start.log` 明确记录 `jdbc:h2:mem:cpclaw-live`，数据库为内存 H2。
- 旧的 MySQL 启动记录显示 `root@localhost` 无密码认证失败（Flyway 获取连接失败），因此不能把当前运行实例视为 MySQL 持久化实例。
- `GET /api/settings` 返回成功；只返回账号、地址、模型概要及 `hasPassword`/`hasApiKey` 标记，未返回敏感值。
- `GET /api/metadata/apps`、`GET /api/metadata/model` 成功但为空；`GET /api/metadata/search?query=test` 成功但无结果。
- `GET /api/metadata/graph/overview` 成功，状态为 `UNINITIALIZED`，节点和边数量为 0；`GET /api/metadata/graph/export` 返回 500，提示图谱尚未构建。
- `GET /api/conversations` 成功并返回会话摘要；未读取消息正文。
- `/actuator/health` 不存在，返回 500（`No static resource actuator/health`）。

## 3. 测试用例矩阵

| 用例 | 预期 | 结果 | 备注 |
|---|---|---|---|
| QA-B-RO-001 数据库运行形态 | 使用 MySQL 持久库 | 阻断 | 当前为内存 H2，重启会丢数据 |
| QA-B-RO-002 配置读取脱敏 | 密码/API Key 不回传明文 | 通过 | 仅返回布尔标记 |
| QA-B-RO-003 元数据应用只读 | 返回已同步应用 | 未通过/未初始化 | 返回空数组 |
| QA-B-RO-004 元数据模型只读 | 返回实体/API 模型 | 未通过/未初始化 | `apps`、`apiActions` 均为空 |
| QA-B-RO-005 元数据搜索 | 返回可检索候选 | 未通过/未初始化 | 空结果，不代表搜索异常 |
| QA-B-RO-006 图谱概览 | 返回已构建快照统计 | 未通过/未初始化 | 0 节点、0 边 |
| QA-B-RO-007 图谱导出只读 | 导出已构建图谱 | 阻断 | 快照未构建，接口返回 500 |
| QA-B-RO-008 会话摘要 | 返回历史会话摘要 | 通过 | 未读取敏感消息正文 |
| QA-B-RO-009 健康检查 | 提供健康状态端点 | 阻断 | 未配置 Actuator 或等价端点，当前返回 500 |

## 4. 结论与后续门槛

本轮不能证明真实 MySQL、云枢元数据或图谱链路已可用；当前实例必须先切换到已认证的 MySQL，并完成 Flyway 迁移，再执行受控元数据同步和真实业务查询验收。由于同步会写入数据库，本报告不替代用户授权，不在本轮自动执行。

