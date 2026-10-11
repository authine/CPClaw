<template>
  <section class="metadata-panel" :aria-busy="showInitialSkeleton">
    <div class="section-heading">
      <div>
        <div class="section-kicker">元数据管理 / 查看</div>
        <h2>按应用浏览元数据</h2>
        <p>先选择应用，再查看该应用的实体、字段、关联和 API 能力。</p>
      </div>
      <div class="section-actions">
        <span :class="['sync-status', { 'sync-status--pending': showInitialSkeleton, 'sync-status--unavailable': !syncHistoryAvailable && hasLoaded }]">
          <el-icon :class="{ 'is-loading': showInitialSkeleton }"><component :is="showInitialSkeleton ? Loading : Clock" /></el-icon>
          {{ syncStatusText }}
        </span>
        <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新数据</el-button>
      </div>
    </div>

    <div v-if="showInitialSkeleton" class="metadata-loading" role="status" aria-live="polite">
      <span class="sr-only">正在读取已同步元数据</span>
      <div class="metadata-loading__intro">
        <span class="metadata-loading__mark" aria-hidden="true"><el-icon class="is-loading"><Loading /></el-icon></span>
        <div><span class="metadata-loading__eyebrow">正在准备工作区</span><strong>正在读取已同步元数据</strong><p>并行加载应用目录、资源关系和最近同步记录；不会重新同步云枢或改动已有数据。</p></div>
      </div>
      <div class="metadata-loading__steps" aria-hidden="true"><span><i></i>应用目录</span><span><i></i>实体与关联</span><span><i></i>同步记录</span></div>
      <div class="stats loading-stats">
        <div v-for="label in statLabels" :key="label" class="loading-stat"><span>{{ label }}</span><i class="loading-stat__value" /></div>
      </div>
    </div>

    <template v-else>
      <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />
      <div class="stats"><div v-for="item in stats" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div>

      <section v-if="!selectedApp" class="app-catalog"><div class="catalog-toolbar"><div><strong>应用目录</strong><small>选择一个应用查看资源详情</small></div><el-input v-model="appKeyword" clearable class="catalog-filter" placeholder="筛选应用"><template #prefix><el-icon><Search /></el-icon></template></el-input></div><div v-if="filteredApps.length" class="app-grid"><button v-for="app in filteredApps" :key="app.id" class="app-card" type="button" @click="openApp(app.id)"><span class="app-card__top"><span class="app-card__mark">{{ app.name.slice(0, 1) || '应' }}</span><el-icon><ArrowRight /></el-icon></span><strong>{{ app.name }}</strong><small>{{ app.code }}</small><span class="resource-summary"><span><b>{{ app.entities.length }}</b>实体</span><span><b>{{ appResourceCount(app, 'fields') }}</b>字段</span><span><b>{{ appResourceCount(app, 'relations') }}</b>关联</span><span><b>{{ appResourceCount(app, 'apis') }}</b>API</span></span></button></div><el-empty v-else-if="!errorMessage" description="没有匹配的应用" /></section>

      <section v-else class="app-detail"><div class="app-detail__toolbar"><el-button text class="back-button" @click="backToCatalog"><el-icon><ArrowLeft /></el-icon>应用目录</el-button><div class="app-switcher"><span>当前应用</span><el-select v-model="selectedAppId" aria-label="切换当前应用" @change="onAppChange"><el-option v-for="app in model.apps" :key="app.id" :label="app.name" :value="app.id" /></el-select></div></div><div class="app-detail__heading"><div><div class="section-kicker">应用资源详情</div><h3>{{ selectedApp.name }}</h3><p>{{ displayDescription(selectedApp.description, `云枢应用“${selectedApp.name}”（编码：${selectedApp.code}）`) }}</p></div><div class="app-metrics"><span><b>{{ selectedApp.entities.length }}</b>实体</span><span><b>{{ appResourceCount(selectedApp, 'fields') }}</b>字段</span><span><b>{{ appResourceCount(selectedApp, 'apis') }}</b>动作数</span></div></div><div class="detail-browser-grid"><aside class="entity-list-panel"><div class="entity-list-panel__title"><strong>实体</strong><span>{{ selectedAppEntities.length }}</span></div><el-input v-model="entityKeyword" clearable placeholder="筛选当前应用实体"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-scrollbar height="430px"><button v-for="entity in selectedAppEntities" :key="entity.id" class="entity-row" :class="{ active: entity.id === selectedEntityId }" @click="selectEntity(entity)"><strong>{{ entity.name }}</strong><span class="entity-row__code">{{ entity.code }}</span><span class="entity-row__metrics">{{ entity.dataItemCount }} 字段 · {{ entity.apiActions.length }} 动作</span></button><el-empty v-if="!selectedAppEntities.length" description="当前应用没有匹配实体" :image-size="72" /></el-scrollbar></aside><el-card v-if="viewMode === 'list'" class="config-card detail-card" shadow="never"><template #header><div class="detail-title"><div><strong>{{ selectedEntity?.name || '请选择实体' }}</strong><small>{{ displayDescription(selectedEntity?.description, selectedEntity?.code || '从当前应用选择实体，查看其资源信息。') }}</small></div><el-tag v-if="selectedEntity" effect="plain">{{ selectedEntity.type || 'data' }}</el-tag></div></template><template v-if="selectedEntity"><el-tabs v-model="activeTab" class="detail-tabs"><el-tab-pane :label="fieldTabLabel" name="fields"><div class="detail-filter"><el-input v-model="detailKeyword" clearable size="small" class="detail-filter__input" placeholder="筛选字段名称、编码或说明" /><el-select v-model="fieldCategory" size="small" class="detail-filter__select"><el-option label="主要字段" value="BUSINESS" /><el-option label="全部字段" value="ALL" /><el-option label="系统字段" value="SYSTEM" /></el-select><span class="detail-filter__count">{{ filteredDataItems.length }} / {{ selectedEntity.dataItems.length }}</span></div><el-table :data="filteredDataItems" max-height="330"><el-table-column prop="name" label="名称" min-width="130" /><el-table-column prop="code" label="编码" min-width="140" /><el-table-column label="字段类别" width="110"><template #default="{ row }"><el-tag size="small" :type="row.fieldCategory === 'SYSTEM' ? 'info' : 'success'">{{ row.fieldCategory === 'SYSTEM' ? '系统字段' : '业务字段' }}</el-tag></template></el-table-column><el-table-column label="类型" width="120"><template #default="{ row }"><span class="field-type">{{ fieldTypeLabel(row) }}</span></template></el-table-column><el-table-column prop="description" label="中文说明" min-width="220" show-overflow-tooltip /></el-table></el-tab-pane><el-tab-pane :label="relationTabLabel" name="relations"><div class="detail-filter"><el-input v-model="detailKeyword" clearable size="small" class="detail-filter__input" placeholder="筛选关系名称或目标实体" /><span class="detail-filter__count">{{ filteredRelations.length }} / {{ selectedEntity.relations.length }}</span></div><el-table :data="filteredRelations" max-height="330"><el-table-column prop="relationName" label="关系" min-width="280" show-overflow-tooltip /><el-table-column prop="targetEntityName" label="目标实体" min-width="320" show-overflow-tooltip /><el-table-column prop="relationType" label="类型" width="220" show-overflow-tooltip /></el-table></el-tab-pane><el-tab-pane :label="actionTabLabel" name="actions"><div class="detail-filter"><el-input v-model="detailKeyword" clearable size="small" class="detail-filter__input" placeholder="筛选动作名称、能力或路径" /><span class="detail-filter__count">{{ filteredActions.length }} / {{ selectedEntity.apiActions.length }}</span></div><el-table :data="filteredActions" max-height="330"><el-table-column prop="name" label="动作" min-width="140" /><el-table-column prop="operationType" label="能力" width="120" /><el-table-column prop="method" label="方法" width="90" /><el-table-column prop="path" label="路径" min-width="180" /></el-table></el-tab-pane></el-tabs></template><el-empty v-else description="请选择当前应用中的实体" /></el-card></div></section>
      <section v-if="selectedApp && selectedEntity" class="metadata-graph-mode" :class="{ 'metadata-graph-mode--active': viewMode === 'graph' }">
        <div class="metadata-graph-mode__toolbar">
          <div><strong>详情展示方式</strong><small>记住你的选择，下次进入元数据详情时继续使用。</small></div>
          <el-segmented v-model="viewMode" :options="viewModeOptions" @change="rememberViewMode" />
        </div>
        <div v-if="viewMode === 'graph'" class="graph-structure-card">
          <div class="graph-structure-card__header"><div><span class="graph-structure-card__eyebrow">实体关系图谱</span><strong>{{ selectedEntity.name }}</strong><small>实体节点汇总字段属性、关联对象和 API 动作；点击实体节点可继续查看其邻域。</small></div><el-tag v-if="loadingNeighborhood" type="info" effect="plain">正在加载</el-tag><el-tag v-else-if="graphNeighborhood" type="success" effect="plain">{{ graphNeighborhood.nodes.length }} 个节点</el-tag></div>
          <div v-loading="loadingNeighborhood" class="entity-graph-canvas"><svg class="entity-graph-canvas__edges" viewBox="0 0 1000 420" preserveAspectRatio="none" aria-hidden="true"><line v-for="edge in graphCanvasEdges" :key="edge.id" :x1="edge.source.x" :y1="edge.source.y" :x2="edge.target.x" :y2="edge.target.y" :class="['graph-edge-line', { 'is-inferred': edge.confidence !== 'EXTRACTED' }]" /></svg><button v-for="node in graphCanvasNodes" :key="node.id" type="button" class="graph-entity-node" :class="{ 'is-center': node.isCenter }" :style="{ left: `${node.x / 10}%`, top: `${node.y / 4.2}%` }" @click="selectGraphNode(node)"><span class="graph-entity-node__type">{{ graphNodeTypeLabel(node.type) }}</span><strong>{{ node.name }}</strong><code>{{ node.code || '未提供编码' }}</code><small v-if="node.entity">{{ node.entity.dataItemCount }} 字段 · {{ node.entity.apiActions.length }} 动作</small></button><el-empty v-if="!loadingNeighborhood && graphCanvasNodes.length === 0" description="当前实体暂无可绘制的邻域" /></div>
          <div v-if="graphNeighborhood" class="graph-structure-card__summary"><span>中心实体 <b>{{ graphNeighborhood.center.name }}</b></span><span>关联节点 <b>{{ Math.max(0, graphNeighborhood.nodes.length - 1) }}</b></span><span>关系边 <b>{{ graphNeighborhood.edges.length }}</b></span></div><el-alert v-if="graphNeighborhood?.truncated" type="warning" :closable="false" show-icon title="邻域节点较多，当前仅展示主要节点。" />
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Clock, Loading, Refresh, Search } from '@element-plus/icons-vue'
import { getMetadataSyncLogs, loadMetadataGraphNeighborhood, loadMetadataGraphOverview, loadMetadataModel, searchMetadata } from '../../services/metadataApi'
import type { MetadataAppModel, MetadataEntityModel, MetadataGraphEdge, MetadataGraphNeighborhood, MetadataGraphNode, MetadataGraphOverview, MetadataModelResponse, MetadataSearchResult, MetadataSyncLogOverview } from '../../types/metadata'

type ResourceType = 'fields' | 'relations' | 'apis'

const model = ref<MetadataModelResponse>({ apps: [], apiActions: [] })
const route = useRoute()
const router = useRouter()
const overview = ref<MetadataGraphOverview>()
const syncLogs = ref<MetadataSyncLogOverview>({ items: [] })
const loading = ref(false)
const hasLoaded = ref(false)
const syncHistoryAvailable = ref(true)
const searching = ref(false)
const query = ref('')
const appKeyword = ref('')
const entityKeyword = ref('')
const results = ref<MetadataSearchResult[]>([])
const searched = ref(false)
const selectedAppId = ref('')
const selectedEntityId = ref('')
const errorMessage = ref('')

type MetadataViewMode = 'list' | 'graph'
const VIEW_MODE_STORAGE_KEY = 'cpclaw.metadata.view-mode'
const viewModeOptions = [{ label: '列表', value: 'list' }, { label: '图谱', value: 'graph' }]
const viewMode = ref<MetadataViewMode>(readViewMode())
const activeTab = ref(viewMode.value === 'graph' ? 'graph' : 'fields')
const graphNeighborhood = ref<MetadataGraphNeighborhood>()
const loadingNeighborhood = ref(false)
const detailKeyword = ref('')
const fieldCategory = ref('BUSINESS')
const fieldTabLabel = computed(() => `字段 ${selectedEntity.value?.dataItems.length ?? 0}`)
const relationTabLabel = computed(() => `关联 ${selectedEntity.value?.relations.length ?? 0}`)
const actionTabLabel = computed(() => `API 动作 ${selectedEntity.value?.apiActions.length ?? 0}`)
const detailKeywordLower = computed(() => detailKeyword.value.trim().toLowerCase())
const filteredDataItems = computed(() => {
  const entity = selectedEntity.value
  if (!entity) return []
  const business = entity.dataItems.filter((item) => item.fieldCategory !== 'SYSTEM')
  const base = fieldCategory.value === 'SYSTEM' ? entity.dataItems.filter((item) => item.fieldCategory === 'SYSTEM')
    : fieldCategory.value === 'ALL' || business.length === 0 ? entity.dataItems : business
  const keyword = detailKeywordLower.value
  return keyword ? base.filter((item) => `${item.name} ${item.code} ${item.dataType} ${item.description ?? ''}`.toLowerCase().includes(keyword)) : base
})
const filteredRelations = computed(() => {
  const entity = selectedEntity.value
  if (!entity) return []
  const keyword = detailKeywordLower.value
  return keyword ? entity.relations.filter((item) => `${item.relationName} ${item.relationType} ${item.targetEntityName} ${item.targetEntityCode}`.toLowerCase().includes(keyword)) : entity.relations
})
const filteredActions = computed(() => {
  const entity = selectedEntity.value
  if (!entity) return []
  const keyword = detailKeywordLower.value
  return keyword ? entity.apiActions.filter((item) => `${item.name} ${item.apiCode} ${item.operationType} ${item.method} ${item.path}`.toLowerCase().includes(keyword)) : entity.apiActions
})

const graphCanvasNodes = computed(() => {
  const nodes = graphNeighborhood.value?.nodes ?? []
  const centerId = graphNeighborhood.value?.center.id
  const center = nodes.find((node) => node.id === centerId)
  const neighbors = nodes.filter((node) => node.id !== centerId)
  const positioned: Array<MetadataGraphNode & { x: number; y: number; isCenter: boolean; entity?: MetadataEntityModel }> = []
  if (center) positioned.push({ ...center, x: 500, y: 210, isCenter: true, entity: entityForGraphNode(center) })
  const radiusX = 330
  const radiusY = 145
  neighbors.forEach((node, index) => {
    const angle = (Math.PI * 2 * index) / Math.max(neighbors.length, 1) - Math.PI / 2
    positioned.push({ ...node, x: 500 + Math.cos(angle) * radiusX, y: 210 + Math.sin(angle) * radiusY, isCenter: false, entity: entityForGraphNode(node) })
  })
  return positioned
})

const graphCanvasEdges = computed(() => {
  const nodes = new Map(graphCanvasNodes.value.map((node) => [node.id, node]))
  return (graphNeighborhood.value?.edges ?? []).map((edge) => ({
    ...edge,
    source: nodes.get(edge.source),
    target: nodes.get(edge.target)
  })).filter((edge): edge is MetadataGraphEdge & { source: NonNullable<typeof edge.source>; target: NonNullable<typeof edge.target> } => Boolean(edge.source && edge.target))
})
function selectEntity(entity: MetadataEntityModel) { selectedEntityId.value = entity.id; detailKeyword.value = ''; activeTab.value = viewMode.value === 'graph' ? 'graph' : 'fields'; if (viewMode.value === 'graph') void loadNeighborhood(entity.id) }
function fieldTypeLabel(item: { dataType?: string }) { const raw = (item?.dataType ?? '').trim(); const match = raw.match(/^([^（(]+)/); return (match ? match[1].trim() : raw) || '未知' }
const statLabels = ['应用', '实体', '字段', '动作数']

const showInitialSkeleton = computed(() => loading.value && !hasLoaded.value)
const allEntities = computed(() => model.value.apps.flatMap((app) => app.entities))
const selectedApp = computed(() => model.value.apps.find((app) => app.id === selectedAppId.value))
const selectedAppEntities = computed(() => { const keyword = entityKeyword.value.trim().toLowerCase(); return (selectedApp.value?.entities ?? []).filter((entity) => !keyword || `${entity.name} ${entity.code}`.toLowerCase().includes(keyword)) })
const selectedEntity = computed<MetadataEntityModel | undefined>(() => selectedAppEntities.value.find((entity) => entity.id === selectedEntityId.value) ?? selectedApp.value?.entities.find((entity) => entity.id === selectedEntityId.value))
const filteredApps = computed(() => { const keyword = appKeyword.value.trim().toLowerCase(); return model.value.apps.filter((app) => !keyword || `${app.name} ${app.code}`.toLowerCase().includes(keyword)) })
const stats = computed(() => [{ label: '应用', value: model.value.apps.length }, { label: '实体', value: allEntities.value.length }, { label: '字段', value: allEntities.value.reduce((sum, entity) => sum + entity.dataItemCount, 0) }, { label: '动作数', value: allEntities.value.reduce((sum, entity) => sum + entity.apiActions.length, 0) }])
const syncStatusText = computed(() => { if (showInitialSkeleton.value) return '正在读取同步状态'; if (!syncHistoryAvailable.value) return '同步记录暂不可用'; return syncLogs.value.latestSuccessfulAt ? `最近成功同步：${formatDate(syncLogs.value.latestSuccessfulAt)}` : '尚无成功同步记录' })

onMounted(() => { activeTab.value = viewMode.value === 'graph' ? 'graph' : 'fields'; void load() })
watch(viewMode, (mode) => {
  activeTab.value = mode === 'graph' ? 'graph' : 'fields'
  if (mode === 'graph' && selectedEntityId.value) void loadNeighborhood(selectedEntityId.value)
})
watch(() => route.params.appId, (appId) => {
  if (typeof appId === 'string' && appId && model.value.apps.some((app) => app.id === appId)) {
    selectedAppId.value = appId
    selectedEntityId.value = typeof route.query.entity === 'string' ? route.query.entity : firstUsefulEntity(model.value.apps.find((app) => app.id === appId))?.id ?? ''
  }
}, { immediate: true })

function appResourceCount(app: MetadataAppModel, type: ResourceType) { if (type === 'fields') return app.entities.reduce((sum, entity) => sum + entity.dataItemCount, 0); if (type === 'relations') return app.entities.reduce((sum, entity) => sum + entity.relationCount, 0); return app.entities.reduce((sum, entity) => sum + entity.apiActions.length, 0) }
function displayDescription(value: string | undefined, fallback: string) { const text = value?.trim() ?? ''; if (!text || text.startsWith('{') || text.startsWith('[')) return fallback; return text }
function firstUsefulEntity(app?: MetadataAppModel) { return app?.entities.find((entity) => entity.dataItemCount > 0 || entity.relationCount > 0) ?? app?.entities[0] }
function openApp(appId: string, entityId?: string) { const app = model.value.apps.find((item) => item.id === appId); selectedAppId.value = appId; selectedEntityId.value = entityId ?? firstUsefulEntity(app)?.id ?? ''; entityKeyword.value = ''; void router.push({ path: `/settings/metadata/${encodeURIComponent(appId)}`, query: selectedEntityId.value ? { entity: selectedEntityId.value } : undefined }) }
function onAppChange(appId: string) { const entityId = firstUsefulEntity(model.value.apps.find((app) => app.id === appId))?.id ?? ''; selectedEntityId.value = entityId; entityKeyword.value = ''; void router.push({ path: `/settings/metadata/${encodeURIComponent(appId)}`, query: entityId ? { entity: entityId } : undefined }) }
function backToCatalog() { selectedAppId.value = ''; selectedEntityId.value = ''; entityKeyword.value = ''; void router.push({ path: '/settings', query: { section: 'metadata-browser' } }) }
async function load() { loading.value = true; errorMessage.value = ''; try { const [modelResult, overviewResult, syncLogsResult] = await Promise.allSettled([loadMetadataModel(), loadMetadataGraphOverview(), getMetadataSyncLogs()]); if (modelResult.status === 'rejected') throw modelResult.reason; if (overviewResult.status === 'rejected') throw overviewResult.reason; model.value = modelResult.value; overview.value = overviewResult.value; const routeAppId = typeof route.params.appId === 'string' ? route.params.appId : ''; if (routeAppId && model.value.apps.some((app) => app.id === routeAppId)) { selectedAppId.value = routeAppId; selectedEntityId.value = typeof route.query.entity === 'string' ? route.query.entity : firstUsefulEntity(model.value.apps.find((app) => app.id === routeAppId))?.id ?? '' } else if (selectedAppId.value && !model.value.apps.some((app) => app.id === selectedAppId.value)) backToCatalog(); if (syncLogsResult.status === 'fulfilled') { syncLogs.value = syncLogsResult.value; syncHistoryAvailable.value = true } else { syncHistoryAvailable.value = false } if (viewMode.value === 'graph' && selectedEntityId.value) await loadNeighborhood(selectedEntityId.value) } catch (error) { errorMessage.value = error instanceof Error && error.message ? error.message : '元数据加载失败' } finally { hasLoaded.value = true; loading.value = false } }
function readViewMode(): MetadataViewMode { if (typeof window === 'undefined') return 'list'; return window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'graph' ? 'graph' : 'list' }
function rememberViewMode(mode: string | number | boolean) { const next = mode === 'graph' ? 'graph' : 'list'; viewMode.value = next; if (typeof window !== 'undefined') window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, next) }
function graphNodeTypeLabel(type: string) { return ({ application: '应用', entity: '实体', data_item: '字段', api_endpoint: 'API 动作' } as Record<string, string>)[type] ?? type }
function entityForGraphNode(node: MetadataGraphNode) { const entityId = node.entityId || node.objectId; return allEntities.value.find((entity) => entity.id === entityId || entity.code === node.code) }
function selectGraphNode(node: { entity?: MetadataEntityModel }) { if (node.entity) { selectedEntityId.value = node.entity.id; if (viewMode.value === 'graph') void loadNeighborhood(node.entity.id) } }
async function loadNeighborhood(entityId: string) { loadingNeighborhood.value = true; try { graphNeighborhood.value = await loadMetadataGraphNeighborhood('entity', entityId, 1, 100) } catch (error) { graphNeighborhood.value = undefined; ElMessage.error(error instanceof Error && error.message ? error.message : '图谱邻域加载失败') } finally { loadingNeighborhood.value = false } }
async function search() { searching.value = true; searched.value = true; try { results.value = await searchMetadata(query.value) } catch (error) { ElMessage.error(error instanceof Error && error.message ? error.message : '元数据搜索失败') } finally { searching.value = false } }
function locateResult(result: MetadataSearchResult) { const entity = allEntities.value.find((item) => item.id === result.objectId); if (entity) { openApp(entity.appId, entity.id); return }; const app = model.value.apps.find((item) => item.id === result.objectId); if (app) { openApp(app.id); return }; ElMessage.info('该搜索结果暂不能定位到应用资源详情') }
function formatDate(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
</script>

<style scoped>
.metadata-panel{display:grid;gap:var(--settings-section-gap)}.section-heading,.section-actions,.search-row,.detail-title,.app-detail__toolbar,.app-detail__heading,.catalog-toolbar{display:flex;align-items:center;justify-content:space-between;gap:14px}.section-heading{align-items:flex-start;gap:24px}.section-heading h2{margin:8px 0 7px;font-size:28px;letter-spacing:-.025em;line-height:1.25}.section-heading p,.detail-title small,.app-detail__heading p{margin:0;color:var(--settings-secondary);font-size:14px;line-height:1.6}.section-kicker{color:var(--settings-brand);font-size:12px;font-weight:800;letter-spacing:.08em}.section-actions{flex-wrap:wrap;justify-content:flex-end}.sync-status{display:inline-flex;align-items:center;gap:6px;color:var(--settings-secondary);font-size:12px;line-height:1.4;white-space:nowrap}.sync-status--pending{color:var(--settings-brand)}.sync-status--unavailable{color:var(--settings-tertiary)}.stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.stats>div{display:grid;gap:5px;padding:16px;border:1px solid var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface)}.stats span{color:var(--settings-secondary);font-size:12px}.stats strong{font-size:22px}.config-card{border-color:var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface);box-shadow:none}.config-card :deep(.el-card__header){padding:18px 22px;border-color:var(--settings-line)}.config-card :deep(.el-card__body){padding:22px}.search-card{padding:2px}.search-row .el-input{max-width:520px}.metadata-loading{display:grid;gap:18px;min-height:258px;padding:24px;border:1px solid var(--settings-line);border-radius:var(--settings-radius);background:linear-gradient(135deg,var(--settings-surface),color-mix(in srgb,var(--settings-brand-soft) 28%,var(--settings-surface)));animation:metadata-appear .2s ease-out}.metadata-loading__intro{display:flex;align-items:flex-start;gap:14px}.metadata-loading__mark{display:grid;flex:0 0 38px;width:38px;height:38px;border-radius:11px;background:var(--settings-brand-soft);color:var(--settings-brand);font-size:19px;place-items:center}.metadata-loading__intro>div{display:grid;gap:4px}.metadata-loading__eyebrow{color:var(--settings-brand);font-size:11px;font-weight:800;letter-spacing:.08em}.metadata-loading strong{color:var(--settings-text);font-size:17px;line-height:1.35}.metadata-loading p{max-width:620px;margin:0;color:var(--settings-secondary);font-size:13px;line-height:1.6}.metadata-loading__steps{display:flex;flex-wrap:wrap;gap:8px}.metadata-loading__steps span{display:inline-flex;align-items:center;gap:7px;padding:6px 10px;border:1px solid var(--settings-line);border-radius:999px;background:color-mix(in srgb,var(--settings-surface) 74%,transparent);color:var(--settings-secondary);font-size:12px}.metadata-loading__steps i{width:6px;height:6px;border-radius:50%;background:var(--settings-brand);box-shadow:0 0 0 4px var(--settings-brand-soft);animation:metadata-pulse 1.4s ease-in-out infinite}.metadata-loading__steps span:nth-child(2) i{animation-delay:.18s}.metadata-loading__steps span:nth-child(3) i{animation-delay:.36s}.loading-stats{margin-top:auto}.loading-stat{min-height:60px}.loading-stat__value{display:block;width:54%;height:22px;border-radius:6px;background:linear-gradient(90deg,var(--settings-surface-soft) 20%,color-mix(in srgb,var(--settings-surface-soft) 62%,var(--settings-surface)) 45%,var(--settings-surface-soft) 70%);background-size:200% 100%;animation:metadata-shimmer 1.3s linear infinite}.app-catalog{display:grid;gap:16px;animation:metadata-appear .2s ease-out}.catalog-toolbar{padding:2px 2px 0}.catalog-toolbar>div{display:grid;gap:4px}.catalog-toolbar strong{font-size:17px}.catalog-toolbar small{color:var(--settings-secondary);font-size:12px}.catalog-filter{width:240px}.app-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:12px}.app-card{display:grid;gap:9px;min-height:174px;padding:17px;border:1px solid var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface);color:var(--settings-text);text-align:left;cursor:pointer;transition:border-color .16s ease,transform .16s ease,box-shadow .16s ease}.app-card:hover{border-color:var(--settings-brand);box-shadow:0 8px 22px rgba(31,48,85,.08);transform:translateY(-2px)}.app-card:focus-visible{outline:2px solid var(--settings-brand);outline-offset:2px}.app-card__top{display:flex;align-items:center;justify-content:space-between;color:var(--settings-tertiary)}.app-card__mark{display:grid;width:32px;height:32px;border-radius:9px;background:var(--settings-brand-soft);color:var(--settings-brand);font-size:14px;font-weight:800;place-items:center}.app-card>strong{overflow:hidden;font-size:15px;text-overflow:ellipsis;white-space:nowrap}.app-card>small{overflow:hidden;color:var(--settings-secondary);font-size:12px;text-overflow:ellipsis;white-space:nowrap}.resource-summary{display:grid;grid-template-columns:repeat(4,1fr);gap:4px;margin-top:auto;padding-top:10px;border-top:1px solid var(--settings-line);color:var(--settings-secondary);font-size:11px}.resource-summary span{display:grid;gap:2px}.resource-summary b{color:var(--settings-text);font-size:14px}.app-detail{display:grid;gap:16px;animation:metadata-appear .2s ease-out}.back-button{margin-left:-10px}.app-switcher{display:flex;align-items:center;gap:10px;color:var(--settings-secondary);font-size:12px}.app-switcher .el-select{width:260px}.app-detail__heading{align-items:flex-end;padding:4px 2px 2px}.app-detail__heading h3{margin:6px 0 4px;font-size:24px;letter-spacing:-.02em}.app-metrics{display:flex;gap:10px}.app-metrics span{padding:7px 10px;border:1px solid var(--settings-line);border-radius:9px;color:var(--settings-secondary);font-size:11px}.app-metrics b{margin-right:4px;color:var(--settings-text);font-size:14px}.detail-browser-grid{display:grid;grid-template-columns:minmax(250px,.34fr) minmax(0,1fr);gap:16px}.entity-list-panel{display:grid;gap:12px;padding:16px;border:1px solid var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface)}.entity-list-panel__title{display:flex;align-items:center;justify-content:space-between}.entity-list-panel__title span{display:grid;min-width:22px;height:22px;border-radius:999px;background:var(--settings-brand-soft);color:var(--settings-brand);font-size:11px;place-items:center}.entity-row{display:grid;width:100%;gap:4px;margin-top:4px;padding:9px 10px;border:0;border-radius:8px;background:transparent;color:var(--settings-text);text-align:left;cursor:pointer}.entity-row:hover,.entity-row.active{background:var(--settings-brand-soft,rgba(79,110,247,.1));color:var(--settings-brand)}.entity-row span{overflow:hidden;color:var(--settings-secondary);font-size:11px;text-overflow:ellipsis;white-space:nowrap}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@keyframes metadata-appear{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:translateY(0)}}@keyframes metadata-shimmer{to{background-position:-200% 0}}@keyframes metadata-pulse{50%{opacity:.42;transform:scale(.82)}}@media(prefers-reduced-motion:reduce){.metadata-loading,.app-catalog,.app-detail,.metadata-loading__steps i,.loading-stat__value{animation:none}}@media(max-width:900px){.stats{grid-template-columns:repeat(2,minmax(0,1fr))}.detail-browser-grid{grid-template-columns:1fr}.section-heading,.app-detail__heading{align-items:flex-start;flex-direction:column}.app-metrics{flex-wrap:wrap}}@media(max-width:600px){.section-heading h2{font-size:25px}.section-actions,.search-row,.catalog-toolbar,.app-detail__toolbar{align-items:stretch;flex-direction:column}.section-actions{width:100%}.sync-status{white-space:normal}.search-row .el-input,.catalog-filter,.app-switcher .el-select{max-width:none;width:100%}.app-switcher{align-items:stretch;flex-direction:column}.app-grid{grid-template-columns:1fr}.app-metrics{width:100%;display:grid;grid-template-columns:repeat(2,1fr)}.metadata-loading{min-height:0;padding:20px}.metadata-loading__intro{gap:12px}.metadata-loading__steps{gap:6px}.config-card :deep(.el-card__header),.config-card :deep(.el-card__body){padding:18px}}
</style>

<style scoped>
.detail-title { display: grid; gap: 8px; padding-bottom: 14px; border-bottom: 1px solid var(--settings-line); }
.detail-title strong { font-size: 18px; letter-spacing: -.01em; }
.detail-title small { color: var(--settings-secondary); font-size: 12.5px; line-height: 1.65; }
.detail-card :deep(.el-card__header) { padding: 18px 20px 14px; }
.detail-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.detail-tabs :deep(.el-tabs__item) { font-weight: 600; }
.detail-filter { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.detail-filter__input { max-width: 320px; }
.detail-filter__select { width: 122px; }
.detail-filter__count { color: var(--settings-secondary); font-size: 12px; white-space: nowrap; }
.field-type { display: inline-block; padding: 1px 8px; border-radius: 999px; background: var(--settings-surface-soft); color: var(--settings-secondary); font-size: 12px; }
.entity-row__code { color: var(--settings-tertiary); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }
.entity-row__metrics { color: var(--settings-secondary); }
.metadata-graph-mode { display: grid; gap: 12px; margin-top: 2px; }
.metadata-graph-mode__toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 14px; border: 1px solid var(--settings-line); border-radius: var(--settings-radius); background: var(--settings-surface-soft); }
.metadata-graph-mode__toolbar > div { display: grid; gap: 3px; }
.metadata-graph-mode__toolbar strong { font-size: 13px; }
.metadata-graph-mode__toolbar small { color: var(--settings-secondary); font-size: 12px; }
.graph-structure-card { display: grid; gap: 14px; padding: 16px; border: 1px solid var(--settings-line); border-radius: var(--settings-radius); background: var(--settings-surface); }
.graph-structure-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.graph-structure-card__header > div { display: grid; gap: 3px; }
.graph-structure-card__eyebrow { color: var(--settings-brand); font-size: 11px; font-weight: 800; letter-spacing: .08em; }
.graph-structure-card__header strong { font-size: 16px; }
.graph-structure-card__header small { color: var(--settings-secondary); font-size: 12px; line-height: 1.5; }
.entity-graph-canvas { position: relative; min-height: 420px; overflow: hidden; border: 1px solid var(--settings-line); border-radius: 12px; background: radial-gradient(circle at 50% 48%, var(--settings-brand-soft), transparent 34%), var(--settings-surface-soft); }
.entity-graph-canvas__edges { position: absolute; inset: 0; width: 100%; height: 100%; }
.graph-edge-line { stroke: color-mix(in srgb, var(--settings-brand) 58%, transparent); stroke-width: 1.7; vector-effect: non-scaling-stroke; }
.graph-edge-line.is-inferred { stroke: #d3a650; stroke-dasharray: 6 5; }
.graph-entity-node { position: absolute; width: 150px; min-height: 82px; padding: 10px 12px; transform: translate(-50%, -50%); border: 1px solid var(--settings-line); border-radius: 10px; background: var(--settings-surface); color: var(--settings-text); box-shadow: 0 6px 18px rgb(31 48 85 / 9%); text-align: left; cursor: pointer; transition: border-color .16s ease, transform .16s ease, box-shadow .16s ease; }
.graph-entity-node:hover, .graph-entity-node:focus-visible { border-color: var(--settings-brand); box-shadow: 0 8px 22px rgb(37 99 235 / 18%); transform: translate(-50%, -50%) translateY(-2px); outline: none; }
.graph-entity-node.is-center { border-color: var(--settings-brand); background: var(--settings-brand-soft); }
.graph-entity-node__type, .graph-entity-node code, .graph-entity-node small { display: block; overflow: hidden; color: var(--settings-secondary); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.graph-entity-node strong { display: block; margin: 5px 0 4px; overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.graph-entity-node small { margin-top: 6px; }
.graph-structure-card__summary { display: flex; flex-wrap: wrap; gap: 10px; color: var(--settings-secondary); font-size: 12px; }
.graph-structure-card__summary span { padding: 7px 10px; border: 1px solid var(--settings-line); border-radius: 8px; }
.graph-structure-card__summary b { margin-left: 4px; color: var(--settings-text); }
.resource-summary { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.resource-summary span:nth-child(3) { display: none; }
@media(max-width:600px){.metadata-graph-mode__toolbar,.graph-structure-card__header{align-items:stretch;flex-direction:column}.entity-graph-canvas{min-height:360px}.graph-entity-node{width:128px;padding:8px}.graph-entity-node strong{font-size:12px}}
</style>
