# 智能体真实数据主线专项验证报告

## 1. 测试结论

本轮 QA 针对用户反馈“系统商机返回了不存在的 schemaCode，结果不真实”重新定义验收口径：CPClaw 不能再把本地 fallback 演示对象当成云枢业务数据。智能体必须沿着以下主线执行：

```text
用户自然语言
  -> Think：抽取动作、业务对象、分析维度、筛选条件
  -> Metadata：从真实云枢 Metadata Index 命中应用/模型
  -> Act：使用真实 schemaCode 调用云枢运行态接口
  -> Analyze：基于真实返回数据做统计/聚合/大模型分析
  -> Reflect：确认数据来源真实后才输出结论
```

当前结论：**真实数据主线保护已通过后端自动化验证，并已通过真实云枢环境复测。**

已验证能力：

- 明确统计：能将“系统有多少商机？”识别为计数查询，并在 mock 和真实云枢元数据下命中 `商机 / int_bu_oppor`。
- 客户统计：能将“系统有多少个客户？”识别为客户计数，并在 mock 和真实云枢元数据下命中 `客户 / crm_customer`。
- 维度分析：能将“每年的客户量情况怎么样？”识别为对象=客户、维度=年份，并在真实云枢 total=8694 时分页返回 8694 条后做按年聚合。
- 分析请求：能将“分析系统中的商机信息”识别为 `analyze_data`，先查询运行态数据，再交给模型/规则生成分析。
- 执行过程可见：`Think` 步骤展示动作、业务对象、维度、筛选、候选对象和候选编码；回答正文展示真实 `schemaCode`、运行态来源、total、原始数据摘要和结论生成方式。
- fallback 保护：在 fallback 演示环境下，“系统有多少商机？”不会返回 `system_opportunity`、演示样本或 `总计 **3** 条`，而是进入澄清/阻断，提示需要真实云枢元数据。

主要不足：

- 当前仍是 ReAct + Reflection MVP，结构化意图主要由规则和本地 Metadata Index 检索完成，尚未升级为完整模型驱动 DAG。
- 多轮上下文引用仍是安全澄清雏形，尚不能稳定绑定上一轮记录 ID。
- 当前候选排序仍是 MVP 规则打分，后续需要升级为模型驱动结构化意图、字段级图谱和可解释候选澄清。

## 2. 测试环境

| 项目 | 内容 |
| --- | --- |
| 测试时间 | 2026-06-21 23:38 +08:00；2026-06-22 00:15 +08:00 真实环境复测 |
| 测试入口 | 后端集成测试；本地 8080 API 真实云枢联调 |
| 数据库 | H2 临时内存库 |
| 真实主线模拟 | Mock `CloudPivotConnector` 返回真实风格元数据和运行态数据 |
| 真实云枢同步 | 29 个应用、922 个模型、951 条搜索文档 |
| 真实商机模型 | `商机 / int_bu_oppor` |
| 真实客户模型 | `客户 / crm_customer` |
| fallback 保护 | `cpclaw.cloudpivot.allow-metadata-fallback=true` 环境下验证不返回假业务结果 |

## 3. 用例结果

| ID | 用户输入 | 预期 | 实际意图 | 候选对象/编码 | 结论 |
| --- | --- | --- | --- | --- | --- |
| AI-REAL-001 | 系统有多少商机？ | 命中真实商机模型并返回运行态 total | `query_data` | 商机 / `int_bu_oppor` | 通过，真实 total=237 |
| AI-REAL-002 | 系统有多少个客户？ | 命中真实客户模型并返回运行态 total | `query_data` | 客户 / `crm_customer` | 通过，真实 total=8694 |
| AI-REAL-003 | 每年的客户量情况怎么样？ | 识别年份维度并按年聚合全量客户数据 | `analyze_data` | 客户 / `crm_customer` | 通过，真实 total=8694、returned=8694 |
| AI-REAL-004 | 分析系统中的商机信息 | 查询商机数据后生成分析 | `analyze_data` | 商机 / `int_bu_oppor` | 通过，真实 total=237、returned=237 |
| AI-GUARD-001 | fallback 环境：系统有多少商机？ | 不返回演示 schema 和假总数 | `clarify_intent` | 未匹配真实业务对象 | 通过 |
| AI-RISK-001 | 给第一条商机写一条跟进记录 | 缺少跟进内容时安全澄清 | `clarify_intent` | 商机 / `int_bu_oppor` | 通过 |

## 4. 关键断言

### 真实商机计数

```text
用户输入：系统有多少商机？
候选对象：商机
schemaCode：int_bu_oppor
运行态 total：237
回答包含：schemaCode=`int_bu_oppor`、北京菲斯曼供热、总计 **237** 条
回答不包含：system_opportunity、local-fallback、演示编码
```

### 真实客户计数

```text
用户输入：系统有多少个客户？
候选对象：客户
schemaCode：crm_customer
mock 运行态 total：58
真实运行态 total：8694
回答包含：schemaCode=`crm_customer`、总计 **8694** 条
回答不包含：system_customer、local-fallback、商机 total
```

### 真实客户按年分析

```text
用户输入：每年的客户量情况怎么样？
候选对象：客户
schemaCode：crm_customer
真实运行态 total：8694
真实运行态 returned：8694
回答包含：schemaCode=`crm_customer`、按年客户量分析、原始数据摘要
回答不包含：system_customer、local-fallback、样本冒充全量
```

### fallback 保护

```text
用户输入：系统有多少商机？
环境：allow-metadata-fallback=true + example.local
预期：不返回业务答案
实际：intent=clarify_intent
回答包含：真实云枢元数据 / 不会用本地演示数据返回业务结果
回答不包含：system_opportunity、华东制造业数字化项目、总计 **3** 条
```

## 5. 自动化结果

命令：

```powershell
cd E:\CPClaw\server
$env:JAVA_HOME='C:\Users\huang\.jdks\openjdk-26.0.1'
& 'C:\Users\huang\.m2\wrapper\dists\apache-maven-3.9.11\a2d47e15\bin\mvn.cmd' test
```

结果：

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

真实云枢复测摘要：

```text
元数据同步：appCount=29, entityCount=922, searchDocumentCount=951
系统有多少商机？ -> schemaCode=int_bu_oppor, total=237, returned=1
系统有多少个客户？ -> schemaCode=crm_customer, total=8694, returned=1
分析系统中的商机信息 -> schemaCode=int_bu_oppor, total=237, returned=237
每年的客户量情况怎么样？ -> schemaCode=crm_customer, total=8694, returned=8694
```

## 6. QA 建议

1. 保留真实云枢核心问法作为每轮回归：商机计数、客户计数、商机分析、客户年度分析。
2. 如果真实元数据中存在多个商机/客户候选，继续增强候选排序和澄清选择，不要默认猜测错误应用。
3. 下一阶段补结构化意图模型和字段级图谱，支持按负责人、阶段、时间范围、金额区间等更复杂筛选与聚合。
4. 保留 fallback guard 自动化测试，任何让 `system_opportunity` 或 `system_customer` 再次进入业务答案的改动都应阻断合并。
