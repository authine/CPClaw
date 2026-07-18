<template>
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
          <span class="summary-label">关联</span>
          <strong>{{ modelStats.relationCount }}</strong>
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
        <el-input v-model="query" class="search-input" clearable placeholder="搜索应用、实体、字段或 API" @keydown.enter="search">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button :loading="searching" @click="search">搜索</el-button>
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

    <main class="detail-panel" v-loading="loadingModel">
      <template v-if="selectedEntity">
        <div class="detail-header">
          <div>
            <div class="detail-kicker">{{ appName(selectedEntity.appId) }}</div>
            <h2>{{ selectedEntity.name }}</h2>
            <p>{{ selectedEntity.code }}</p>
          </div>
          <div class="detail-tags">
            <el-tag>{{ selectedEntity.type || 'data' }}</el-tag>
            <el-tag type="success">{{ selectedEntity.dataItemCount }} 个数据项</el-tag>
            <el-tag type="warning">{{ selectedEntity.relationCount }} 个关联</el-tag>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="metadata-tabs">
          <el-tab-pane label="数据项" name="fields">
            <el-table :data="selectedEntity.dataItems" height="460" empty-text="暂无数据项">
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
            <el-table :data="selectedEntity.relations" height="460" empty-text="暂无关联关系">
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
            <el-table :data="selectedEntity.apiActions" height="460" empty-text="暂无 API 动作">
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

          <el-tab-pane label="知识图谱" name="graph">
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

  <section class="search-section">
    <div class="section-header">
      <h2>检索结果</h2>
      <span>{{ results.length }} 条</span>
    </div>
    <el-table v-loading="searching" :data="results" empty-text="暂无检索结果">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="objectType" label="类型" width="120" />
      <el-table-column prop="code" label="编码" min-width="160" show-overflow-tooltip />
      <el-table-column prop="graphPath" label="图谱路径" min-width="220" show-overflow-tooltip />
      <el-table-column prop="riskLevel" label="风险" width="100" />
      <el-table-column prop="reason" label="匹配原因" min-width="220" show-overflow-tooltip />
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageHeader from '../components/common/PageHeader.vue'
import {
  listMetadataApps,
  loadMetadataGraphNeighborhood,
  loadMetadataGraphOverview,
  loadMetadataModel,
  rebuildMetadataGraph,
  searchMetadata,
  syncMetadata
} from '../services/metadataApi'
import type {
  MetadataAppSummary,
  MetadataEntityModel,
  MetadataGraphEdge,
  MetadataGraphNeighborhood,
  MetadataGraphOverview,
  MetadataModelResponse,
  MetadataSearchResult,
  MetadataSyncResponse
} from '../types/metadata'

const emptyModel: MetadataModelResponse = { apps: [], apiActions: [] }

const apps = ref<MetadataAppSummary[]>([])
const model = ref<MetadataModelResponse>(emptyModel)
const graphOverview = ref<MetadataGraphOverview>()
const graphNeighborhood = ref<MetadataGraphNeighborhood>()
const results = ref<MetadataSearchResult[]>([])
const syncResult = ref<MetadataSyncResponse>()
const query = ref('')
const selectedEntityId = ref('')
const entityKeyword = ref('')
const expandedAppIds = ref<string[]>([])
const activeTab = ref('fields')
const loadingApps = ref(false)
const loadingModel = ref(false)
const searching = ref(false)
const syncing = ref(false)
const rebuildingGraph = ref(false)
const loadingNeighborhood = ref(false)
const errorMessage = ref('')

const allEntities = computed(() => model.value.apps.flatMap(app => app.entities))

const modelStats = computed(() => {
  const entities = allEntities.value
  return {
    appCount: model.value.apps.length,
    entityCount: entities.length,
    dataItemCount: entities.reduce((sum, entity) => sum + entity.dataItemCount, 0),
    relationCount: graphOverview.value?.edgesByType.ENTITY_RELATES_TO_ENTITY ?? Math.round(entities.reduce((sum, entity) => sum + entity.relationCount, 0) / 2),
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

onMounted(load)

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
    await search()
    ElMessage.success('元数据初始化完成')
  } catch (error) {
    reportError(error)
  } finally {
    syncing.value = false
  }
}

async function search() {
  searching.value = true
  errorMessage.value = ''
  try {
    results.value = await searchMetadata(query.value)
  } catch (error) {
    reportError(error)
  } finally {
    searching.value = false
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
  activeTab.value = 'fields'
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
  margin-top: 14px;
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
