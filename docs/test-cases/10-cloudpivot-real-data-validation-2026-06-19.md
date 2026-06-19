# 云枢元数据与 CRM 商机真实数据验证报告

## 1. 验证结论

- 验证日期：2026-06-19
- 验证角色：测试工程师
- 验证环境：本地后端 `http://localhost:8080`，本地前端 `http://localhost:5173`，本地 MySQL `127.0.0.1:3306/CPClaw`
- 总体结论：通过
- 通过标准：云枢连接成功、元数据初始化成功、本地 MySQL 写入成功、CRM 应用下商机模型可命中、对话链路调用云枢运行态接口并返回真实商机数据、审计记录完整。

本报告不记录任何明文密码、Token、Cookie、Session ID 或 API Key。

## 2. 真实验证数据摘要

| 项目 | 实际结果 |
| --- | --- |
| 普通用户云枢连接 | 成功，返回“普通用户云枢账号验证通过” |
| 管理员云枢连接 | 成功，返回“管理员云枢账号验证通过，可执行云枢元数据初始化” |
| 正确初始化入口 | `POST /api/metadata/sync` |
| 初始化状态 | `cloudpivot-metadata-initialized` |
| 同步批次 | `4b1f8420-2801-418d-8eec-db525b7c9473` |
| 应用数量 | 29 |
| 实体数量 | 922 |
| 搜索索引数量 | 951 |
| MySQL 表计数 | `cloudpivot_apps=29`，`cloudpivot_entities=922`，`metadata_search_documents=951` |
| CRM 商机命中 | `zlcsstcrm / 商机`，模型编码 `int_bu_oppor` |
| 商机运行态查询总数 | 237 |
| 对话 Agent Run | `de977b97-69ff-4928-baa1-77184e1574e5`，状态 `completed` |
| 审计工具调用 | `metadata_search`、`cloudpivot_runtime_query` 均为 `completed` |

## 3. 测试用例与执行结果

### TC-REAL-001 本地服务与 MySQL 可用性

- 前置条件：后端、前端、本地 MySQL 已启动。
- 操作步骤：
  1. 检查后端端口 `8080`。
  2. 检查前端端口 `5173`。
  3. 检查 MySQL 端口 `3306`。
  4. 使用 JDBC 连接 `CPClaw` 数据库并统计核心元数据表行数。
- 预期结果：服务端口可访问，数据库可连接，元数据表存在且有数据。
- 实际结果：
  - `127.0.0.1:8080` 可连接。
  - `127.0.0.1:5173` 可访问。
  - `127.0.0.1:3306` 可连接。
  - `cloudpivot_apps=29`。
  - `cloudpivot_entities=922`。
  - `metadata_search_documents=951`。
- 结论：通过。

### TC-REAL-002 云枢普通用户连接验证

- 前置条件：设置中已保存普通用户云枢访问地址、账号和密码凭据。
- 操作步骤：调用 `POST /api/settings/cloudpivot/test`。
- 预期结果：接口返回成功，且不回显密码。
- 实际结果：
  - `success=true`。
  - `data.success=true`。
  - `data.message=普通用户云枢账号验证通过`。
  - 设置查询接口仅返回 `hasPassword=true`，未返回明文密码。
- 结论：通过。

### TC-REAL-003 云枢管理员连接验证

- 前置条件：设置中已保存管理员云枢访问地址、账号和密码凭据。
- 操作步骤：调用 `POST /api/settings/metadata-cloudpivot/test`。
- 预期结果：接口返回成功，且可用于元数据初始化。
- 实际结果：
  - `success=true`。
  - `data.success=true`。
  - `data.message=管理员云枢账号验证通过，可执行云枢元数据初始化`。
- 结论：通过。

### TC-REAL-004 云枢元数据初始化

- 前置条件：管理员云枢连接验证通过，本地 MySQL 可用。
- 操作步骤：调用 `POST /api/metadata/sync`。
- 预期结果：返回初始化成功状态，并将应用、实体和搜索索引写入 MySQL。
- 实际结果：
  - `status=cloudpivot-metadata-initialized`。
  - `syncId=4b1f8420-2801-418d-8eec-db525b7c9473`。
  - `appCount=29`。
  - `entityCount=922`。
  - `searchDocumentCount=951`。
  - JDBC 表计数与接口返回一致。
- 结论：通过。

### TC-REAL-005 初始化入口路径校验

- 前置条件：后端服务已启动。
- 操作步骤：分别调用旧路径和当前项目实际路径。
- 预期结果：当前项目实际路径可初始化，旧路径不得作为验收依据。
- 实际结果：
  - `POST /api/metadata/cloudpivot/initialize` 返回 `No static resource api/metadata/cloudpivot/initialize.`。
  - `POST /api/metadata/sync` 返回初始化成功。
- 结论：通过。后续验证统一使用 `POST /api/metadata/sync`。

### TC-REAL-006 CRM 应用下商机元数据命中

- 前置条件：云枢元数据已初始化。
- 操作步骤：调用 `GET /api/metadata/search?query=CRM%20%E5%95%86%E6%9C%BA`。
- 预期结果：优先返回 CRM 应用下的商机实体。
- 实际结果：首条返回：
  - `name=商机`。
  - `code=int_bu_oppor`。
  - `graphPath=zlcsstcrm / 商机`。
  - `riskLevel=low`。
  - `reason=命中本地 Metadata Index`。
- 结论：通过。

### TC-REAL-007 对话查询 CRM 商机真实业务数据

- 前置条件：云枢元数据已初始化，普通用户云枢连接可用。
- 操作步骤：
  1. 创建测试会话。
  2. 发送用户消息：`查询CRM应用下面的商机`。
  3. 检查 Agent 意图、计划摘要和助手回复。
- 预期结果：识别为查询意图，匹配 CRM 商机模型，调用云枢运行态接口，返回真实商机数据和总数。
- 实际结果：
  - `intent=query_data`。
  - `riskLevel=low`。
  - `requiresConfirmation=false`。
  - 计划摘要：已根据本地元数据匹配到“商机”，并调用云枢运行态接口查询到真实数据。
  - 助手回复：已在云枢中查询到“商机”对应数据，总计 `237` 条。
- 真实返回样例：
  1. `instanceName=北京菲斯曼供热`
  2. `instanceName=云南交投经营管理系统升级`
  3. `instanceName=国能--低代码平台项目`
  4. `instanceName=广顺兴餐饮`
  5. `instanceName=DHL OA 礼品领用&客户服务类查询项目`
  6. `instanceName=成都交投善成实业资产管理数字化系统建设项目`
  7. `instanceName=DHL 异地取件多流程整合优化`
  8. `instanceName=航天火工 BPM`
  9. `instanceName=贝壳低领代码一体化平台`
  10. `instanceName=省公安厅AI项目`
- 结论：通过。

### TC-REAL-008 Agent 审计记录验证

- 前置条件：已完成一次 CRM 商机真实数据查询。
- 操作步骤：调用 `GET /api/audit/agent-runs/de977b97-69ff-4928-baa1-77184e1574e5`。
- 预期结果：Agent Run 状态完成，工具调用记录完整，输入输出已脱敏且包含运行态查询结果。
- 实际结果：
  - `status=completed`。
  - `intent=query_data`。
  - 工具调用数量：2。
  - `metadata_search`：`inputJsonMasked={query:查询CRM应用下面的商机}`，`outputJsonMasked={match:商机,code:int_bu_oppor,objectType:entity}`。
  - `cloudpivot_runtime_query`：`inputJsonMasked={schemaCode:int_bu_oppor}`，`outputJsonMasked={returnedRecords:10,total:237}`。
- 结论：通过。

## 4. 问题定位与修复覆盖

| 问题 | 处理结果 |
| --- | --- |
| 提示“请先初始化云枢元数据” | 已用正确入口初始化，且 `CRM 商机` 可命中本地索引。 |
| CRM 商机应在 CRM 应用下优先命中 | 已增强 CRM 查询相关性排序，首条命中 `zlcsstcrm / 商机`。 |
| 云枢接口路径存在 `/api/api/...` 部署前缀差异 | 已在连接器中兼容 `/api/api/...` 与 `/api/...` 路径。 |
| 运行态列表返回不含足够可读字段 | 已补充详情接口读取，将 `instanceName/name/title` 优先展示。 |
| PowerShell 中文请求编码可能导致乱码 | 实测链路使用 UTF-8 JSON 字节或 `curl --data-binary` 验证。 |

## 5. 测试结论

本轮测试按照真实业务场景验证，不使用模拟数据作为通过依据。云枢元数据已初始化到本地 MySQL，CRM 应用下“商机”模型已命中，运行态查询返回 `237` 条真实商机数据，并展示真实业务名称。审计记录显示本次链路执行了元数据搜索和云枢运行态查询，状态均为完成。

结论：通过，可以进入提交与推送环节。
