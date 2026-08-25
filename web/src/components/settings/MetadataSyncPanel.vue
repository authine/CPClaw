<template>
  <section class="metadata-panel">
    <div class="section-heading">
      <div>
        <div class="section-kicker">元数据管理 / 同步</div>
        <h2>同步云枢元数据</h2>
        <p>从管理员云枢环境同步应用、实体、字段、关联关系与可执行 API 能力。</p>
      </div>
      <el-tag :type="configured ? 'success' : 'warning'" effect="plain" round>{{ configured ? '管理员环境已配置' : '需先配置管理员环境' }}</el-tag>
    </div>

    <el-alert v-if="!configured" type="warning" :closable="false" show-icon title="请先完成管理员云枢环境配置并测试连接，再执行元数据同步。" />
    <el-alert v-else type="info" :closable="false" show-icon title="同步会刷新本地元数据和知识图谱快照，不会修改云枢业务数据。" />

    <el-card class="config-card metadata-sync-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>同步范围</strong><small>同步成功后即可在“元数据查看”中搜索和浏览。</small></div><el-button type="primary" :disabled="!configured" :loading="syncing" @click="sync"><el-icon><Refresh /></el-icon>开始同步</el-button></div></template>
      <div class="scope-grid"><div><strong>应用与实体</strong><span>业务对象、编码与归属应用</span></div><div><strong>字段与关系</strong><span>数据项、引用字段与实体关联</span></div><div><strong>API 与图谱</strong><span>能力动作与关联知识图谱</span></div></div>
      <el-alert v-if="syncResult" class="sync-result" type="success" :closable="false" show-icon :title="`同步完成：${syncResult.appCount} 个应用、${syncResult.entityCount} 个实体、${syncResult.dataItemCount} 个字段、${syncResult.graphEdgeCount} 条图谱边。`" />
    </el-card>

    <el-card class="config-card sync-history-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>同步日志</strong><small>按开始时间倒序记录每次同步，失败记录也会保留。</small></div><span v-if="logs.latestSuccessfulAt" class="latest-sync">最近一次成功：{{ formatDate(logs.latestSuccessfulAt) }}</span><span v-else class="latest-sync latest-sync--empty">尚无成功同步记录</span></div></template>
      <el-table v-loading="logsLoading" :data="logs.items" class="sync-log-table" size="small" empty-text="暂无同步记录">
        <el-table-column label="同步时间" min-width="168"><template #default="{ row }"><span>{{ formatDate(row.startedAt) }}</span><small v-if="row.completedAt" class="sub-cell">完成于 {{ formatClock(row.completedAt) }}</small></template></el-table-column>
        <el-table-column label="状态" width="92"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="同步范围" min-width="260"><template #default="{ row }"><span v-if="row.detailedCountsRecorded">{{ row.appCount }} 应用 · {{ row.entityCount }} 实体 · {{ row.dataItemCount }} 字段 · {{ row.relationCount }} 关系</span><span v-else>{{ row.appCount }} 个应用 · 历史快照未保存对象明细</span><small class="sub-cell">{{ row.graphNodeCount }} 个图谱节点 · {{ row.graphEdgeCount }} 条图谱边</small></template></el-table-column>
        <el-table-column label="耗时" width="92"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
        <el-table-column label="结果" min-width="220" show-overflow-tooltip><template #default="{ row }"><span :class="{ 'error-cell': row.status === 'failed' }">{{ row.errorMessage || (row.status === 'succeeded' ? '同步完成，本地元数据已更新' : '同步未完成') }}</span></template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getMetadataSyncLogs, syncMetadata } from '../../services/metadataApi'
import type { MetadataSyncLogOverview, MetadataSyncResponse } from '../../types/metadata'

defineProps<{ configured: boolean }>()
const emit = defineEmits<{ synced: [] }>()
const syncing = ref(false)
const syncResult = ref<MetadataSyncResponse>()
const logs = ref<MetadataSyncLogOverview>({ items: [] })
const logsLoading = ref(false)
onMounted(() => { void loadLogs() })

async function sync() {
  syncing.value = true
  try {
    syncResult.value = await syncMetadata()
    await loadLogs()
    ElMessage.success('元数据同步完成')
    emit('synced')
  } catch (error) {
    ElMessage.error(error instanceof Error && error.message ? error.message : '元数据同步失败')
  } finally {
    syncing.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  try { logs.value = await getMetadataSyncLogs() } catch { /* The sync action remains usable if history is unavailable. */ } finally { logsLoading.value = false }
}

function formatDate(value?: string) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—' }
function formatClock(value?: string) { return value ? new Date(value).toLocaleTimeString('zh-CN', { hour12: false }) : '—' }
function formatDuration(value?: number) { return value === undefined || value === null ? '—' : value >= 1000 ? `${(value / 1000).toFixed(1)}s` : `${Math.round(value)}ms` }
function statusLabel(value: string) { return ({ running: '同步中', succeeded: '已完成', failed: '失败', incomplete: '未完成' } as Record<string, string>)[value] || '未知' }
function statusType(value: string) { return value === 'succeeded' ? 'success' : value === 'failed' ? 'danger' : 'warning' }
</script>

<style scoped>
.metadata-panel { display:grid; max-width:var(--settings-content-width); gap:var(--settings-section-gap); }
.section-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; }
.section-heading h2 { margin:8px 0 7px; font-size:28px; letter-spacing:-.025em; line-height:1.25; }
.section-heading p { max-width:760px; margin:0; color:var(--settings-secondary); font-size:14px; line-height:1.65; }
.section-kicker { color:var(--settings-brand); font-size:12px; font-weight:800; letter-spacing:.08em; }
.config-card { border-color:var(--settings-line); border-radius:var(--settings-radius); background:var(--settings-surface); box-shadow:none; }
.config-card :deep(.el-card__header) { padding:18px 22px; border-color:var(--settings-line); }.config-card :deep(.el-card__body) { padding:22px; }
.card-title-row { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }.card-title-row>div { display:grid; gap:4px; }.card-title-row small { color:var(--settings-secondary); font-size:12px; line-height:1.5; }
.scope-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:14px; }.scope-grid>div { display:grid; gap:6px; padding:18px; border:1px solid var(--settings-line); border-radius:10px; background:color-mix(in srgb,var(--settings-surface) 92%,var(--settings-brand)); }.scope-grid span { color:var(--settings-secondary); font-size:12px; line-height:1.5; }.sync-result { margin-top:18px; }.sync-history-card :deep(.el-card__body) { padding:0; }.latest-sync { color:var(--settings-secondary); font-size:12px; white-space:nowrap; }.latest-sync--empty { color:var(--settings-tertiary); }.sync-log-table { width:100%; }.sync-log-table :deep(.el-table__cell) { padding:9px 0; }.sync-log-table :deep(.cell) { line-height:1.4; }.sub-cell { display:block; margin-top:3px; color:var(--settings-tertiary); font-size:11px; line-height:1.35; }.error-cell { color:#d14343; }
@media(max-width:800px) { .section-heading { flex-direction:column; gap:12px; }.scope-grid { grid-template-columns:1fr; } }
@media(max-width:620px) { .section-heading h2 { font-size:25px; }.config-card :deep(.el-card__header),.config-card :deep(.el-card__body) { padding:18px; }.card-title-row { flex-direction:column; }.card-title-row .el-button { width:100%; margin-left:0; }.latest-sync { white-space:normal; }.sync-log-table { min-width:760px; }.sync-history-card :deep(.el-card__body) { overflow-x:auto; } }
</style>
