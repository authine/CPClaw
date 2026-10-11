# 云枢真实智能填单草稿与确认计划验收（2026-09-24）

本用例验证真实云枢元数据驱动的智能填单，不执行真实写入。确认后的真实创建仍由 Provider 写入门禁和宿主确认契约控制。

## 测试环境

| 项目 | 值 |
|---|---|
| CPClaw 后端 | `http://127.0.0.1:8080` |
| 宿主 | `cpclaw-web` |
| 云枢应用 | `zlcsstcrm` |
| 云枢实体 | `sql_line` / 私海线索 |
| 真实字段 | `sql_line_name` / 线索名称 |
| 用户目标 | 我需要创建一个线索，我最近跟进了一个客户，叫奥哲 |

## 验收步骤与结果

1. 使用可信 `cpclaw-web` 宿主调用 `yunshu-skill/1.0` 的 `start` 操作。
2. CPClaw 从同步元数据中唯一定位 `zlcsstcrm/sql_line`，没有使用模拟实体或虚构 Schema。
3. 自然语言中的“叫奥哲”被安全映射为真实字段 `sql_line_name=奥哲`；当多个名称字段存在时，只有带有元数据 `metadata:primary-name` 标记的字段允许自动填充，避免凭语义猜测任意字段。
4. 返回任务状态：
   - `task.status=confirmation_required`
   - `completion.state=ready`
   - `providerContract=unverified`
   - `hostAction=request_confirmation`
5. 未调用 `save`、`submit` 或任何真实写入接口。

## 结论

- 真实字段定位、自然语言字段映射、草稿生成和确认前写入阻断通过。
- 该用例不证明确认后的真实创建可在生产开放；生产写入仍要求宿主可信确认、Provider 契约开关、幂等、回执和 UNKNOWN 对账全部通过。
- 不把“确认计划已生成”表述为“业务记录已创建”。
