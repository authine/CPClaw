# 云枢洞察空字段兼容性验证

## 问题

云枢运行态记录的 `data` 对象可能包含值为 `null` 的字段。洞察报告在生成记录摘要时使用 `Collectors.toMap` 收集字段，未过滤空值会触发 `NullPointerException`，界面错误地显示为“云枢运行态查询失败”。

## 修复

`YunshuInsightReportService.recordSummary` 现在跳过空键和空值字段后再生成摘要。运行态查询成功但洞察后处理失败的场景不再因为空字段中断。

## 验证

| 用例 | 预期 | 结果 |
|---|---|---|
| 记录包含 `data.createdTime = null` | 洞察报告正常生成 | 通过 |
| 记录包含多个空字段 | 不抛出 `NullPointerException` | 通过 |
| 既有洞察回归 | 原有多对象、个人范围、筛选降级用例继续通过 | 通过 |

自动化测试：`InsightReportServiceTests`，共 6 条，全部通过。

真实 MySQL/云枢复测：输入“分析系统商机情况”成功返回 240 条商机、5986.94 万元金额汇总和洞察报告，耗时约 66 秒；未再出现 `NullPointerException`。同时，洞察后处理异常已与运行态查询异常分离记录，分别使用 `insight_report_failed` 和 `runtime_query_failed` 状态。
