<template>
<main class="cp-page">
  <PageHeader title="云枢元数据" description="查看已同步到本地的云枢实体模型、数据项、关联关系和可执行 API 动作。" />

  <el-alert v-if="errorMessage" class="page-error" type="error" show-icon :closable="false" :title="errorMessage" />

  <section class="toolbar-band">
    <div class="toolbar-main">
      <div class="summary-grid">
        <div class="summary-item">
          <span class="summary-label">应用</span>
          <strong>{{ modelStats.appCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">实体</span>
          <strong>{{ modelStats.entityCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">数据项</span>
          <strong>{{ modelStats.dataItemCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">动作数</span>
          <strong>{{ modelStats.actionCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">API 动作</span>
          <strong>{{ modelStats.apiActionCount }}</strong>
        </div>
        <div class="summary-item">
          <span class="summary-label">图谱边</span>
          <strong>{{ modelStats.graphEdgeCount }}</strong>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button type="primary" :loading="syncing" @click="sync">
          <el-icon><Refresh /></el-icon>
          <span>初始化云枢元数据</span>
        </el-button>
      </div>
    </div>
    <el-alert
      v-if="syncResult"
      class="sync-result"
      :title="`同步完成：应用 ${syncResult.appCount} 个，实体 ${syncResult.entityCount} 个，数据项 ${syncResult.dataItemCount} 个，图节点 ${syncResult.graphNodeCount} 个，图边 ${syncResult.graphEdgeCount} 条`"
      type="success"
      show-icon
    />
  </section>

  <section class="graph-status-band" :class="{ 'graph-status-band--warning': graphOverview?.unresolvedEdgeCount }">
    <div>
      <span class="status-dot" :class="{ active: graphOverview?.status === 'ACTIVE' }" />
      <strong>全应用知识图谱</strong>
      <span>{{ graphOverview?.coveredApplicationCount || 0 }}/{{ graphOverview?.applicationCount || 0 }} 个应用</span>
      <span>覆盖率 {{ formatPercent(graphOverview?.coverageRate || 0) }}</span>
    </div>
    <div class="graph-status-meta">
      <span>{{ graphOverview?.provider || '尚未构建' }}</span>
      <span v-if="graphOverview?.unresolvedEdgeCount">{{ graphOverview.unresolvedEdgeCount }} 个引用待解析</span>
      <el-button size="small" type="primary" plain @click="openGraphStructure">查看图谱结构</el-button>
      <el-button size="small" :loading="rebuildingGraph" @click="rebuildGraph">重建图谱</el-button>
    </div>
  </section>

  <section class="model-browser">
    <aside class="entity-panel">
      <div class="panel-header">
        <div>
          <h2>应用模型</h2>
          <p>{{ filteredApps.length }} 个应用 · {{ filteredEntities.length }} 个实体</p>
        </div>
        <el-segmented
          v-model="viewMode"
          class="view-mode-switch"
          :options="viewModeOptions"
          @change="rememberViewMode"
        />
      </div>
      <el-input v-model="entityKeyword" class="entity-search" clearable placeholder="搜索应用、实体名称或编码" />
      <el-scrollbar class="app-entity-list">
        <section v-for="app in filteredApps" :key="app.id" class="app-group">
          <button class="app-row" type="button" @click="toggleApp(app.id)">
            <span class="app-title">{{ app.name }}</span>
            <span class="app-code">{{ app.code }}</span>
            <span class="app-count">{{ app.entities.length }} 个实体</span>
          </button>
          <div v-show="expandedAppIds.includes(app.id)" class="entity-list">
            <button
              v-for="entity in app.entities"
              :key="entity.id"
              class="entity-row"
              :class="{ active: entity.id === selectedEntityId }"
              type="button"
              @click="selectEntity(entity.id)"
            >
              <span class="entity-name">{{ entity.name }}</span>
              <span class="entity-code">{{ entity.code }}</span>
              <span class="entity-meta">{{ entity.dataItemCount }} 字段 · {{ entity.relationCount }} 关联</span>
            </button>
          </div>
        </section>
        <el-empty v-if="!loadingModel && filteredEntities.length === 0" description="暂无匹配的应用或实体对象" />
      </el-scrollbar>
    </aside>

    <main class="detail-panel" :class="{ 'detail-panel--graph': viewMode === 'graph' }" v-loading="loadingModel">
      <template v-if="selectedEntity">
        <header class="entity-hero">
          <div class="entity-hero__main">
            <div class="detail-kicker">{{ appName(selectedEntity.appId) }} / 实体资源</div>
            <div class="entity-hero__title-row">
              <h2>{{ selectedEntity.name }}</h2>
              <el-tag class="entity-type-tag" effect="plain">{{ selectedEntity.type || 'BizModel' }}</el-tag>
            </div>
            <div class="entity-hero__identity">
              <span>实体编码</span>
              <code>{{ selectedEntity.code }}</code>
            </div>
            <p class="entity-hero__description">
              {{ selectedEntity.description || '云枢业务实体，承载业务数据、字段定义、关联关系与可执行动作。' }}
            </p>
          </div>
          <div class="entity-hero__stats" aria-label="实体统计">
            <div class="entity-stat entity-stat--blue">
              <strong>{{ selectedEntity.dataItemCount }}</strong>
              <span>数据项</span>
            </div>
            <div class="entity-stat entity-stat--green">
              <strong>{{ selectedEntity.apiActions.length }}</strong>
              <span>业务动作</span>
            </div>
          </div>
          <el-button class="entity-hero__graph-action" type="primary" plain @click="openGraphStructure">
            查看图谱结构
          </el-button>
        </header>

        <div v-if="viewMode === 'graph'" class="graph-mode-banner">
          <div>
            <span class="graph-mode-banner__eyebrow">图谱查看模式</span>
            <strong>以实体为中心查看属性、关联关系与业务动作</strong>
          </div>
          <span>点击左侧实体切换中心节点</span>
        </div>

        <el-tabs v-model="activeTab" class="metadata-tabs">
          <el-tab-pane label="数据项" name="fields">
            <div class="detail-filter-row">
              <el-input v-model="detailKeyword" clearable size="small" placeholder="筛选当前实体字段" />
              <span>{{ filteredDataItems.length }} / {{ selectedEntity.dataItems.length }}</span>
            </div>
            <el-table :data="filteredDataItems" height="420" empty-text="暂无匹配字段">
              <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
              <el-table-column prop="code" label="编码" min-width="180" show-overflow-tooltip />
              <el-table-column prop="dataType" label="类型" width="130" show-overflow-tooltip />
              <el-table-column label="属性" width="150">
                <template #default="{ row }">
                  <div class="tag-line">
                    <el-tag v-if="row.required" size="small" type="danger">必填</el-tag>
                    <el-tag v-if="row.reference" size="small" type="warning">关联表单</el-tag>
                    <span v-if="!row.required && !row.reference" class="muted">普通字段</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="关联对象" min-width="160" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.referenceEntityName || row.referenceEntityCode || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="关联关系" name="relations">
            <div class="detail-filter-row">
              <el-input v-model="detailKeyword" clearable size="small" placeholder="筛选当前实体关联关系" />
              <span>{{ filteredRelations.length }} / {{ selectedEntity.relations.length }}</span>
            </div>
            <el-table :data="filteredRelations" height="420" empty-text="暂无匹配关联关系">
              <el-table-column prop="relationName" label="关系名称" min-width="170" show-overflow-tooltip />
              <el-table-column prop="relationType" label="关系类型" width="140" show-overflow-tooltip />
              <el-table-column label="来源实体" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sourceEntityName }} ({{ row.sourceEntityCode }})</template>
              </el-table-column>
              <el-table-column label="关联字段" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.sourceDataItemName || '-' }} <span v-if="row.sourceDataItemCode">({{ row.sourceDataItemCode }})</span></template>
              </el-table-column>
              <el-table-column label="目标实体" min-width="170" show-overflow-tooltip>
                <template #default="{ row }">{{ row.targetEntityName }} ({{ row.targetEntityCode }})</template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="API 动作" name="actions">
            <div class="detail-filter-row">
              <el-input v-model="detailKeyword" clearable size="small" placeholder="筛选当前实体 API 动作" />
              <span>{{ filteredActions.length }} / {{ selectedEntity.apiActions.length }}</span>
            </div>
            <el-table :data="filteredActions" height="420" empty-text="暂无匹配 API 动作">
              <el-table-column prop="name" label="动作" min-width="170" show-overflow-tooltip />
              <el-table-column prop="apiCode" label="编码" min-width="170" show-overflow-tooltip />
              <el-table-column prop="operationType" label="能力类型" width="150" show-overflow-tooltip />
              <el-table-column label="方法与路径" min-width="280" show-overflow-tooltip>
                <template #default="{ row }">
                  <span class="method">{{ row.method }}</span>
                  <span>{{ row.path }}</span>
                </template>
              </el-table-column>
              <el-table-column label="风险" width="150">
                <template #default="{ row }">
                  <el-tag :type="row.requiresConfirmation ? 'danger' : 'success'" size="small">
                    {{ row.requiresConfirmation ? '需确认' : row.riskLevel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="dataScope" label="数据能力" min-width="260" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="图谱结构" name="graph">
            <div class="graph-structure-summary" v-if="graphOverview">
              <div class="graph-structure-summary__header">
                <div>
                  <span class="graph-structure-summary__eyebrow">GRAPH METADATA</span>
                  <strong>全应用图谱结构</strong>
                  <p>展示当前图谱快照中的节点类型、关系类型与覆盖范围。</p>
                </div>
                <el-tag :type="graphOverview.status === 'ACTIVE' ? 'success' : 'warning'" effect="plain">
                  {{ graphOverview.status === 'ACTIVE' ? '已构建' : graphOverview.status || '待构建' }}
                </el-tag>
              </div>
              <div class="graph-structure-metrics">
                <div><span>节点总数</span><strong>{{ graphOverview.nodeCount }}</strong></div>
                <div><span>关系边</span><strong>{{ graphOverview.edgeCount }}</strong></div>
                <div><span>覆盖应用</span><strong>{{ graphOverview.coveredApplicationCount }}/{{ graphOverview.applicationCount }}</strong></div>
                <div><span>待解析边</span><strong>{{ graphOverview.unresolvedEdgeCount }}</strong></div>
              </div>
              <div class="graph-structure-columns">
                <section>
                  <div class="graph-structure-columns__title">节点类型</div>
                  <div v-for="item in graphNodeTypeRows" :key="item.type" class="graph-type-row">
                    <span>{{ graphNodeTypeLabel(item.type) }}</span><strong>{{ item.count }}</strong>
                  </div>
                </section>
                <section>
                  <div class="graph-structure-columns__title">关系类型</div>
                  <div v-for="item in graphEdgeTypeRows" :key="item.type" class="graph-type-row">
                    <span>{{ graphEdgeTypeLabel(item.type) }}</span><strong>{{ item.count }}</strong>
                  </div>
                </section>
              </div>
            </div>
            <div v-if="graphNeighborhood" class="entity-graph-canvas">
              <div class="entity-graph-canvas__header">
                <div>
                  <strong>实体关系图</strong>
                  <span>节点展示实体摘要，连线表示当前实体的一跳关系。</span>
                </div>
                <el-tag size="small" effect="plain">{{ graphCanvasNodes.length }} 个节点</el-tag>
              </div>
              <div class="entity-graph-canvas__stage">
                <svg class="entity-graph-canvas__edges" viewBox="0 0 1000 420" preserveAspectRatio="none" aria-hidden="true">
                  <line
                    v-for="edge in graphCanvasEdges"
                    :key="edge.id"
                    :x1="edge.source.x"
                    :y1="edge.source.y"
                    :x2="edge.target.x"
                    :y2="edge.target.y"
                    :class="['graph-edge-line', { 'is-inferred': edge.confidence !== 'EXTRACTED' }]"
                  />
                </svg>
                <button
                  v-for="node in graphCanvasNodes"
                  :key="node.id"
                  type="button"
                  class="graph-entity-node"
                  :class="{ 'is-center': node.isCenter }"
                  :style="{ left: `${node.x / 10}%`, top: `${node.y / 4.2}%` }"
                  @click="selectGraphNode(node)"
                >
                  <span class="graph-entity-node__type">{{ node.isCenter ? '中心实体' : '关联实体' }}</span>
                  <strong>{{ node.name }}</strong>
                  <code>{{ node.code || '未提供编码' }}</code>
                  <small v-if="node.entity">{{ node.entity.dataItemCount }} 字段 · {{ node.entity.apiActions.length }} 动作</small>
                </button>
                <el-empty v-if="graphCanvasNodes.length === 0" description="当前实体暂无可绘制的关系节点" />
              </div>
            </div>
            <div class="neighborhood" v-loading="loadingNeighborhood">
              <template v-if="graphNeighborhood">
                <div class="neighborhood-summary">
                  <div>
                    <span>中心对象</span>
                    <strong>{{ graphNeighborhood.center.name }}</strong>
                    <code>{{ graphNeighborhood.center.code }}</code>
                  </div>
                  <div>
                    <span>一跳节点</span>
                    <strong>{{ Math.max(0, graphNeighborhood.nodes.length - 1) }}</strong>
                  </div>
                  <div>
                    <span>关系边</span>
                    <strong>{{ graphNeighborhood.edges.length }}</strong>
                  </div>
                </div>
                <div class="edge-groups">
                  <section v-for="group in neighborhoodEdgeGroups" :key="group.type" class="edge-group">
                    <div class="edge-group__header">
                      <strong>{{ graphEdgeTypeLabel(group.type) }}</strong>
                      <span>{{ group.edges.length }}</span>
                    </div>
                    <div v-for="edge in group.edges.slice(0, 20)" :key="edge.id" class="edge-row">
                      <div>
                        <span>{{ edgeDirection(edge) }}</span>
                        <strong>{{ edgeNeighbor(edge)?.name || '未知对象' }}</strong>
                        <code>{{ edgeNeighbor(edge)?.code || edgeNeighbor(edge)?.type }}</code>
                      </div>
                      <el-tag :type="edge.confidence === 'EXTRACTED' ? 'success' : 'warning'" size="small">
                        {{ edge.confidence === 'EXTRACTED' ? '云枢明确关系' : '推断能力关系' }}
                      </el-tag>
                    </div>
                    <div v-if="group.edges.length > 20" class="edge-group__more">另有 {{ group.edges.length - 20 }} 条关系，可通过检索或多跳接口继续查看</div>
                  </section>
                </div>
                <el-alert v-if="graphNeighborhood.truncated" type="warning" :closable="false" show-icon title="邻域节点较多，当前仅展示前 100 个节点。" />
              </template>
              <el-empty v-else-if="!loadingNeighborhood" description="当前对象暂无可展示的图谱邻域" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else description="请选择左侧实体对象" />
    </main>
  </section>

  <section class="coverage-section">
    <div class="section-header">
      <div>
        <h2>应用覆盖率</h2>
        <p>全部已同步云枢应用均纳入同一活动图谱快照</p>
      </div>
      <span>{{ graphOverview?.coveredApplicationCount || 0 }}/{{ graphOverview?.applicationCount || 0 }}</span>
    </div>
    <el-table :data="graphOverview?.applications || []" max-height="360" empty-text="图谱尚未构建">
      <el-table-column prop="name" label="应用" min-width="170" show-overflow-tooltip />
      <el-table-column prop="appCode" label="编码" min-width="180" show-overflow-tooltip />
      <el-table-column prop="entityCount" label="实体" width="90" />
      <el-table-column prop="dataItemCount" label="数据项" width="100" />
      <el-table-column prop="edgeCount" label="相关边" width="100" />
      <el-table-column label="覆盖率" width="120">
        <template #default="{ row }">
          <el-tag :type="row.coverageRate === 1 ? 'success' : 'warning'">{{ formatPercent(row.coverageRate) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </section>

  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/common/PageHeader.vue'
import {
  listMetadataApps,
  loadMetadataGraphNeighborhood,
  loadMetadataGraphOverview,
  loadMetadataModel,
  rebuildMetadataGraph,
  syncMetadata
} from '../services/metadataApi'
import type {
  MetadataAppSummary,
  MetadataEntityModel,
  MetadataGraphEdge,
  MetadataGraphNode,
  MetadataGraphNeighborhood,
  MetadataGraphOverview,
  MetadataModelResponse,
  MetadataSyncResponse
} from '../types/metadata'

const emptyModel: MetadataModelResponse = { apps: [], apiActions: [] }

const apps = ref<MetadataAppSummary[]>([])
const model = ref<MetadataModelResponse>(emptyModel)
const graphOverview = ref<MetadataGraphOverview>()
const graphNeighborhood = ref<MetadataGraphNeighborhood>()
const syncResult = ref<MetadataSyncResponse>()
const selectedEntityId = ref('')
const entityKeyword = ref('')
const expandedAppIds = ref<string[]>([])
const activeTab = ref('fields')
type MetadataViewMode = 'list' | 'graph'
const VIEW_MODE_STORAGE_KEY = 'cpclaw.metadata.view-mode'
const viewModeOptions = [
  { label: '列表', value: 'list' },
  { label: '图谱', value: 'graph' }
]
const viewMode = ref<MetadataViewMode>(readViewMode())
const loadingApps = ref(false)
const loadingModel = ref(false)
const syncing = ref(false)
const rebuildingGraph = ref(false)
const loadingNeighborhood = ref(false)
const errorMessage = ref('')
const detailKeyword = ref('')

const allEntities = computed(() => model.value.apps.flatMap(app => app.entities))

const modelStats = computed(() => {
  const entities = allEntities.value
  return {
    appCount: model.value.apps.length,
    entityCount: entities.length,
    dataItemCount: entities.reduce((sum, entity) => sum + entity.dataItemCount, 0),
    actionCount: entities.reduce((sum, entity) => sum + entity.apiActions.length, 0),
    apiActionCount: graphOverview.value?.nodesByType.api_endpoint ?? model.value.apiActions.length,
    graphEdgeCount: graphOverview.value?.edgeCount ?? 0
  }
})

const filteredEntities = computed(() => {
  const keyword = entityKeyword.value.trim().toLowerCase()
  return filteredApps.value.flatMap(app => app.entities.filter(entity => {
    return !keyword || `${app.name} ${app.code} ${entity.name} ${entity.code}`.toLowerCase().includes(keyword)
  }))
})

const filteredApps = computed(() => {
  const keyword = entityKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return model.value.apps
  }
  return model.value.apps
    .map(app => {
      const appMatched = `${app.name} ${app.code}`.toLowerCase().includes(keyword)
      const entities = appMatched
        ? app.entities
        : app.entities.filter(entity => `${entity.name} ${entity.code}`.toLowerCase().includes(keyword))
      return { ...app, entities }
    })
    .filter(app => app.entities.length > 0)
})

const selectedEntity = computed(() => allEntities.value.find(entity => entity.id === selectedEntityId.value))
const detailKeywordLower = computed(() => detailKeyword.value.trim().toLowerCase())
const filteredDataItems = computed(() => {
  const entity = selectedEntity.value
  if (!entity || !detailKeywordLower.value) return entity?.dataItems || []
  return entity.dataItems.filter(item => `${item.name} ${item.code} ${item.dataType} ${item.description}`.toLowerCase().includes(detailKeywordLower.value))
})
const filteredRelations = computed(() => {
  const entity = selectedEntity.value
  if (!entity || !detailKeywordLower.value) return entity?.relations || []
  return entity.relations.filter(item => `${item.relationName} ${item.relationType} ${item.targetEntityName} ${item.targetEntityCode}`.toLowerCase().includes(detailKeywordLower.value))
})
const filteredActions = computed(() => {
  const entity = selectedEntity.value
  if (!entity || !detailKeywordLower.value) return entity?.apiActions || []
  return entity.apiActions.filter(item => `${item.name} ${item.apiCode} ${item.operationType} ${item.method} ${item.path} ${item.dataScope}`.toLowerCase().includes(detailKeywordLower.value))
})

const neighborhoodEdgeGroups = computed(() => {
  const groups = new Map<string, MetadataGraphEdge[]>()
  for (const edge of graphNeighborhood.value?.edges || []) {
    const edges = groups.get(edge.type) || []
    edges.push(edge)
    groups.set(edge.type, edges)
  }
  return Array.from(groups.entries()).map(([type, edges]) => ({ type, edges }))
})

watch(filteredEntities, next => {
  if (!next.some(entity => entity.id === selectedEntityId.value)) {
    selectedEntityId.value = next[0]?.id || ''
  }
})

watch([selectedEntityId, activeTab], ([entityId, tab]) => {
  if (entityId && tab === 'graph') {
    void loadNeighborhood(entityId)
  }
})

const graphNodeTypeRows = computed(() => Object.entries(graphOverview.value?.nodesByType || {})
  .map(([type, count]) => ({ type, count }))
  .sort((left, right) => right.count - left.count))

const graphEdgeTypeRows = computed(() => Object.entries(graphOverview.value?.edgesByType || {})
  .map(([type, count]) => ({ type, count }))
  .sort((left, right) => right.count - left.count))

const graphCanvasNodes = computed(() => {
  const nodes = graphNeighborhood.value?.nodes || []
  const centerId = graphNeighborhood.value?.center.id
  const neighbors = nodes.filter(node => node.id !== centerId)
  const center = nodes.find(node => node.id === centerId)
  const positioned = center ? [{ ...center, x: 500, y: 210, isCenter: true, entity: entityForGraphNode(center) }] : []
  const radiusX = 330
  const radiusY = 145
  neighbors.forEach((node, index) => {
    const angle = (Math.PI * 2 * index) / Math.max(neighbors.length, 1) - Math.PI / 2
    positioned.push({
      ...node,
      x: 500 + Math.cos(angle) * radiusX,
      y: 210 + Math.sin(angle) * radiusY,
      isCenter: false,
      entity: entityForGraphNode(node)
    })
  })
  return positioned
})

const graphCanvasEdges = computed(() => {
  const nodes = new Map(graphCanvasNodes.value.map(node => [node.id, node]))
  return (graphNeighborhood.value?.edges || []).map(edge => ({
    ...edge,
    source: nodes.get(edge.source) || { x: 500, y: 210 },
    target: nodes.get(edge.target) || { x: 500, y: 210 }
  })).filter(edge => edge.source.id && edge.target.id)
})

watch(viewMode, mode => {
  activeTab.value = mode === 'graph' ? 'graph' : 'fields'
  if (mode === 'graph' && selectedEntityId.value) void loadNeighborhood(selectedEntityId.value)
})

onMounted(() => {
  activeTab.value = viewMode.value === 'graph' ? 'graph' : 'fields'
  void load()
})

function readViewMode(): MetadataViewMode {
  if (typeof window === 'undefined') return 'list'
  return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'graph' ? 'graph' : 'list'
}

function rememberViewMode(mode: string | number | boolean) {
  const next = mode === 'graph' ? 'graph' : 'list'
  viewMode.value = next
  window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, next)
}

function openGraphStructure() {
  viewMode.value = 'graph'
  activeTab.value = 'graph'
  window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, 'graph')
  if (selectedEntityId.value) void loadNeighborhood(selectedEntityId.value)
}

async function load() {
  loadingApps.value = true
  loadingModel.value = true
  errorMessage.value = ''
  try {
    const [nextApps, nextModel, nextGraphOverview] = await Promise.all([
      listMetadataApps(),
      loadMetadataModel(),
      loadMetadataGraphOverview()
    ])
    apps.value = nextApps
    model.value = nextModel
    graphOverview.value = nextGraphOverview
    expandedAppIds.value = nextModel.apps.map(app => app.id)
    if (!selectedEntityId.value) {
      selectedEntityId.value = nextModel.apps.flatMap(app => app.entities)[0]?.id || ''
    }
  } catch (error) {
    reportError(error)
  } finally {
    loadingApps.value = false
    loadingModel.value = false
  }
}

async function sync() {
  syncing.value = true
  errorMessage.value = ''
  try {
    syncResult.value = await syncMetadata()
    await load()
    ElMessage.success('元数据初始化完成')
  } catch (error) {
    reportError(error)
  } finally {
    syncing.value = false
  }
}

async function rebuildGraph() {
  rebuildingGraph.value = true
  try {
    graphOverview.value = await rebuildMetadataGraph()
    graphNeighborhood.value = undefined
    if (selectedEntityId.value && activeTab.value === 'graph') {
      await loadNeighborhood(selectedEntityId.value)
    }
    ElMessage.success('全部应用知识图谱已重建')
  } catch (error) {
    reportError(error)
  } finally {
    rebuildingGraph.value = false
  }
}

async function loadNeighborhood(entityId: string) {
  loadingNeighborhood.value = true
  try {
    graphNeighborhood.value = await loadMetadataGraphNeighborhood('entity', entityId, 1, 100)
  } catch (error) {
    graphNeighborhood.value = undefined
    ElMessage.error(error instanceof Error && error.message ? error.message : '图谱邻域加载失败')
  } finally {
    loadingNeighborhood.value = false
  }
}

function selectEntity(entityId: string) {
  selectedEntityId.value = entityId
  detailKeyword.value = ''
  activeTab.value = viewMode.value === 'graph' ? 'graph' : 'fields'
}

function toggleApp(appId: string) {
  expandedAppIds.value = expandedAppIds.value.includes(appId)
    ? expandedAppIds.value.filter(id => id !== appId)
    : [...expandedAppIds.value, appId]
}

function appName(appId: string) {
  return model.value.apps.find(app => app.id === appId)?.name || apps.value.find(app => app.id === appId)?.name || '未知应用'
}

function edgeNeighbor(edge: MetadataGraphEdge) {
  const centerId = graphNeighborhood.value?.center.id
  const neighborId = edge.source === centerId ? edge.target : edge.source
  return graphNeighborhood.value?.nodes.find(node => node.id === neighborId)
}

function edgeDirection(edge: MetadataGraphEdge) {
  return edge.source === graphNeighborhood.value?.center.id ? '指向' : '来自'
}

function graphEdgeTypeLabel(type: string) {
  const labels: Record<string, string> = {
    APP_CONTAINS_ENTITY: '所属应用',
    ENTITY_HAS_DATA_ITEM: '数据项',
    DATA_ITEM_REFERENCES_ENTITY: '字段引用',
    ENTITY_RELATES_TO_ENTITY: '实体关系',
    API_OPERATES_ON_ENTITY: 'API 能力'
  }
  return labels[type] || type
}

function graphNodeTypeLabel(type: string) {
  const labels: Record<string, string> = {
    application: '应用节点', entity: '实体节点', data_item: '字段节点', api_endpoint: 'API 动作节点'
  }
  return labels[type] || type
}

function entityForGraphNode(node: MetadataGraphNode) {
  const entityId = node.entityId || node.objectId
  return allEntities.value.find(entity => entity.id === entityId || entity.code === node.code)
}

function selectGraphNode(node: { id: string; entity?: MetadataEntityModel }) {
  if (node.entity) { selectedEntityId.value = node.entity.id; activeTab.value = 'graph' }
}

function formatPercent(value: number) {
  return `${Math.round(value * 10000) / 100}%`
}

function reportError(error: unknown) {
  errorMessage.value = error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
  ElMessage.error(errorMessage.value)
}
</script>

<style scoped>
.page-error,
.sync-result {
  margin-bottom: 16px;
}

.toolbar-band,
.coverage-section,
.search-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 16px;
}

.toolbar-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(88px, 1fr));
  gap: 10px;
  min-width: 620px;
}

.summary-item {
  border: 1px solid #edf0f5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafbfc;
}

.summary-label {
  display: block;
  color: #667085;
  font-size: 12px;
}

.summary-item strong {
  display: block;
  margin-top: 4px;
  color: #111827;
  font-size: 20px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.search-input {
  width: 280px;
}

.graph-status-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  border: 1px solid #bbd5c5;
  border-radius: 6px;
  background: #f4fbf7;
  padding: 10px 14px;
  color: #344054;
  font-size: 13px;
}

.graph-status-band > div,
.graph-status-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.graph-status-band--warning {
  border-color: #ecd39d;
  background: #fffaf0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #98a2b3;
}

.status-dot.active {
  background: #16a34a;
}

.model-browser {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
  margin-top: 16px;
  min-height: 620px;
}

.entity-panel,
.detail-panel,
.search-section {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.entity-panel {
  padding: 14px;
  min-height: 620px;
}

.panel-header,
.section-header,
.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-header h2,
.section-header h2,
.detail-header h2 {
  margin: 0;
  font-size: 16px;
}

.panel-header p,
.detail-header p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.view-mode-switch {
  flex: 0 0 auto;
}

.detail-panel--graph {
  background: linear-gradient(180deg, #fbfdff 0%, #ffffff 42%);
}

.graph-mode-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 14px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f5f9ff;
  color: #334155;
}

.graph-mode-banner__eyebrow {
  display: block;
  margin-bottom: 3px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.graph-mode-banner strong {
  color: #0f172a;
  font-size: 13px;
}

.graph-mode-banner > span {
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.entity-search {
  width: 100%;
  margin-top: 12px;
}

.app-entity-list {
  height: 510px;
  margin-top: 12px;
}

.app-group + .app-group {
  margin-top: 10px;
}

.app-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  width: 100%;
  border: 1px solid #d9e2f2;
  border-radius: 6px;
  background: #f8fafc;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.app-row:hover {
  border-color: #93b8ff;
  background: #f3f7ff;
}

.app-title {
  min-width: 0;
  color: #111827;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-code,
.app-count {
  color: #667085;
  font-size: 12px;
}

.app-code {
  grid-column: 1;
  word-break: break-all;
}

.app-count {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-self: center;
  white-space: nowrap;
}

.entity-list {
  margin-top: 6px;
  padding-left: 10px;
  border-left: 2px solid #e5e7eb;
}

.entity-row {
  display: block;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  padding: 10px;
  text-align: left;
  cursor: pointer;
}

.entity-row + .entity-row {
  margin-top: 6px;
}

.entity-row:hover,
.entity-row.active {
  border-color: #bcd3ff;
  background: #f4f8ff;
}

.entity-name,
.entity-code,
.entity-meta {
  display: block;
}

.entity-name {
  color: #111827;
  font-weight: 600;
}

.entity-code,
.entity-meta {
  margin-top: 3px;
  color: #667085;
  font-size: 12px;
  word-break: break-all;
}

.detail-panel {
  min-width: 0;
  padding: 16px;
}

.entity-hero {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 24px;
  margin: -2px -2px 0;
  padding: 18px 18px 16px;
  border: 1px solid #e1e8f4;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 72%);
  box-shadow: 0 2px 8px rgb(31 55 88 / 4%);
}

.entity-hero__main {
  min-width: 0;
  flex: 1;
}

.entity-hero__title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 2px;
}

.entity-hero__title-row h2 {
  margin: 0;
  color: #0f1f38;
  font-size: 24px;
  line-height: 1.25;
  letter-spacing: -.02em;
}

.entity-type-tag {
  color: #3567c8;
  border-color: #b9cdf6;
  background: #f2f6ff;
}

.entity-hero__identity {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.entity-hero__identity code {
  padding: 3px 7px;
  border: 1px solid #e2e8f0;
  border-radius: 5px;
  background: #f8fafc;
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.entity-hero__description {
  max-width: 760px;
  margin: 10px 0 0;
  color: #5b6b82;
  font-size: 13px;
  line-height: 1.65;
}

.entity-hero__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(78px, 1fr));
  align-self: center;
  min-width: 264px;
  border-left: 1px solid #e6edf7;
}

.entity-hero__graph-action {
  align-self: flex-start;
  flex: 0 0 auto;
  margin-top: 2px;
}

.entity-stat {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 72px;
  padding: 0 14px;
  border-right: 1px solid #edf1f6;
}

.entity-stat strong {
  color: #162b4a;
  font-size: 22px;
  line-height: 1.1;
}

.entity-stat span {
  margin-top: 7px;
  color: #738198;
  font-size: 12px;
}

.entity-stat--blue strong { color: #2563eb; }
.entity-stat--amber strong { color: #c27a16; }
.entity-stat--green strong { color: #16845b; }

.detail-kicker {
  margin-bottom: 4px;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}

.detail-tags,
.tag-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.metadata-tabs {
  margin: 14px 2px 0;
  padding: 0 12px 12px;
  border: 1px solid #e7edf5;
  border-radius: 10px;
  background: #fff;
}

.detail-filter-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 10px 0;
}

.detail-filter-row .el-input {
  max-width: 360px;
}

.detail-filter-row > span {
  flex: 0 0 auto;
  color: #8491a5;
  font-size: 12px;
}

.graph-structure-summary {
  margin: 14px 0 16px;
  padding: 16px;
  border: 1px solid #dce6f4;
  border-radius: 10px;
  background: linear-gradient(145deg, #f7faff 0%, #ffffff 70%);
}

.graph-structure-summary__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.graph-structure-summary__eyebrow {
  display: block;
  margin-bottom: 4px;
  color: #3b72d9;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: .12em;
}

.graph-structure-summary__header strong {
  color: #13233c;
  font-size: 16px;
}

.graph-structure-summary__header p {
  margin: 5px 0 0;
  color: #718096;
  font-size: 12px;
}

.graph-structure-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.graph-structure-metrics > div {
  padding: 10px 12px;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
  background: #fff;
}

.graph-structure-metrics span,
.graph-type-row span {
  color: #738198;
  font-size: 12px;
}

.graph-structure-metrics strong {
  display: block;
  margin-top: 4px;
  color: #1b3153;
  font-size: 18px;
}

.graph-structure-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  margin-top: 16px;
}

.graph-structure-columns__title {
  padding-bottom: 7px;
  border-bottom: 1px solid #e7edf5;
  color: #334155;
  font-size: 12px;
  font-weight: 700;
}

.entity-graph-canvas {
  margin: 0 0 16px;
  padding: 14px;
  border: 1px solid #dce6f4;
  border-radius: 10px;
  background: #fbfdff;
}

.entity-graph-canvas__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.entity-graph-canvas__header strong { color: #1e3150; font-size: 14px; }
.entity-graph-canvas__header span { margin-left: 10px; color: #7b8799; font-size: 12px; }

.entity-graph-canvas__stage {
  position: relative;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid #e4ebf5;
  border-radius: 8px;
  background-image: radial-gradient(#d9e3f1 1px, transparent 1px);
  background-size: 18px 18px;
}

.entity-graph-canvas__edges { position: absolute; inset: 0; width: 100%; height: 100%; }
.graph-edge-line { stroke: #8eacd5; stroke-width: 1.7; vector-effect: non-scaling-stroke; }
.graph-edge-line.is-inferred { stroke: #d3a650; stroke-dasharray: 6 5; }

.graph-entity-node {
  position: absolute;
  width: 154px;
  min-height: 92px;
  transform: translate(-50%, -50%);
  padding: 10px 12px;
  border: 1px solid #cbd9ed;
  border-radius: 9px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 5px 16px rgb(38 73 120 / 10%);
  color: #1e293b;
  text-align: left;
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}

.graph-entity-node:hover { border-color: #6e9ce5; box-shadow: 0 8px 22px rgb(37 99 235 / 18%); transform: translate(-50%, -50%) translateY(-2px); }
.graph-entity-node.is-center { border-color: #4d7fe0; background: #f2f6ff; }
.graph-entity-node__type,
.graph-entity-node code,
.graph-entity-node small { display: block; color: #718096; font-size: 11px; }
.graph-entity-node strong { display: block; margin: 5px 0 4px; overflow: hidden; color: #173259; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.graph-entity-node code { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.graph-entity-node small { margin-top: 6px; }

.graph-type-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed #edf1f6;
}

.graph-type-row strong {
  color: #1e3a63;
  font-size: 13px;
}

.neighborhood {
  min-height: 420px;
}

.neighborhood-summary {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) repeat(2, minmax(100px, 160px));
  gap: 10px;
  margin-bottom: 14px;
}

.neighborhood-summary > div {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fafbfc;
  padding: 10px 12px;
}

.neighborhood-summary span,
.neighborhood-summary strong,
.neighborhood-summary code {
  display: block;
}

.neighborhood-summary span {
  color: #667085;
  font-size: 12px;
}

.neighborhood-summary strong {
  margin-top: 4px;
  color: #111827;
}

.neighborhood-summary code,
.edge-row code {
  margin-top: 3px;
  color: #667085;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}

.edge-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.edge-group {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  overflow: hidden;
}

.edge-group__header,
.edge-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.edge-group__more {
  padding: 8px 10px;
  border-top: 1px solid #eef1f5;
  color: #667085;
  font-size: 12px;
}

.edge-group__header {
  background: #f8fafc;
  padding: 8px 10px;
  color: #344054;
  font-size: 13px;
}

.edge-row {
  padding: 9px 10px;
  border-top: 1px solid #eef1f5;
}

.edge-row > div {
  min-width: 0;
}

.edge-row span,
.edge-row strong,
.edge-row code {
  display: block;
}

.edge-row span {
  color: #667085;
  font-size: 11px;
}

.edge-row strong {
  margin-top: 2px;
  color: #111827;
  font-size: 13px;
}

.method {
  display: inline-block;
  min-width: 44px;
  margin-right: 8px;
  color: #2563eb;
  font-weight: 700;
}

.muted {
  color: #98a2b3;
  font-size: 12px;
}

.coverage-section,
.search-section {
  margin-top: 16px;
}

.coverage-section {
  overflow: hidden;
}

.section-header p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 13px;
}

.section-header {
  margin-bottom: 12px;
}

.section-header span {
  color: #667085;
  font-size: 13px;
}

@media (max-width: 1100px) {
  .toolbar-main,
  .model-browser {
    grid-template-columns: 1fr;
  }

  .toolbar-main {
    align-items: stretch;
    flex-direction: column;
  }

  .summary-grid {
    min-width: 0;
  }

  .graph-status-band {
    align-items: flex-start;
    flex-direction: column;
  }
}

@media (max-width: 760px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .search-input {
    width: 100%;
  }

  .toolbar-actions {
    justify-content: stretch;
  }

  .toolbar-actions :deep(.el-button) {
    flex: 1;
    margin-left: 0;
  }

  .neighborhood-summary,
  .edge-groups {
    grid-template-columns: 1fr;
  }

  .edge-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .app-entity-list {
    height: 360px;
  }
}
</style>
