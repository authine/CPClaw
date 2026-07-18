# Graphify 全应用元数据知识图谱

## 1. 目标

CPClaw 将目标云枢环境中已经同步的全部应用一次性投影为知识图谱，不设置 CRM 试点白名单。MySQL 继续保存权威元数据和可原子切换的图快照；Graphify 使用 v8 Node-Link JSON 兼容格式作为本地导出与后续语义增强入口。

知识图谱只保存设计态元数据，不保存客户、商机、合同等运行态业务记录。Agent 最终回答业务数据时仍必须调用真实云枢运行态接口。

## 2. 全量覆盖范围

节点类型：

- `application`：云枢应用。
- `entity`：业务模型、目录、报表、页面等云枢实体定义。
- `data_item`：实体数据项和关联表单字段。
- `api_endpoint`：已核验的云枢运行态 API 能力。

边类型：

- `APP_CONTAINS_ENTITY`
- `ENTITY_HAS_DATA_ITEM`
- `DATA_ITEM_REFERENCES_ENTITY`
- `ENTITY_RELATES_TO_ENTITY`
- `API_OPERATES_ON_ENTITY`

云枢明确返回的应用归属、字段和实体关系使用 `confidence=EXTRACTED`、`weight=1.0`。通用 API 根据 `applicableObjectType` 扩展到实体的能力边使用 `confidence=INFERRED`、`weight=0.7`，不会冒充云枢显式关系。

## 3. 稳定身份与版本切换

图节点不使用每次同步变化的数据库 UUID 作为图身份，而使用可重建稳定键：

```text
app:{appCode}
entity:{appCode}:{entityCode}
data_item:{appCode}:{entityCode}:{dataItemCode}
api_endpoint:{apiCode}
```

每次构图先写入新的 `metadata_graph_snapshots`、`metadata_graph_nodes` 和 `metadata_graph_edges` 快照。只有节点、边和应用覆盖率全部计算完成后，新快照才切换为 `ACTIVE`，之前的快照改为 `STALE`。默认保留最近两个快照，用于失败回退和对比。

全部接口按当前 `ACTIVE` 快照读取，避免同步过程中出现半张图。

## 4. Graphify 兼容导出

Graphify 官方项目面向文件语料，核心持久格式是 NetworkX Node-Link JSON。CPClaw 直接生成兼容结构：

```json
{
  "directed": true,
  "multigraph": false,
  "graph": {
    "provider": "graphify-v8-compatible",
    "schema_version": "graphify-v8-node-link"
  },
  "nodes": [],
  "links": []
}
```

默认导出到 `./storage/graphify-out/graph.json`，写入使用临时文件和原子替换。部署环境可以通过 `CPCLAW_GRAPHIFY_OUTPUT_DIR` 指定持久卷。该文件可以交给 Graphify、NetworkX、Neo4j 转换工具或离线分析任务使用；线上图谱查询不依赖 Python、Claude 或外部图数据库可用性。

## 5. API

- `GET /api/metadata/graph/overview`：图状态、节点/边类型统计、未解析边、应用覆盖率和导出路径。
- `GET /api/metadata/graph/neighborhood`：按稳定节点 ID 或 `objectType + objectId` 查询 1-3 跳邻域。
- `GET /api/metadata/graph/export`：直接返回 Graphify Node-Link JSON。
- `POST /api/metadata/graph/rebuild`：不重新访问云枢，基于当前 MySQL 权威元数据重建全应用图快照。
- `POST /api/metadata/sync`：完成云枢元数据同步后自动构图，并返回图节点、边、应用覆盖率和导出路径。

邻域查询受 `maxDepth` 和 `maxNodes` 保护，默认最多 3 跳、500 个节点，避免全图响应挤占交互请求。

## 6. 配置

```yaml
cpclaw:
  metadata:
    graphify:
      enabled: true
      rebuild-on-metadata-sync: true
      include-api-endpoints: true
      write-export: true
      output-directory: ./storage/graphify-out
      batch-size: 500
      max-depth: 3
      max-nodes: 500
      snapshot-retention: 2
```

所有参数均支持对应的 `CPCLAW_GRAPHIFY_*` 环境变量，不在业务代码中硬编码部署路径和容量上限。

## 7. 验收标准

- 应用覆盖率必须为本地权威元数据应用数的 100%，每个应用都有独立覆盖状态。
- `application`、`entity`、`data_item` 节点数必须分别与 MySQL 权威表一致。
- 所有显式实体关系必须可追溯到来源数据项；未解析引用进入 `unresolvedEdgeCount`。
- 相同元数据连续重建后节点和边的稳定键集合完全一致，不产生重复图对象。
- Graphify 导出中的节点数、边数和当前 `ACTIVE` 快照一致。
- 图谱不可用时不得影响现有 MySQL Metadata Index 和真实云枢运行态查询。
- 元数据页面在桌面、平板和移动端均可查看全局统计、应用覆盖率和对象邻域，不产生横向溢出。

## 8. 后续增强边界

Graphify 的 LLM 语义推断只能生成候选弱关系。弱关系必须记录证据、置信度和来源，不允许直接进入写操作或确定性运行态执行路径。未来扩展表单、视图、流程、Action 等节点时继续沿用稳定键、版本快照和强弱关系规则。
