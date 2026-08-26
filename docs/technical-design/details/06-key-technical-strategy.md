# CPClaw 关键技术方案

> 文档类型：关键技术方案｜状态：当前策略基线。总体分层见 `../00-technical-blueprint.md`；云枢 Runtime 细节见 `20-universal-yunshu-skill-runtime-spec.md`。

## 1. Skill 能力隔离与云枢元数据驱动

CPClaw 平台层不规定所有 Skill 必须采用元数据驱动。平台只规定 Skill 必须声明能力、输入输出、权限、风险和生命周期。云枢 Skill 在其内部同步设计态元数据，并以实体、字段、关系、操作、系统字段分类和中文描述构成云枢运行时可验证的语义边界。模型和模板只能在对应 Skill 的边界内选择目标；任何未验证能力或业务对象关键词分支都不可以进入 CPClaw 框架执行路径。

## 2. 统一 Runtime 与宿主一致性

Web、MCP、CLI/Remote API 通过 Host Adapter 转换请求，然后统一调用：

`TaskGateway → SemanticTaskRuntime → SkillRegistry → YunshuMcpTaskExecutor`

Runtime 持久化任务、事件、幂等键、父子续接和证据完成度，输出 `TaskExperienceEnvelope`。宿主只能展示或组织语言，不能自行补查、重新执行或改变返回事实。

## 3. 受约束规划与安全执行

规划分为 `UNDERSTAND / DISCOVER / PLAN / VALIDATE / EXECUTE / ANALYZE / PROCESS / COMPLETE`。每一步都受元数据、权限、API 白名单、风险等级、确认快照和审计约束。写风险操作必须绑定计划哈希、记录引用、主体、有效期和一次性确认；未验证的连接器只允许澄清或只读结果。

## 4. 查询、分析和证据

确定性代码计算事实指标，模型负责受限规划、解释和流式表达。结果必须携带数据来源、范围、口径、证据和缺口；系统字段默认不作为业务洞察维度。向量召回可改善候选排序，但不能产生可执行编码或替代 MySQL 元数据索引。

## 5. 模板插件治理

业务场景由审核通过的声明式模板插件表达，模板受 Manifest、白名单算子、管理员审批、版本和审计约束。默认云枢 Runtime 不加载场景模板，不维护领域词、固定字段映射或风险规则。详见 `15-analysis-template-plugin-contract.md`。

## 6. 安全、配置与持久化

凭据采用 AES-GCM 密文或安全引用，密钥由部署环境稳定提供；不得入库明文、日志、MCP 载荷或 Git。MySQL/Flyway 是持久化真相源，H2 只在测试使用。详见 `04-data-model.md`、`05-security.md`、`12-persistent-database-runtime.md`。

## 7. 对外集成策略

推荐 OpenClaw 类宿主通过 MCP 调用唯一高阶工具 `yunshu_handle_request`，并加载行为说明文档。CLI 是无 MCP 宿主和自动化场景的同 Runtime 转发通道，不含云枢业务判断。详见 `19-openclaw-class-ai-tool-integration.md`。

## 8. 尚未完成的生产策略

可信 OIDC/JWT、真实写入/流程/导入契约、后台队列断线恢复、模板版本回滚和跨宿主网络 E2E 尚未完成。它们属于发布门禁，不应由重试、浏览器兜底或模型猜测替代。
