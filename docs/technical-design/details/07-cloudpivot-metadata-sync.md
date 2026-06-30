# 云枢元数据同步增强设计

## 1. 目标

本设计明确 CPClaw 当前阶段的云枢元数据同步口径：同步云枢实体模型、实体模型下的数据项，以及由“关联表单”数据项推导出的实体关联关系。这里不是把字段做通用拆解，而是按云枢模型驱动语义保存可用于 Agent 理解用户意图的元数据知识。

## 2. 同步对象

- 云枢实体模型：来自云枢应用下的业务模型/实体模型列表，落库到 `cloudpivot_entities`，是运行态查询的主执行对象。
- 云枢数据项：来自业务模型详情中的 `dataItems`、`properties`、`fields`、`bizProperties`、`schemaProperties`、`items`、`columns` 等结构，落库到 `cloudpivot_data_items`。历史版本中的 `cloudpivot_entity_fields` 只作为兼容表保留，不作为新的业务语义命名。
- 实体关联关系：当数据项类型或配置识别为“关联表单”，并且能解析到真实目标实体模型时，生成 `cloudpivot_entity_relations`。关系通过 `source_data_item_id` 指向来源数据项，关系来源必须是关联表单数据项，不能凭空推断。

## 3. 采集策略

实体模型同步成功后，连接器会逐个实体探测数据项接口。当前真实云枢环境已经确认，数据项主要来自设计态 `bizproperty` 接口，因此同步优先级调整为：

- `/api/app/bizproperty/list`，参数优先使用 `schemaCode`，可附带 `isPublish=true`。
- `/api/app/bizproperty/list_page`，用于兼容分页返回结构，解析 `data.content`、`records`、`rows` 等数组。
- `/api/app/bizproperty/list_find`，用于兼容搜索式数据项接口。
- `/api/runtime/form/get_biz_schema`，用于兼容运行态表单模型结构。
- `/api/app/bizmodels/get` 与 `/api/runtime/app/get_bizmodel`，作为旧版模型详情兜底。

同步器会并发探测实体数据项，单个实体的数据项接口失败时只记录调试日志并继续同步其他实体，不能让一个模型详情失败污染整个元数据批次。真实接口响应中的 `data` 若是 JSON 字符串，也需要继续解析。

## 4. 数据项识别

数据项编码会兼容以下字段：

- `code`
- `propertyCode`
- `fieldCode`
- `dataItemCode`
- `name`

数据项名称会兼容以下字段：

- `name`
- `displayName`
- `propertyName`
- `fieldName`
- `label`
- `name_i18n`

数据项类型会兼容以下字段：

- `type`
- `dataType`
- `propertyType`
- `fieldType`
- `controlType`

原始 JSON 需要写入 `raw_json`，方便后续根据真实云枢接口结构继续扩展数据项、表单、视图、Action 和流程能力。真实云枢数据项配置可能超过 MySQL `TEXT` 64KB 上限，因此云枢应用、实体、数据项和关系表中的 `raw_json` 必须使用 `LONGTEXT`。

## 5. 关联关系识别

关联关系只从“关联表单”数据项生成。识别信号包括：

- 类型或配置中包含 `关联表单`、`relevance_form`、`relevance`、`association`、`bizObject`、`reference` 等语义。
- 数据项配置中包含 `refSchemaCode`、`referenceSchemaCode`、`relativeCode`、`targetSchemaCode`、`targetBizModelCode`、`targetModelCode`、`associationSchemaCode` 等目标模型编码。
- `options`、`settings`、`config`、`relevance`、`reference`、`target`、`originalOptions` 等配置节点如果是 JSON 字符串，需要先解析后再查找明确目标模型编码。
- 目标模型编码必须能解析到当前已同步的真实实体模型；无法解析时只保存数据项，不生成关系。
- 解析时需要避免把当前实体自己的 `schemaCode` 误判为目标实体。
- 不允许通过全文扫描原始 JSON 中是否碰巧包含某个实体编码来生成关系；否则普通文本、数值、人员、部门、下拉等数据项会被误判为关联表单关系。

## 6. 搜索索引

元数据初始化完成后，`metadata_search_documents` 需要包含四类对象：

- `app`
- `entity`
- `data_item`
- `relation`

其中运行态查询仍以 `entity` 的真实 `schemaCode` 作为执行入口；`data_item` 和 `relation` 用于意图理解、分析维度识别、字段/数据项补全以及跨实体路径校验。

当用户输入精确编码，例如 `customerType` 或 `opportunityCustomer` 时，编码精确命中的元数据对象需要优先于泛化业务词命中，避免“客户”实体压过明确的数据项编码。

## 7. 同步响应

`/api/metadata/sync` 返回值需要包含：

- `appCount`
- `entityCount`
- `dataItemCount`
- `relationCount`
- `searchDocumentCount`

这些计数用于验证初始化是否真的采集到了实体模型、数据项和关联关系，而不是只完成应用/实体两层同步。

## 8. 验收标准

1. 元数据同步可持久化云枢实体模型、数据项和关联表单关系。
2. 关联关系只能由可解析目标实体的关联表单数据项生成。
3. 检索索引包含 `data_item` 和 `relation` 文档。
4. 精确数据项编码查询能命中数据项。
5. 实体模型仍是运行态查询的主执行对象，不允许数据项或关系替代真实 `schemaCode`。
6. 关联关系表中的 `source_data_item_id` 能回查到来源关联表单数据项。
7. 后端完整测试和前端构建通过。

## 9. 当前真实初始化结果

2026-06-30 在真实云枢环境重新初始化后，接口返回并经数据库抽样确认：

- 应用：`appCount=29`
- 实体模型：`entityCount=940`
- 数据项：`dataItemCount=16868`
- 关联表单关系：`relationCount=936`
- 搜索文档：`searchDocumentCount=18773`，其中 `app=29`、`entity=940`、`data_item=16868`、`relation=936`

本轮曾出现两类问题并已修正：

- 仅探测 `bizmodels/get` 时无法同步真实数据项，已改为优先使用真实云枢 `bizproperty` 数据项接口。
- 关系识别过宽时会把普通数据项误判为关系，已删除原始 JSON 全文扫实体编码的兜底，只保留明确目标模型配置生成关系。
