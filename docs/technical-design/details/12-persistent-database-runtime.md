# 持久化数据库运行技术方案

**文档类型：运行与部署专项｜状态：已实现并验证（Flyway V1–V30；迁移脚本为唯一 schema 事实源）。**

## 数据边界

MySQL 是 CPClaw 的唯一运行数据库。`system_settings`、`encrypted_credentials`、`model_configs` 保存配置；`cloudpivot_*`、`metadata_search_documents`、`metadata_graph_*` 保存元数据与图谱；会话、审计、确认、结果引用、模型调用分析和 Agent 记忆也使用同一库。Flyway `V1` 至 `V10` 是唯一 schema 变更入口，JPA `ddl-auto` 保持 `none`。

数据库基础设施不由业务设置页管理：关系库通过 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD` 配置；可选 pgvector 通过 `CPCLAW_VECTOR_ENABLED`、`CPCLAW_VECTOR_JDBC_URL`、`CPCLAW_VECTOR_USERNAME`、`CPCLAW_VECTOR_PASSWORD` 配置。pgvector 是可重建的检索增强库，不是运行配置的权威库。管理员云枢环境仅用于连接云枢并同步元数据；遗留 `search_engine_type` / `search_engine_url` 不参与当前检索链路，应从 UI 隐藏。

## 启动门禁

新增持久化运行时门禁，在服务就绪前检查：

1. 数据库连接存在且数据库产品为 MySQL；
2. JDBC URL 不是 `jdbc:h2:mem:` 或其他内存库；
3. `CPC_ENCRYPTION_KEY` 可由部署平台注入；未注入时，首次启动自动在 `CPC_ENCRYPTION_KEY_FILE`（默认 `storage/.encryption-key`）生成并持久化随机密钥，禁止使用代码内置默认密钥；
4. Flyway 已启用并完成迁移。

任一条件不满足即失败退出，不能用临时 H2 代替 MySQL。测试资源显式关闭此门禁，并只在测试 JVM 中使用 H2。

## 密钥与恢复

凭据采用 AES-GCM 密文保存。`CPC_ENCRYPTION_KEY` 应由部署平台的密钥管理能力稳定注入，并纳入备份/灾备清单；单机部署未注入时，必须备份默认的 `storage/.encryption-key` 文件。应用启动时若环境变量与密钥文件不一致会主动失败，防止静默使已有凭据失效。改变该值前必须先以旧密钥解密、以新密钥重加密后再切换。数据库备份至少覆盖配置、凭据和所有 `cloudpivot_*` / `metadata_*` 表，并通过恢复后的只读校验验证。

## 现有内存数据迁移

运行中的内存 H2 数据与外部 MySQL 不共享。迁移前先建立并验证 MySQL 连接，再在仍运行的实例中安全导出非秘密配置和密文记录，导入后执行受控重启及读取比对。没有目标库凭据或未完成安全导出时，禁止重启当前实例；无法导出的密钥需由管理员重新录入，不能伪造“已恢复”。本规则不适用于测试 JVM，因为测试数据本来不属于正式运行状态。
### 凭据密钥不一致处理

`encrypted_credentials` 的密文不可解时，`CredentialService` 返回受控的 `UNREADABLE` 状态并抛出不含密文的业务异常。连接测试返回业务失败而非 HTTP 500；运行时查询、元数据同步和确认操作返回“请重新录入凭据”的可恢复提示。除非用户明确授权，不删除旧密文；只有重新录入成功后才更新同一 owner/type 记录。

模型删除通过 `DELETE /api/settings/models/{id}` 执行，在事务内删除 `model_configs` 及同 owner/type 的 API Key 密文；会话和审计表只保存历史 ID，不做级联删除。

### Token 用量记录

模型网关统一读取标准 `usage`，并兼容 `input_tokens/output_tokens`、驼峰字段、嵌套 `response.usage` 以及流式响应结束标记后的 usage 事件。一次 Agent 执行内的多次模型调用在 `ModelUsageContext` 中累加，最终写入 `agent_runs`；日志分析在主记录缺失时回退汇总 `agent_model_calls`。供应商没有返回 usage 时必须显示“—”，不得用字符数估算。
