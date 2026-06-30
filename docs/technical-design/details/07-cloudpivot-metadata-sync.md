# 云枢元数据同步增强设计

## 1. 目标

本设计明确 CPClaw 当前阶段的云枢元数据同步口径：同步云枢实体模型、实体模型下的数据项，以及由“关联表单”数据项推导出的实体关联关系。这里不是把字段做通用拆解，而是按云枢模型驱动语义保存可用于 Agent 理解用户意图的元数据知识。

## 2. 同步对象

- 云枢实体模型：来自云枢应用下的业务模型/实体模型列表，落库到 `cloudpivot_entities`，是运行态查询的主执行对象。
- 云枢数据项：来自业务模型详情中的 `dataItems`、`properties`、`fields`、`bizProperties`、`schemaProperties`、`items`、`columns` 等结构，落库到 `cloudpivot_data_items`。历史版本中的 `cloudpivot_entity_fields` 只作为兼容表保留，不作为新的业务语义命名。
- 实体关联关系：当数据项类型或配置识别为“关联表单”，并且能解析到真实目标实体模型时，生成 `cloudpivot_entity_relations`。关系通过 `source_data_item_id` 指向来源数据项，关系来源必须是关联表单数据项，不能凭空推断。

## 3. 采集策略

实体模型同步成功后，连接器会逐个实体探测业务模型详情接口，当前优先尝试以下路径：

- `/api/app/bizmodels/get`
- `/api/app/bizmodels/get_by_code`
- `/api/app/bizmodels/get_detail`
- `/api/runtime/app/get_bizmodel`
- `/api/app/bizmodels/load`

参数会按 `schemaCode`、`bizModelCode`、`modelCode`、`code` 以及可选 `appCode` 组合尝试。单个实体的数据项详情接口失败时只记录日志并继续同步其他实体，不能让一个模型详情失败污染整个元数据批次。

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

原始 JSON 需要写入 `raw_json`，方便后续根据真实云枢接口结构继续扩展数据项、表单、视图、Action 和流程能力。

## 5. 关联关系识别

关联关系只从“关联表单”数据项生成。识别信号包括：

- 类型或配置中包含 `关联表单`、`relevance_form`、`relevance`、`association`、`bizObject`、`reference` 等语义。
- 数据项配置中包含 `refSchemaCode`、`referenceSchemaCode`、`relativeCode`、`targetSchemaCode`、`targetBizModelCode`、`targetModelCode`、`associationSchemaCode` 等目标模型编码。
- 目标模型编码必须能解析到当前已同步的真实实体模型；无法解析时只保存数据项，不生成关系。
- 解析时需要避免把当前实体自己的 `schemaCode` 误判为目标实体。

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
