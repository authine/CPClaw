# Yunshu Skill 真实元数据字段验证记录（2026-09-01）

## 验证范围

本记录验证运行态表单识别、字段来源和草稿写入使用已同步的云枢元数据；不把未验证的云枢创建接口当作通过。

## 真实元数据证据

- 元数据查询：`GET /api/metadata/search?query=私海线索`
- 识别对象：`私海线索`，应用 `zlcsstcrm`，业务对象编码 `sql_line`
- 真实字段示例：`sql_line_name`（线索名称）、`company_name`（公司名称）、`company_size`（企业规模）、`cust_nature`（企业性质）、`source_clue`（原线索）、`Label_description`（标签说明）
- 字段均来自 `cloudpivot_data_items`，运行时未生成 `form_details` 或其他泛化字段。

## 运行态结果

| 步骤 | 结果 |
| --- | --- |
| `start`：我需要创建一个线索，我最近跟进了一个客户，叫奥哲 | 返回 `needs_input`；`formRef.applicationCode=zlcsstcrm`、`schemaCode=sql_line`；返回真实字段列表 |
| `input`：`sql_line_name=奥哲`、`company_name=奥哲信息` | 同一 `taskId` 草稿更新，字段值写入对应真实编码；未出现泛化字段 |
| 普通字段校验/版本控制 | 定向 `PersistentFormTaskRuntimeTests` 全部通过，覆盖真实字段编码和 CAS |
| 无法解析元数据 | 返回 `AMBIGUOUS_TARGET`，字段列表为空，不降级为泛化表单 |

## 结论与边界

真实字段识别与本地草稿链路可执行；测试基准已改为云枢真实字段编码。云枢 `create/save/submit` 契约仍未验证，确认后只能安全阻断并保留业务信息，不能报告为真实创建成功。
