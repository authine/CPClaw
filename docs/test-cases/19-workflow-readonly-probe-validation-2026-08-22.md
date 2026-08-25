# 流程中心只读接口探查与验证报告

## 范围与安全边界

仅使用后端持久化的普通用户云枢凭据，探查流程查询接口；不读取浏览器 Cookie/localStorage，不调用审批、驳回、转交、撤回、终止等写接口。

## 真实验证结果

| 场景 | 真实结果 | 结论 |
| --- | --- | --- |
| 我的待办 | `search_workitems` 候选，`data.totalElements=2`，返回 2 条记录 | 通过 |
| 我的已办 | `list_finished_workitems`，`data.totalElements=427` | 通过 |
| 我发起的流程 | `list_my_instances`，`data.totalElements=0` | 通过 |
| 流程实例列表 | `list_instances`，只读返回真实分页结构 | 通过 |
| 实例详情/节点 | 需要实例 ID，未发送猜测请求 | 安全跳过 |
| 同意第一条待办 | 生成 high-risk confirmation，`confirmationId` 非空 | 未确认，不写入 |

真实响应契约已写入 `cloudpivot_api_endpoints.raw_json`：包括 method、path、请求键名、`data.totalElements`、`data.content`、首条字段集合和响应形状。元数据重同步会保留已验证流程契约。

## 自动化回归

- `mvn test`：36 项通过。
- `CpClawApiTests`：覆盖探查、待办/已办查询和流程处理确认门禁。
- `npm run build`：通过；仅有第三方 PURE 注释和 chunk 体积提示。

## 限制

流程写接口尚未探查，也不会在用户明确确认前调用。实例详情和节点查询需要从已返回列表中取得实例 ID，后续应在用户明确查看某条流程时执行定点只读查询。
