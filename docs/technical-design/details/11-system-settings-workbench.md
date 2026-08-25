# 系统设置工作台补全技术方案

**状态：已确认实施。**

## 1. 前端结构

`SettingsView.vue` 是唯一工作台壳层，使用 `section` 查询参数保存当前菜单。云枢连接拆为 `user-cloudpivot` 与 `admin-cloudpivot`；元数据拆为 `metadata-sync` 与 `metadata-browser`。旧 `/metadata` 路由重定向到 `/settings?section=metadata-browser`。

元数据功能复用 `metadataApi.ts` 与现有 `MetadataController`，不得复制同步、搜索和图谱业务逻辑。同步面板调用 `POST /api/metadata/sync`；浏览面板调用现有模型、应用、搜索和图谱接口。管理员凭据未完整保存时，前端禁用同步，后端既有同步校验仍是最终边界。

## 2. 模型连通性测试

新增模型预检使用 `POST /api/settings/models/test`，请求体仅包含 `modelName`、`modelApiBaseUrl` 与 `modelApiKey`。该接口不创建 `ModelConfig`、不写入凭据表、不回显请求体，只把最小探测结果返回页面。前端将通过状态绑定到连接字段指纹；名称、地址或 API Key 任一变化都会使状态失效并禁用保存。

已保存模型复测继续使用 `POST /api/settings/models/{modelConfigId}/test`。服务从 `ModelConfigRepository` 和 `CredentialService` 读取配置及加密 API Key，页面不再次传递已保存密钥。

`OpenAiCompatibleModelGateway.testModel` 对 OpenAI-compatible `chat/completions` 发起最小非流式请求：`max_tokens=1`、`temperature=0`、短提示词。2xx 且 `choices[0].message.content` 非空才视为成功。接口返回 `{ success, message, latencyMs }`，异常、响应正文与密钥均不得回传或写入日志。

连接测试使用短超时、仅允许 http/https URL 且拒绝空主机、userinfo、环回和私网目标；本地模拟地址不作为真实供应商可用的证明。测试成功仅验证网络、凭据、模型名和基础对话响应，不验证质量、思考模式或复杂 Agent 流程。

## 3. 验证

- 后端：已有设置、元数据同步/浏览回归；覆盖预检不落库、模型不存在、禁用/缺密钥、失败与成功响应。
- 前端：构建；菜单 URL 恢复；管理员未配置禁用同步；新增模型预检、字段变更失效、保存门禁及已保存模型复测反馈；深色与窄屏验证。

## 4. 安全控制台

安全菜单保留在 `SettingsView.vue` 中，以 `SettingsResponse` 的脱敏布尔状态和模型摘要构成配置状态，不额外请求或回显任何密码、API Key。个人云枢状态只表示个人账号/密码和管理员环境是否就绪，云枢地址统一由管理员云枢环境维护；个人云枢、管理员云枢和 Agent 模型状态行分别跳转到对应设置子菜单；日志分析为独立菜单，旧 `/audit` 仅保留给技术详情兼容。

写操作确认与审计追溯是系统规则而非可切换开关，页面只能说明其约束，不能伪装成可关闭的配置项。数据库、向量库等部署参数不在该 UI 暴露编辑能力。

## 5. 元数据两级浏览

`MetadataBrowserPanel.vue` 将本地 `MetadataModelResponse.apps` 作为一级应用目录数据源。目录卡只显示应用名、编码以及汇总后的实体、字段、关联、API 数量；点击后设置当前 `selectedAppId` 并进入详情状态。

详情页以当前应用为唯一范围：顶部提供返回目录、`el-select` 应用切换器和资源统计；实体列表只包含当前应用对象，字段、关联、API 页签跟随选中实体变化。搜索结果若定位到某个实体，可切换到其所属应用并选中对应实体。既有 `/api/metadata/model` 接口已返回完整层级，因此不新增后端 API。

## 5. 版本错配错误处理

设置页不得把 Spring 的 `No static resource` 等框架原文直接展示给用户，也不得以消息浮层和页面警告重复显示同一请求失败。模型测试或日志分析接口缺失时，统一提示“当前后端版本未包含[对应能力]接口，需要以持久化数据库配置启动新版后端”，同时保留原始错误仅供服务端日志与开发排查。该提示不替代实际后端升级；日志分析请求失败时，页面不得将默认的零值指标解释为有效统计。

## 6. 日志分析数据与 API

新增 `agent_model_calls` 持久化明细，记录 `agent_run_id`、模型、调用类型、脱敏输入/输出摘要、状态、Token、耗时和时间；AgentRun 同步保存本轮汇总，避免从消息 JSON 临时推导统计。`GET /api/audit/analytics` 提供时间范围、状态、动态意图、模型筛选和分页，返回 summary、items 与当前已记录的 `intentOptions`，分页上限 100。意图不是固定枚举：模型可以生成新的业务意图文本，后端只对内部执行能力做白名单/权限校验；前端筛选选项必须从后端实际记录动态生成。Provider 未返回 usage 时 Token 字段为 null，前端显示“—”，不估算。

旧 `/api/audit/agent-runs/{id}` 继续提供技术详情；日志分析不回显 API Key、密码、Cookie、完整业务正文或未脱敏模型响应。

## 7. 设置工作台视觉令牌与布局边界

`SettingsView.vue` 在工作台根节点维护设置页视觉令牌（页面背景、表面、分隔线、正文/辅助/弱化文本、品牌色、圆角和间距）。子面板只消费 `--settings-*` 变量，不自行定义相近但不同的灰阶、圆角或间距。

工作台壳层必须与 `ChatView.vue` 对齐：侧栏和右侧工具栏共享 56px 头部高度；品牌标记为 32px 方形、工作台标题使用 14px/600；设置专属差异只允许出现在内容区标题和菜单项，不得通过放大全局头部制造另一套页面比例。

设置内容区以 `--settings-content-width: 980px` 约束阅读和编辑宽度；说明性区域、表单卡片、同步卡片和安全面板均遵守此上限。标准表单为单列，字段间距 20px，卡片内边距 24px；信息提示与操作栏分别通过 `.deployment-note`、`.form-actions` 布局，前者不占用按钮区域，后者带顶部边界并允许小屏换行。`MetadataBrowserPanel` 的目录和详情可因数据浏览需要超过单列表单宽度，但仍使用同一令牌。

断点为 900px 和 620px：前者将数据详情并为单列，后者将操作区、标题状态与筛选控件改为纵向排列；不使用固定高度定位，也不依赖负边距实现对齐，防止文字增多、缩放或窄屏时重叠。

## 8. 元数据查看异步读取状态

`MetadataBrowserPanel.vue` 首次加载并行读取 `GET /api/metadata/model`、`GET /api/metadata/graph/overview` 和 `GET /api/metadata/sync-logs`。模型与图谱为浏览页的必要数据；同步日志是展示增强，单独读取失败不得阻断已持久化元数据浏览，但页面须显示“同步记录暂不可用”。

面板以 `hasLoaded` 区分首次读取和刷新：首次读取期间只渲染 `metadata-loading` 紧凑工作区载入卡，包含三项读取范围和四项轻量统计占位，不渲染假搜索区或假应用目录；数据就绪后目录/详情以短暂淡入出现。刷新期间保留既有 `model`、图谱统计和应用选择，只设置刷新按钮加载状态。最近一次同步时间取 `MetadataSyncLogOverview.latestSuccessfulAt`，经本地化格式化后展示，禁止由应用/实体时间猜测。该方案不新增接口、不触碰同步事务或凭据，仅复用已有脱敏只读同步日志接口。
