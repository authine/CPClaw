<template>
  <section class="log-analytics">
    <div class="section-heading">
      <div>
        <div class="section-kicker">智能与安全 / 调用明细</div>
        <h2>调用明细</h2>
        <p>按条件筛选已脱敏的调用记录，查看执行上下文、Token 和工具调用详情。</p>
      </div>
      <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新数据</el-button>
    </div>

    <el-card class="config-card filter-card" shadow="never">
      <div class="filters">
        <el-select v-model="filters.status" clearable placeholder="全部状态" @change="load"><el-option label="已完成" value="completed" /><el-option label="云枢查询失败" value="runtime_query_failed" /><el-option label="等待确认" value="pending_confirmation" /><el-option label="已中止" value="cancelled" /></el-select>
        <el-input v-model="filters.intent" clearable placeholder="搜索业务意图" @keyup.enter="load" @clear="load"><template #prefix><el-icon><Search /></el-icon></template></el-input>
        <el-button @click="resetFilters">重置筛选</el-button>
      </div>
    </el-card>

    <el-alert v-if="errorMessage" type="warning" :closable="false" show-icon :title="errorMessage" />

    <el-alert class="analytics-note" type="info" :closable="false" show-icon title="日志仅展示脱敏摘要；Token 取自模型服务返回的 usage，未返回时显示“—”，不做估算。" />
    <el-card class="config-card logs-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>调用明细</strong><small>{{ analytics ? `共 ${analytics.total} 条，按最新执行时间排序。` : '等待后端返回日志分析数据。' }}</small></div><span class="table-hint">点击任意记录查看详情</span></div></template>
      <el-table v-loading="loading" :data="analytics?.items ?? []" row-key="id" class="compact-table" empty-text="暂未记录可分析的调用日志" @row-click="openDetail">
        <el-table-column label="执行时间" width="156"><template #default="{ row }"><span class="nowrap">{{ formatDate(row.createdAt) }}</span></template></el-table-column>
        <el-table-column prop="modelName" label="模型 / 执行方式" width="142" show-overflow-tooltip />
        <el-table-column prop="inputSummary" label="输入摘要（脱敏）" min-width="170" show-overflow-tooltip />
        <el-table-column prop="outputSummary" label="输出摘要（脱敏）" min-width="190" show-overflow-tooltip />
      <el-table-column label="Token 消耗" width="218"><template #default="{ row }"><div class="token-cell"><strong class="token-main">总计 {{ formatNumber(row.totalTokens) }}</strong><small v-if="row.totalTokens !== null && row.totalTokens !== undefined">输入 {{ formatNumber(row.promptTokens) }} · 输出 {{ formatNumber(row.completionTokens) }} · 缓存 {{ formatNumber(row.cachedTokens) }}</small><small v-else>未返回用量</small></div></template></el-table-column>
        <el-table-column label="耗时" width="82"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
        <el-table-column label="详情" width="66" fixed="right"><template #default="{ row }"><el-button text type="primary" class="detail-link" @click.stop="openDetail(row)">查看</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><span>第 {{ analytics?.page ?? 1 }} 页</span><el-pagination background size="small" layout="prev, pager, next" :current-page="analytics?.page ?? 1" :page-size="20" :total="analytics?.total ?? 0" @current-change="changePage" /></div>
    </el-card>

    <el-drawer v-model="detailVisible" title="调用详情" size="min(620px, 92vw)" destroy-on-close class="log-detail-drawer">
      <template v-if="selectedItem">
        <div class="detail-summary"><div><span>执行时间</span><strong>{{ formatDate(selectedItem.createdAt) }}</strong></div><div><span>模型 / 执行方式</span><strong>{{ selectedItem.modelName }}</strong></div><div><span>业务意图</span><strong>{{ displayIntent(selectedItem.businessIntent) }}</strong></div><div><span>状态</span><el-tag size="small" :type="statusType(selectedItem.status)">{{ statusLabel(selectedItem.status) }}</el-tag></div></div>
        <div class="detail-metrics"><div><span>输入 Token</span><strong>{{ formatNumber(selectedItem.promptTokens) }}</strong></div><div><span>输出 Token</span><strong>{{ formatNumber(selectedItem.completionTokens) }}</strong></div><div><span>缓存 Token</span><strong>{{ formatNumber(selectedItem.cachedTokens) }}</strong></div><div><span>总计 Token</span><strong>{{ formatNumber(selectedItem.totalTokens) }}</strong></div><div><span>耗时</span><strong>{{ formatDuration(selectedItem.durationMs) }}</strong></div><div><span>工具调用</span><strong>{{ selectedItem.toolCallCount }}</strong></div></div>
        <el-skeleton v-if="detailLoading" :rows="6" animated />
        <el-alert v-else-if="detailError" type="warning" :closable="false" show-icon :title="detailError" />
        <template v-else>
          <section class="detail-block"><h3>输入摘要（脱敏）</h3><pre>{{ selectedItem.inputSummary || '—' }}</pre></section>
          <section class="detail-block"><h3>输出摘要（脱敏）</h3><pre>{{ selectedItem.outputSummary || '—' }}</pre></section>
          <section v-if="detail?.planJson" class="detail-block"><h3>执行计划</h3><pre>{{ prettyJson(detail.planJson) }}</pre></section>
          <section v-if="detail?.reflectionJson" class="detail-block"><h3>反思与结果</h3><pre>{{ prettyJson(detail.reflectionJson) }}</pre></section>
          <section v-if="detail?.tools?.length" class="detail-block"><h3>工具调用（{{ detail.tools.length }}）</h3><div v-for="tool in detail.tools" :key="tool.id" class="tool-row"><div><strong>{{ tool.toolName }}</strong><el-tag size="small" :type="tool.status === 'completed' ? 'success' : 'warning'">{{ tool.status }}</el-tag></div><pre>{{ tool.outputJsonMasked || tool.inputJsonMasked || '—' }}</pre></div></section>
        </template>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getAgentRun, getLogAnalytics, type AuditDetail, type LogAnalyticsItem, type LogAnalyticsResponse } from '../../services/auditApi'

const analytics = ref<LogAnalyticsResponse>()
const loading = ref(false)
const errorMessage = ref('')
const filters = reactive({ status: '', intent: '', page: 1 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const selectedItem = ref<LogAnalyticsItem>()
const detail = ref<AuditDetail>()
load()
async function load() { loading.value = true; errorMessage.value = ''; try { analytics.value = await getLogAnalytics({ status: filters.status, intent: filters.intent, page: filters.page, size: 20 }) } catch (error) { analytics.value = undefined; errorMessage.value = analyticsErrorMessage(error); ElMessage.warning(errorMessage.value) } finally { loading.value = false } }
function resetFilters() { filters.status = ''; filters.intent = ''; filters.page = 1; void load() }
function changePage(page: number) { filters.page = page; void load() }
async function openDetail(item: LogAnalyticsItem) { selectedItem.value = item; detail.value = undefined; detailError.value = ''; detailVisible.value = true; detailLoading.value = true; try { detail.value = await getAgentRun(item.id) } catch (error) { detailError.value = error instanceof Error && error.message ? error.message : '调用详情加载失败' } finally { detailLoading.value = false } }
function formatNumber(value?: number) { return value === undefined || value === null ? '—' : new Intl.NumberFormat('zh-CN').format(value) }
function formatDuration(value?: number) { return value === undefined || value === null ? '—' : value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${Math.round(value)}ms` }
function formatDate(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function prettyJson(value?: string) { if (!value) return '—'; try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value } }
function displayIntent(value?: string) { const text = (value || '').trim(); return text || '历史记录未标注' }
function statusLabel(value: string) { return ({ completed: '已完成', runtime_query_failed: '查询失败', pending_confirmation: '等待确认', cancelled: '已中止', no_runtime_candidate: '未匹配对象' } as Record<string, string>)[value] || value || '未知' }
function statusType(value: string) { return value === 'completed' ? 'success' : value.includes('failed') || value === 'cancelled' ? 'danger' : 'warning' }
function analyticsErrorMessage(error: unknown) {
  const rawMessage = error instanceof Error && error.message ? error.message : ''
  return /No static resource.*audit\/analytics/i.test(rawMessage)
    ? '当前后端版本未包含日志分析接口，需要以持久化数据库配置启动新版后端。'
    : (rawMessage || '日志分析加载失败，请稍后重试。')
}
</script>

<style scoped>
.log-analytics{display:grid;max-width:var(--settings-content-width);gap:var(--settings-section-gap)}.section-heading,.filters,.card-title-row,.pagination{display:flex;align-items:center;justify-content:space-between;gap:14px}.section-heading{align-items:flex-start;gap:24px}.section-heading h2{margin:8px 0 7px;font-size:28px;letter-spacing:-.025em;line-height:1.25}.section-heading p{max-width:760px;margin:0;color:var(--settings-secondary);font-size:14px;line-height:1.65}.section-kicker{color:var(--settings-brand);font-size:12px;font-weight:800;letter-spacing:.08em}.config-card{border-color:var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface);box-shadow:none}.config-card :deep(.el-card__header){padding:15px 20px;border-color:var(--settings-line)}.config-card :deep(.el-card__body){padding:18px 20px}.filters{justify-content:flex-start}.filters .el-select{width:180px}.filters .el-input{width:220px}.card-title-row small,.pagination>span{color:var(--settings-secondary);font-size:12px}.analytics-note{margin:0}.table-hint{color:var(--settings-tertiary);font-size:12px}.logs-card{overflow:hidden}.compact-table{width:100%}.compact-table :deep(.el-table__cell){padding:7px 0}.compact-table :deep(.cell){line-height:1.35;overflow:hidden;text-overflow:ellipsis}.compact-table :deep(tbody tr){cursor:pointer}.nowrap{white-space:nowrap}.token-cell{display:grid;gap:2px;min-width:0}.token-main{font-size:14px;line-height:1.2}.token-cell small{color:var(--settings-secondary);font-size:10px;line-height:1.25;white-space:nowrap}.detail-link{padding:0;font-size:12px}.pagination{margin-top:14px}.pagination>span{white-space:nowrap}.detail-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;padding-bottom:16px;border-bottom:1px solid var(--settings-line)}.detail-summary>div,.detail-metrics>div{display:grid;gap:4px}.detail-summary span,.detail-metrics span{color:var(--settings-secondary);font-size:12px}.detail-summary strong{font-size:14px}.detail-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;padding:15px 0}.detail-metrics>div{padding:11px 12px;border:1px solid var(--settings-line);border-radius:9px;background:var(--settings-surface)}.detail-metrics strong{font-size:18px}.detail-metrics small{color:var(--settings-secondary);font-size:10px}.detail-block{display:grid;gap:7px;margin-top:18px}.detail-block h3{margin:0;font-size:13px}.detail-block pre,.tool-row pre{max-height:220px;margin:0;padding:11px 12px;overflow:auto;border:1px solid var(--settings-line);border-radius:8px;background:var(--settings-surface-soft);color:var(--settings-text);font:12px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;word-break:break-word}.tool-row{display:grid;gap:7px;padding:10px 0;border-top:1px solid var(--settings-line)}.tool-row>div{display:flex;align-items:center;gap:8px}.log-detail-drawer :deep(.el-drawer__body){padding:18px;overflow:auto}.log-detail-drawer :deep(.el-drawer__header){margin-bottom:0;padding:18px;border-bottom:1px solid var(--settings-line)}@media(max-width:620px){.section-heading,.filters,.pagination{align-items:stretch;flex-direction:column}.section-heading h2{font-size:25px}.filters .el-select,.filters .el-input,.filters .el-button{width:100%;margin-left:0}.config-card :deep(.el-card__header),.config-card :deep(.el-card__body){padding:16px}.pagination>span{text-align:center}.detail-summary,.detail-metrics{grid-template-columns:1fr 1fr}.compact-table :deep(.el-table__cell){padding:6px 0}}
</style>
