<template>
  <section class="usage-dashboard">
    <div class="section-heading">
      <div>
        <div class="section-kicker">智能与安全 / 用量看板</div>
        <h2>Token 用量看板</h2>
        <p>按时间和模型查看真实返回的 Token 使用量，辅助定位高消耗任务与模型；不包含费用估算。</p>
      </div>
      <el-button :loading="loading" @click="load"><el-icon><Refresh /></el-icon>刷新数据</el-button>
    </div>

    <el-alert v-if="errorMessage" type="warning" :closable="false" show-icon :title="errorMessage" />

    <div class="metric-grid">
      <div><span>调用次数</span><strong>{{ formatNumber(dashboard?.summary.invocations) }}</strong><small>当前已记录调用</small></div>
      <div><span>已返回用量</span><strong>{{ formatNumber(dashboard?.summary.usageRecorded) }}</strong><small>未返回 usage 的调用不估算</small></div>
      <div><span>总 Token</span><strong>{{ formatNumber(dashboard?.summary.totalTokens) }}</strong><small>输入 {{ formatNumber(dashboard?.summary.promptTokens) }} · 输出 {{ formatNumber(dashboard?.summary.completionTokens) }}</small></div>
      <div><span>缓存 Token</span><strong>{{ formatNumber(dashboard?.summary.cachedTokens) }}</strong><small>来自模型服务 usage 缓存字段</small></div>
    </div>

    <div class="dashboard-grid">
      <el-card class="config-card trend-card" shadow="never">
        <template #header><div class="card-title-row"><div><strong>Token 消耗趋势</strong><small>按执行时间聚合；柱高表示总 Token。</small></div><div class="granularity-tabs" role="tablist" aria-label="选择 Token 趋势时间粒度"><button v-for="item in granularityOptions" :key="item.value" :class="{ active: granularity === item.value }" type="button" role="tab" :aria-selected="granularity === item.value" @click="granularity = item.value">{{ item.label }}</button></div></div></template>
        <div v-if="series.length" class="trend-wrap">
          <svg viewBox="0 0 760 270" class="trend-chart" role="img" :aria-label="`${granularityLabel} Token 消耗趋势图`">
            <line v-for="level in 4" :key="level" class="chart-grid" x1="44" x2="742" :y1="26 + (level - 1) * 62" :y2="26 + (level - 1) * 62" />
            <text x="4" y="31" class="axis-text">{{ formatCompact(maxTokens) }}</text><text x="14" y="217" class="axis-text">0</text>
            <g v-for="(item, index) in series" :key="item.period" class="trend-bar">
              <title>{{ `${item.label}：总计 ${formatNumber(item.totalTokens)} Token；输入 ${formatNumber(item.promptTokens)}，输出 ${formatNumber(item.completionTokens)}，缓存 ${formatNumber(item.cachedTokens)}；${formatNumber(item.invocations)} 次调用` }}</title>
              <rect :x="barX(index)" :y="barY(item.totalTokens)" :width="barWidth" :height="barHeight(item.totalTokens)" rx="5" class="bar-total" />
              <rect v-if="item.cachedTokens > 0" :x="barX(index)" :y="barY(item.totalTokens)" :width="barWidth" height="4" rx="2" class="bar-cache" />
              <text :x="barX(index) + barWidth / 2" y="246" class="bar-label">{{ item.label }}</text>
            </g>
          </svg>
          <div class="chart-legend"><span><i class="legend-total" />总 Token</span><span><i class="legend-cache" />含缓存用量的调用</span><span>当前范围 {{ formatNumber(dashboard?.summary.usageRecorded) }} 次返回 usage</span></div>
        </div>
        <el-empty v-else description="暂无可聚合的 Token 使用记录" :image-size="72" />
      </el-card>

      <el-card class="config-card model-card" shadow="never">
        <template #header><div class="card-title-row"><div><strong>模型消耗分布</strong><small>按模型汇总总 Token 与调用次数。</small></div></div></template>
        <div v-if="models.length" class="model-list">
          <div v-for="model in models" :key="model.modelName" class="model-row">
            <div class="model-row__label"><strong>{{ model.modelName }}</strong><small>{{ formatNumber(model.invocations) }} 次调用 · {{ formatNumber(model.usageRecorded) }} 次返回 usage</small></div>
            <div class="model-row__meter"><span :style="{ width: `${modelWidth(model.totalTokens)}%` }" /></div>
            <strong class="model-row__value">{{ formatNumber(model.totalTokens) }}</strong>
          </div>
        </div>
        <el-empty v-else description="暂无模型用量记录" :image-size="72" />
      </el-card>
    </div>

    <el-card class="config-card composition-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>Token 构成</strong><small>缓存 Token 由模型服务单独返回，可能已包含在输入 Token 中，因此不与输入简单相加。</small></div></div></template>
      <div class="composition-grid"><div><span>输入 Token</span><strong>{{ formatNumber(dashboard?.summary.promptTokens) }}</strong></div><div><span>输出 Token</span><strong>{{ formatNumber(dashboard?.summary.completionTokens) }}</strong></div><div><span>缓存 Token</span><strong>{{ formatNumber(dashboard?.summary.cachedTokens) }}</strong></div><div><span>总 Token</span><strong>{{ formatNumber(dashboard?.summary.totalTokens) }}</strong></div></div>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTokenUsageDashboard, type TokenUsageDashboard, type TokenUsageModel, type TokenUsagePeriod } from '../../services/auditApi'

type Granularity = 'daily' | 'weekly' | 'monthly'
const dashboard = ref<TokenUsageDashboard>()
const loading = ref(false)
const errorMessage = ref('')
const granularity = ref<Granularity>('daily')
const granularityOptions: Array<{ value: Granularity; label: string }> = [{ value: 'daily', label: '按日' }, { value: 'weekly', label: '按周' }, { value: 'monthly', label: '按月' }]
const series = computed<TokenUsagePeriod[]>(() => (dashboard.value?.[granularity.value] ?? []).slice(granularity.value === 'daily' ? -14 : -12))
const models = computed<TokenUsageModel[]>(() => (dashboard.value?.models ?? []).slice(0, 8))
const maxTokens = computed(() => Math.max(1, ...series.value.map(item => item.totalTokens)))
const maxModelTokens = computed(() => Math.max(1, ...models.value.map(item => item.totalTokens)))
const granularityLabel = computed(() => ({ daily: '按日', weekly: '按周', monthly: '按月' })[granularity.value])
const barWidth = computed(() => Math.max(10, Math.min(34, 620 / Math.max(series.value.length, 1) - 8)))
load()
async function load() { loading.value = true; errorMessage.value = ''; try { dashboard.value = await getTokenUsageDashboard() } catch (error) { dashboard.value = undefined; errorMessage.value = error instanceof Error && error.message ? error.message : 'Token 用量看板加载失败'; ElMessage.warning(errorMessage.value) } finally { loading.value = false } }
function barX(index: number) { const step = 650 / Math.max(series.value.length, 1); return 62 + index * step + (step - barWidth.value) / 2 }
function barHeight(value: number) { return Math.max(value > 0 ? 4 : 0, Math.round(value / maxTokens.value * 184)) }
function barY(value: number) { return 220 - barHeight(value) }
function modelWidth(value: number) { return Math.max(value > 0 ? 3 : 0, Math.round(value / maxModelTokens.value * 100)) }
function formatNumber(value?: number) { return value === undefined || value === null ? '—' : new Intl.NumberFormat('zh-CN').format(value) }
function formatCompact(value: number) { return value >= 1000 ? `${(value / 1000).toFixed(value >= 10000 ? 0 : 1)}k` : String(value) }
</script>

<style scoped>
.usage-dashboard{display:grid;max-width:var(--settings-content-width);gap:var(--settings-section-gap)}.section-heading,.card-title-row{display:flex;align-items:center;justify-content:space-between;gap:16px}.section-heading{align-items:flex-start}.section-kicker{color:var(--settings-brand);font-size:12px;font-weight:700;letter-spacing:.08em}.section-heading h2{margin:8px 0 7px;font-size:28px;letter-spacing:-.025em;line-height:1.25}.section-heading p{max-width:760px;margin:0;color:var(--settings-secondary);font-size:14px;line-height:1.65}.config-card{border-color:var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface);box-shadow:none}.config-card :deep(.el-card__header){padding:16px 20px;border-color:var(--settings-line)}.config-card :deep(.el-card__body){padding:18px 20px}.card-title-row>div:first-child{display:grid;gap:4px}.card-title-row small{color:var(--settings-secondary);font-size:12px;line-height:1.5}.granularity-tabs{display:grid;flex:0 0 204px;grid-template-columns:repeat(3,minmax(0,1fr));gap:3px;padding:3px;border:1px solid var(--settings-line);border-radius:9px;background:var(--settings-surface-soft)}.granularity-tabs button{min-height:30px;border:0;border-radius:6px;background:transparent;color:var(--settings-secondary);font:600 12px/1 inherit;cursor:pointer;transition:background-color .16s ease,color .16s ease,box-shadow .16s ease}.granularity-tabs button:hover{color:var(--settings-text);background:var(--settings-surface)}.granularity-tabs button.active{background:var(--settings-surface);color:var(--settings-brand);box-shadow:0 1px 3px rgba(18,28,45,.12)}.granularity-tabs button:focus-visible{outline:2px solid var(--settings-brand);outline-offset:1px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric-grid>div,.composition-grid>div{display:grid;gap:5px;padding:15px 16px;border:1px solid var(--settings-line);border-radius:var(--settings-radius);background:var(--settings-surface)}.metric-grid span,.metric-grid small,.composition-grid span{color:var(--settings-secondary);font-size:12px}.metric-grid strong{font-size:25px;letter-spacing:-.02em}.dashboard-grid{display:grid;grid-template-columns:minmax(0,1.5fr) minmax(300px,.9fr);gap:16px}.trend-card,.model-card{min-height:350px}.trend-wrap{display:grid;gap:12px}.trend-chart{display:block;width:100%;min-height:250px;overflow:visible}.chart-grid{stroke:var(--settings-line);stroke-width:1}.axis-text,.bar-label{fill:var(--settings-tertiary);font-size:11px}.bar-label{text-anchor:middle}.bar-total{fill:var(--settings-brand)}.bar-cache{fill:#9e7bff}.trend-bar{cursor:default}.trend-bar:hover .bar-total{fill:var(--settings-brand-strong)}.chart-legend{display:flex;flex-wrap:wrap;gap:12px;color:var(--settings-secondary);font-size:12px}.chart-legend span{display:inline-flex;align-items:center;gap:5px}.chart-legend i{width:8px;height:8px;border-radius:50%}.legend-total{background:var(--settings-brand)}.legend-cache{background:#9e7bff}.model-list{display:grid;gap:15px}.model-row{display:grid;grid-template-columns:minmax(110px,1fr) minmax(80px,1.2fr) auto;align-items:center;gap:12px}.model-row__label{display:grid;min-width:0;gap:3px}.model-row__label strong{overflow:hidden;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.model-row__label small{color:var(--settings-secondary);font-size:11px}.model-row__meter{height:8px;overflow:hidden;border-radius:999px;background:var(--settings-surface-soft)}.model-row__meter span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,var(--settings-brand),#7d93ff)}.model-row__value{font-size:13px;font-variant-numeric:tabular-nums}.composition-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.composition-grid strong{font-size:20px;letter-spacing:-.02em}@media(max-width:900px){.metric-grid,.composition-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.dashboard-grid{grid-template-columns:1fr}}@media(max-width:620px){.section-heading,.card-title-row{align-items:stretch;flex-direction:column}.section-heading h2{font-size:25px}.section-heading .el-button{width:100%;margin-left:0}.granularity-tabs{width:100%;flex-basis:auto}.metric-grid,.composition-grid{grid-template-columns:1fr}.config-card :deep(.el-card__header),.config-card :deep(.el-card__body){padding:16px}.trend-chart{min-width:540px}.trend-wrap{overflow:auto;padding-bottom:4px}.model-row{grid-template-columns:minmax(105px,1fr) minmax(60px,1fr) auto;gap:8px}}
</style>
