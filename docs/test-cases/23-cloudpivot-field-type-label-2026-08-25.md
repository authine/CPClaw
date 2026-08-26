# 云枢字段类型展示验证（2026-08-25）

## 目标

元数据页面的“类型”列不再直接展示云枢底层数值枚举；保留原始类型编码供系统内部执行和诊断，并向用户展示云枢类型显示名。

## 变更

- `dataType`：返回云枢类型显示名，例如 `文本（Text）`、`日期时间（DateTime）`、`人员（User）`、`部门（Department）`。
- `dataTypeCode`：保留云枢原始类型编码，例如 `0`、`3`、`50`、`60`。
- 类型映射位于云枢 Skill 的元数据语义层，不进入 CPClaw 框架业务判断。

## 验证结果

- `CloudPivotMetadataSemanticsTests`：4 条通过。
- `CpClawApiTests`：3 条通过。
- 后端全量测试：75 条通过，0 失败，0 错误。
- 前端 `npm run build`：通过。
- 真实接口 `/api/metadata/model` 验证：
  - `0` → `文本（Text）`
  - `3` → `日期时间（DateTime）`
  - `50` → `人员（User）`
  - `60` → `部门（Department）`
