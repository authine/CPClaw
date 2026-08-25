# 流程中心元数据与接口契约登记

## 事实依据

2026-08-22 对 `http://pmotest.authine.com:10086/workflow-center/my-unfinished-workitem` 做了只读探查。页面因未登录跳转 `/login`，未读取账号密码，也未执行任何流程动作。公开前端静态资源登记了以下候选路径：

- 查询：`list_workitems`、`list_finished_workitems`、`list_my_instances`、`list_instances`、`get_instance_baseinfo`、`list_workflow_instance_activity`。
- 动作：`finish_instance`、`reject_workItem`、`forward_workItem`、`abort_instance`、`delete_instance` 等。

静态资源不能证明当前租户的 HTTP 方法、请求体、分页字段、权限、意见规则或返回结构。

## 本地元数据增补

元数据同步时持久化独立应用 `workflow_center`，并登记以下对象：

| 对象编码 | 业务含义 |
| --- | --- |
| `my_pending_workitems` | 我的待办 |
| `my_finished_workitems` | 我的已办 |
| `my_started_workflows` | 我发起的流程 |
| `workflow_instance` | 流程实例 |
| `workflow_workitem` | 流程工作项 |
| `workflow_activity` | 流程节点 |

查询 API 作为低风险、未验证候选登记；审批、驳回、转交、撤回/终止等动作统一标记高风险并要求确认。所有流程候选的 `rawJson` 均记录 `verified=false` 和 `requiresAuthenticatedContract=true`。

## Agent 边界

包含“待办、已办、我发起、流程实例、工作项、节点”的请求进入流程中心能力门；包含“同意、驳回、转交、加签、撤回、终止”的请求不得直接调用 HTTP。完成登录后的只读契约探查、权限校验和状态回查后，写动作才可生成确认计划；用户确认前不得产生真实写入。

`COMPLETED`、`Processing` 等流程生命周期值只用于系统状态判断，不作为业务阶段或经营分析维度。

## 受控只读探查方案（2026-08-22）

流程中心接口探查由后端 `WorkflowCenterService` 触发，使用系统设置中加密保存的普通用户云枢凭据；不读取浏览器 Cookie、localStorage 或页面登录态。探查器只允许以下六个查询候选：待办、已办、我发起、流程实例列表、实例详情、实例节点。前四类列表接口最多尝试四种最小请求变体（GET/POST + `page/size` 或 `pageNo/pageSize`，页面大小为 1）；实例详情和节点接口需要实例 ID，不发送猜测请求。

成功结果写入 `cloudpivot_api_endpoints.raw_json`，包含真实 method、path、请求键名、顶层/数据/首条记录字段、总数键名、响应形状和 `verifiedAt`。元数据同步会保留已验证工作流契约，避免重新同步覆盖真实方法和字段。新增 `POST /api/metadata/workflow/probe` 供管理员显式触发；探查失败不解锁流程查询能力。

对话查询仅在对应 endpoint `verified=true` 时读取真实流程数据；没有 total 字段时只报告当前页数量，不冒充总数。审批、驳回、转交、撤回、终止等写候选完全不参与探查，仍必须先生成确认计划，用户明确确认前不得调用 HTTP 写接口。
