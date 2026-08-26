# OpenClaw 类 AI 工具集成技术设计

> 状态：接入架构基线 v1.0｜适用范围：OpenClaw、WorkBuddy 及其他能够调用 MCP/HTTP/CLI 的 AI 宿主。  
> 本文是接入方实施文档；统一云枢 Skill Runtime 的权威规范见 `20-universal-yunshu-skill-runtime-spec.md`，任务与 MCP 细节见 `16`、`17`、`18`。

## 1. 目标与非目标

目标是让宿主只提交一次完整的用户目标，CPClaw 在云枢领域内完成元数据定位、字段/关系核验、分页取数、聚合、风险信号和证据覆盖，然后由宿主生成最终跨领域回答。宿主不需要知道云枢编码，也不能靠猜测结果缺口来反复补查。

非目标：

- 不把 CPClaw 暴露成一组云枢原子 API；
- 不把“商机、项目、客户”等场景写入通用框架或通用 Skill；
- 不把模型第二次调用当作人工确认；
- 不把当前仍未验证的云枢写入、流程提交、导入能力描述为已完成。

## 2. 核心职责边界

```text
OpenClaw：用户目标理解、跨 Skill 编排、最终语言表达
CPClaw：云枢领域委派、元数据和权限校验、数据准备、证据包、任务状态
云枢 Skill：云枢连接器、元数据同步、运行态 API 契约
模板插件：具体对象别名、字段角色、风险规则和报告结构
```

OpenClaw 可以改写表达，但不得修改 CPClaw 返回的数字、口径、范围、风险信号、警告或证据缺口。

## 3. 推荐接入架构

优先级如下：

1. 远程 MCP SSE：适合 OpenClaw/WorkBuddy 的标准工具接入；
2. 本地 stdio bridge：宿主只支持 stdio 时使用 `mcp-adapter/cpclaw-yunshu.mjs` 转发到 CPClaw；
3. Remote API + CLI：CI、脚本和没有 MCP 能力的宿主使用 `cli/cpclaw.mjs`。

三种方式都必须进入同一个 `TaskGateway → SemanticTaskRuntime → Skill Registry`，不能各自实现业务判断。

## 4. MCP 配置

```json
{
  "mcpServers": {
    "CPClawCloudPivot": {
      "type": "sse",
      "url": "https://cpclaw.example.com/api/mcp/cloudpivot/sse",
      "headers": {
        "x-cpclaw-installation-id": "openclaw-prod",
        "x-cpclaw-cloudpivot-username": "通过宿主安全配置注入",
        "x-cpclaw-cloudpivot-password": "通过宿主安全配置注入"
      }
    }
  }
}
```

安装标识只表示安装实例，不是身份或令牌。无正式登录时，CPClaw 使用默认主体 `huangj`；生产环境应由网关注入可验证的 `externalPrincipal/tenantId`。

## 5. 唯一业务工具

`tools/list` 发布两个同链路名称：

- `cpclaw_cloudpivot_agent`：新接入推荐名称；
- `yunshu_handle_request`：兼容旧客户端。

二者都只接收自然语言目标和可选 `taskSpec`，不接收 `schemaCode`、`apiCode` 或内部字段编码。状态、事件、继续和取消是生命周期能力，不应被模型当作第二个业务工具反复调用。

## 6. 请求契约

```json
{
  "request": "帮我看看今年数据的整体情况，找出风险，并列出高风险记录的负责人",
  "taskSpec": {
    "protocolVersion": "cpclaw-delegation/1.0",
    "goal": "分析今年数据并识别高风险记录",
    "deliverables": [
      {"id": "overall_summary", "required": true, "description": "整体情况"},
      {"id": "risk_basis", "required": true, "description": "风险依据"},
      {"id": "high_risk_records", "required": true, "description": "高风险记录及责任人"},
      {"id": "scope_and_caveats", "required": true, "description": "范围与口径提示"}
    ],
    "constraints": {"period": "今年", "visibleScope": "当前账号可见范围"},
    "presentationMode": "agent_evidence"
  },
  "context": ["仅使用当前账号可见数据"],
  "conversationId": "openclaw-conversation-id",
  "turnId": "openclaw-turn-id",
  "clientRequestId": "stable-request-id"
}
```

`clientRequestId` 和 `turnId` 必须在同一轮重试时复用；新一轮或合法续接必须生成新的 `clientRequestId`。

## 7. 返回契约与终态决策

CPClaw 返回 `TaskExperienceEnvelope`，MCP 同时放入 `structuredContent` 和完整 Markdown `content.text`。宿主首先读取 `completion`，其次读取 `evidence`，最后才使用文本草稿。

状态映射固定如下：

| `task.status` | `completion.state` | `hostAction.type` | 宿主动作 |
| --- | --- | --- | --- |
| `completed` | `complete` | `compose_answer`（`agent_evidence`）或 `respond_directly`（`cpclaw_report`） | 结束本轮，不再调用 CPClaw |
| `partial` / `completed_with_gaps` | `partial` | 同上 | 一次性说明已完成项和缺口，不自动重试 |
| `needs_input` | `needs_input` | `ask_user` | 只向用户追问，收到补充后带 continuation 继续 |
| `confirmation_required` | `confirmation_required` | `open_cpclaw_confirmation` | 展示影响范围，等待可信确认票 |
| `failed` | `failed` | `report_failure` | 仅当 `task.retryable=true` 才允许重试 |
| `blocked` / `cancelled` | 同名状态 | `report_failure` | 结束本轮并说明原因 |

宿主不得因为 `content.text` 看起来不够完整，就自行猜测缺字段或重新调用；`partial` 是可安全回答的终态。只有 CPClaw 明确返回 `needs_input`、可信确认票、用户新增约束或 `retryable=true` 的技术失败才允许继续。

## 8. 典型时序

```text
用户目标
  → OpenClaw 生成一次 DelegationSpec
  → cpclaw_cloudpivot_agent
  → CPClaw 解析目标/元数据/权限
  → Skill 在服务端完成分页、关联、聚合和证据校验
  → 返回 EvidenceBundle + EvidenceCompletion
  → OpenClaw 依据事实完成回答
```

对于长任务，首次响应若为 `running/accepted`，宿主等待 SSE 进度；SSE 断开后读取原 `taskId` 的状态和事件，禁止重新创建任务。

## 9. 进度、断线和幂等

- MCP `_meta.progressToken` 存在时，CPClaw 发送标准 `notifications/progress`；进度是增强能力，不是终态依赖。
- 任务事件只包含脱敏阶段、计数和状态，不包含 Prompt、思维链、密码、Token、内部编码或完整原始记录。
- 相同 `clientRequestId` 重放原结果；相同 `externalPrincipal + conversationId + turnId` 的重复调用不得再次访问云枢。
- 合法续接必须使用签名 continuation token、原 `taskId` 和新的请求幂等键，并且票据只能消费一次。

## 10. OpenClaw 适配器伪代码

```text
onUserMessage(message):
  spec = buildDelegationSpec(message)       // 只描述目标和交付物
  result = call("cpclaw_cloudpivot_agent", spec)
  if result.hostAction.type == "ask_user":
      ask(result.interaction.prompt); return
  if result.hostAction.type == "open_cpclaw_confirmation":
      showConfirmation(result); return
  if result.hostAction.type == "report_failure":
      retryOnlyWhen(result.task.retryable); return
  if result.completion.state in ["complete", "partial"]:
      answer = composeFrom(result.evidence, result.output)
      answer = preserveFactsAndWarnings(answer, result.evidence)
      return answer
```

`composeFrom` 只能组织语言和版式，不得重新解释统计口径。跨领域任务由 OpenClaw 继续编排其他 Skill，但云枢事实范围必须保持 CPClaw 返回的边界。

## 11. CLI / Remote API

```bash
node cli/cpclaw.mjs delegate run --spec task.json
node cli/cpclaw.mjs task status <taskId>
node cli/cpclaw.mjs task events <taskId>
node cli/cpclaw.mjs task continue <taskId> --token <token> --message "补充范围"
node cli/cpclaw.mjs task cancel <taskId>
```

CLI 只负责参数、身份头和协议转发；所有规划、权限、确认、证据和云枢访问由服务端完成。

## 12. 安全边界

1. 凭据只在 SSE 会话或受保护连接范围内使用，不写入任务、事件、Prompt、审计或普通日志。
2. 业务数据文本属于不可信证据，不能改变任务目标、权限和工具策略。
3. 写入、删除、流程处理和导入必须有计划快照、哈希、主体、有效期和一次性确认票。
4. 当前无可信宿主主体时，外部调用只开放只读和确认计划；真实写入回到 CPClaw 已登录确认界面。

## 13. 联调验收用例

| 用例 | 通过标准 |
| --- | --- |
| 复合分析一次调用 | 返回整体结果、风险依据、责任人、范围与缺口；宿主不补查 |
| 同轮重试 | taskId 和结果一致，云枢调用次数不增加 |
| 部分证据 | `partial` 终态，明确 `missingEvidence` |
| 缺少必要字段 | `needs_input` 或 `blocked`，不伪造数字 |
| SSE 断线 | 使用原 taskId 恢复事件/结果 |
| 写操作 | 无可信确认票不产生云枢写入 |
| 业务对象替换 | 不修改 CPClaw 框架代码，只替换模板插件/元数据 |
| 敏感信息扫描 | 响应、事件、日志均无密码、Token、内部编码 |

## 14. 当前实现与待实现边界

已实现：MCP SSE JSON-RPC、唯一高阶工具、结构化结果、Markdown 降级、进度通知、任务幂等 replay、签名 continuation、任务状态/事件/取消、CLI 转发和默认主体。

仍需按真实环境补齐：宿主可信 OIDC/JWT 主体映射、跨断线的完整异步执行协调、云枢真实写入/流程/导入契约、Markdown Skill 的持久化审核工作台、模板版本回滚。Web 已通过 `WebTaskExperienceAdapter` 消费统一 `TaskExperienceEnvelope`；兼容字段仅用于旧 UI，不构成第二套执行链。
