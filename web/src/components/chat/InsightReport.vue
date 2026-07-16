<template>
  <section class="insight-report" :aria-label="report.title || '智能问数报告'">
    <header class="report-header">
      <div class="report-heading">
        <span class="report-heading__eyebrow">智能问数报告</span>
        <h2>{{ report.title || report.subject || '数据洞察' }}</h2>
        <p v-if="report.subject">{{ report.subject }}</p>
      </div>
      <div class="report-confidence">
        <span>置信度</span>
        <strong>{{ report.confidence || '未标注' }}</strong>
      </div>
    </header>

    <div v-if="report.periodLabel || report.scopeLabel" class="report-context">
      <span v-if="report.periodLabel">{{ report.periodLabel }}</span>
      <span v-if="report.scopeLabel">{{ report.scopeLabel }}</span>
    </div>

    <div v-if="report.warnings.length" class="report-warnings" role="alert">
      <div class="report-section-title">
        <el-icon><WarningFilled /></el-icon>
        <span>数据提示</span>
      </div>
      <ul>
        <li v-for="warning in report.warnings" :key="warning">{{ warning }}</li>
      </ul>
    </div>

    <div v-if="report.kpis.length" class="kpi-grid">
      <article v-for="kpi in report.kpis" :key="`${kpi.label}-${kpi.value}`" :class="['kpi-item', toneClass(kpi.tone)]">
        <span class="kpi-item__label">{{ kpi.label }}</span>
        <div class="kpi-item__value">
          <strong>{{ kpi.value }}</strong>
          <span v-if="kpi.unit">{{ kpi.unit }}</span>
        </div>
        <p v-if="kpi.description">{{ kpi.description }}</p>
      </article>
    </div>

    <div v-if="visibleCharts.length" class="chart-list">
      <article v-for="chart in visibleCharts" :key="chart.id" class="chart-panel">
        <div class="chart-panel__header">
          <h3>{{ chart.title }}</h3>
          <span v-if="chart.unit">单位：{{ chart.unit }}</span>
        </div>
        <p v-if="chart.description" class="chart-panel__description">{{ chart.description }}</p>

        <div v-if="chart.type === 'bar'" class="bar-chart" role="img" :aria-label="chart.title">
          <div v-if="chart.series.length > 1" class="chart-legend">
            <span v-for="(series, seriesIndex) in chart.series" :key="series.name">
              <i :style="{ backgroundColor: chartColor(seriesIndex) }"></i>{{ series.name }}
            </span>
          </div>
          <div v-for="(label, labelIndex) in chart.labels" :key="`${chart.id}-${label}-${labelIndex}`" class="bar-group">
            <span class="bar-group__label" :title="label">{{ label }}</span>
            <div class="bar-group__series">
              <div v-for="(series, seriesIndex) in chart.series" :key="`${series.name}-${labelIndex}`" class="bar-row">
                <div class="bar-row__track">
                  <span
                    class="bar-row__fill"
                    :style="{
                      width: barWidth(series.values[labelIndex], chart),
                      backgroundColor: chartColor(seriesIndex)
                    }"
                  ></span>
                </div>
                <strong>{{ formatValue(series.values[labelIndex]) }}</strong>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="chart.type === 'donut'" class="donut-chart" role="img" :aria-label="chart.title">
          <div class="donut-chart__visual" :style="{ background: donutGradient(chart) }">
            <div class="donut-chart__center">
              <strong>{{ formatValue(donutTotal(chart)) }}</strong>
              <span>{{ chart.unit || chart.series[0]?.name || '合计' }}</span>
            </div>
          </div>
          <div class="donut-legend">
            <div v-for="(label, index) in chart.labels" :key="`${chart.id}-${label}-${index}`">
              <span><i :style="{ backgroundColor: chartColor(index) }"></i>{{ label }}</span>
              <strong>{{ formatValue(chart.series[0]?.values[index]) }}</strong>
            </div>
          </div>
        </div>

        <div v-else-if="chart.type === 'line'" class="line-chart" role="img" :aria-label="chart.title">
          <svg viewBox="0 0 640 220" preserveAspectRatio="none" aria-hidden="true">
            <line v-for="offset in [40, 80, 120, 160, 200]" :key="offset" x1="20" :y1="offset" x2="620" :y2="offset" class="line-chart__grid" />
            <polyline :points="linePoints(chart)" class="line-chart__path" />
            <g v-for="(value, index) in lineValues(chart)" :key="`${chart.id}-point-${index}`">
              <circle :cx="linePointX(index, chart.labels.length)" :cy="linePointY(value, chart)" r="5" class="line-chart__point">
                <title>{{ chart.labels[index] }}：{{ formatValue(value) }}{{ chart.unit }}</title>
              </circle>
            </g>
          </svg>
          <div class="line-chart__labels" :style="{ '--line-label-count': String(Math.max(chart.labels.length, 1)) }">
            <span v-for="(label, index) in chart.labels" :key="`${chart.id}-label-${index}`" :title="`${label}：${formatValue(lineValues(chart)[index])}${chart.unit}`">
              {{ label }}
            </span>
          </div>
        </div>

        <div v-else class="funnel-chart" role="img" :aria-label="chart.title">
          <div v-for="(label, index) in chart.labels" :key="`${chart.id}-${label}-${index}`" class="funnel-row">
            <span class="funnel-row__label" :title="label">{{ label }}</span>
            <div class="funnel-row__track">
              <span
                class="funnel-row__fill"
                :style="{
                  width: funnelWidth(chart.series[0]?.values[index], chart),
                  backgroundColor: chartColor(index)
                }"
              ></span>
            </div>
            <div class="funnel-row__value">
              <strong>{{ formatValue(chart.series[0]?.values[index]) }}</strong>
              <span v-if="chart.series[1]">当前 {{ formatValue(chart.series[1]?.values[index]) }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>

    <footer v-if="report.dataSources.length || report.relatedQuestions.length" class="report-footer">
      <div v-if="report.dataSources.length" class="data-sources">
        <div class="report-section-title">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据来源</span>
        </div>
        <p>{{ report.dataSources.join(' · ') }}</p>
      </div>
      <div v-if="report.relatedQuestions.length" class="related-questions">
        <span>继续追问</span>
        <div>
          <button
            v-for="question in report.relatedQuestions"
            :key="question"
            type="button"
            @click.stop="emit('question', question)"
          >
            <span>{{ question }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
      </div>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRight, DataAnalysis, WarningFilled } from '@element-plus/icons-vue'
import type { InsightChart, InsightReport } from '../../types/agent'

const props = defineProps<{ report: InsightReport }>()
const emit = defineEmits<{ question: [question: string] }>()

const palette = ['#2563eb', '#16a34a', '#f59e0b', '#e11d48', '#0891b2', '#7c3aed']
const visibleCharts = computed(() => props.report.charts.slice(0, 3))

function toneClass(tone: string) {
  const value = tone.toLowerCase()
  if (['positive', 'success', 'good', 'up'].includes(value)) return 'kpi-item--positive'
  if (['warning', 'warn', 'caution'].includes(value)) return 'kpi-item--warning'
  if (['negative', 'danger', 'bad', 'down'].includes(value)) return 'kpi-item--negative'
  return 'kpi-item--neutral'
}

function chartColor(index: number) {
  return palette[index % palette.length]
}

function finiteValue(value: number | undefined) {
  return Number.isFinite(value) ? Number(value) : 0
}

function formatValue(value: number | undefined) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(finiteValue(value))
}

function chartMaximum(chart: InsightChart) {
  return Math.max(0, ...chart.series.flatMap((series) => series.values.map(finiteValue)))
}

function barWidth(value: number | undefined, chart: InsightChart) {
  const maximum = chartMaximum(chart)
  const current = finiteValue(value)
  return maximum > 0 && current > 0 ? `${Math.max(2, current / maximum * 100)}%` : '0%'
}

function donutValues(chart: InsightChart) {
  return chart.labels.map((_, index) => Math.max(0, finiteValue(chart.series[0]?.values[index])))
}

function donutTotal(chart: InsightChart) {
  return donutValues(chart).reduce((total, value) => total + value, 0)
}

function donutGradient(chart: InsightChart) {
  const values = donutValues(chart)
  const total = values.reduce((sum, value) => sum + value, 0)
  if (total <= 0) {
    return '#e4e7ec'
  }
  let offset = 0
  const stops = values.map((value, index) => {
    const start = offset
    offset += value / total * 100
    return `${chartColor(index)} ${start}% ${offset}%`
  })
  return `conic-gradient(${stops.join(', ')})`
}

function funnelWidth(value: number | undefined, chart: InsightChart) {
  const maximum = Math.max(0, ...donutValues(chart))
  const current = finiteValue(value)
  return maximum > 0 && current > 0 ? `${Math.max(8, current / maximum * 100)}%` : '0%'
}

function lineValues(chart: InsightChart) {
  return chart.labels.map((_, index) => Math.max(0, finiteValue(chart.series[0]?.values[index])))
}

function linePointX(index: number, count: number) {
  if (count <= 1) return 320
  return 20 + index * 600 / (count - 1)
}

function linePointY(value: number | undefined, chart: InsightChart) {
  const maximum = Math.max(0, ...lineValues(chart))
  return maximum > 0 ? 200 - finiteValue(value) / maximum * 160 : 200
}

function linePoints(chart: InsightChart) {
  return lineValues(chart)
    .map((value, index) => `${linePointX(index, chart.labels.length)},${linePointY(value, chart)}`)
    .join(' ')
}
</script>

<style scoped>
.insight-report {
  display: grid;
  gap: 22px;
  width: 100%;
  min-width: 0;
  margin-top: 18px;
  padding: 22px 0;
  border-top: 1px solid #dfe3e8;
  border-bottom: 1px solid #dfe3e8;
  color: #17202d;
}

.report-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.report-heading {
  min-width: 0;
}

.report-heading__eyebrow,
.report-section-title,
.related-questions > span {
  color: #52606d;
  font-size: 12px;
  font-weight: 700;
}

.report-heading h2 {
  margin: 5px 0 0;
  font-size: 22px;
  line-height: 1.35;
  letter-spacing: 0;
}

.report-heading p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 14px;
  line-height: 1.6;
}

.report-confidence {
  display: grid;
  flex: 0 0 auto;
  gap: 3px;
  min-width: 88px;
  padding-left: 16px;
  border-left: 3px solid #2563eb;
}

.report-confidence span {
  color: #667085;
  font-size: 11px;
}

.report-confidence strong {
  font-size: 15px;
}

.report-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.report-context span {
  padding: 5px 9px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #f8fafc;
  color: #475467;
  font-size: 12px;
}

.report-warnings {
  padding: 14px 16px;
  border-left: 4px solid #f59e0b;
  background: #fffbeb;
}

.report-section-title {
  display: flex;
  align-items: center;
  gap: 7px;
}

.report-warnings ul {
  display: grid;
  gap: 7px;
  margin: 9px 0 0;
  padding-left: 20px;
}

.report-warnings li {
  color: #475467;
  font-size: 13px;
  line-height: 1.65;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(180px, 100%), 1fr));
  gap: 10px;
}

.kpi-item {
  position: relative;
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
  padding: 15px 16px 14px;
  overflow: hidden;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
}

.kpi-item::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: #2563eb;
}

.kpi-item--positive::before { background: #16a34a; }
.kpi-item--warning::before { background: #f59e0b; }
.kpi-item--negative::before { background: #e11d48; }

.kpi-item__label {
  overflow-wrap: anywhere;
  color: #667085;
  font-size: 12px;
  font-weight: 600;
}

.kpi-item__value {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 6px;
}

.kpi-item__value strong {
  overflow-wrap: anywhere;
  font-size: 24px;
  line-height: 1.1;
}

.kpi-item__value span,
.kpi-item p {
  color: #667085;
  font-size: 12px;
}

.kpi-item p {
  margin: 0;
  line-height: 1.5;
}

.chart-list {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.chart-panel {
  min-width: 0;
  padding-top: 18px;
  border-top: 1px solid #eaecf0;
}

.chart-panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.chart-panel__description {
  margin: -8px 0 18px;
  color: #667085;
  font-size: 11.5px;
  line-height: 1.6;
}

.chart-panel h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.4;
  letter-spacing: 0;
}

.chart-panel__header span {
  flex: 0 0 auto;
  color: #98a2b3;
  font-size: 11px;
}

.chart-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-bottom: 14px;
  color: #667085;
  font-size: 11px;
}

.chart-legend span,
.donut-legend span {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 6px;
}

.chart-legend i,
.donut-legend i {
  flex: 0 0 auto;
  width: 9px;
  height: 9px;
  border-radius: 2px;
}

.bar-chart,
.funnel-chart {
  display: grid;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.bar-group {
  display: grid;
  grid-template-columns: minmax(72px, 110px) minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.bar-group__label,
.funnel-row__label {
  overflow: hidden;
  color: #475467;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-group__series {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.bar-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(44px, auto);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.bar-row__track,
.funnel-row__track {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  border-radius: 4px;
  background: #f2f4f7;
}

.bar-row__track {
  height: 9px;
}

.bar-row__fill,
.funnel-row__fill {
  display: block;
  max-width: 100%;
  height: 100%;
  border-radius: inherit;
}

.bar-row strong,
.funnel-row__value strong,
.donut-legend strong {
  color: #344054;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.funnel-row__value {
  display: grid;
  justify-items: end;
  gap: 2px;
  min-width: 52px;
}

.funnel-row__value span {
  color: #98a2b3;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.donut-chart {
  display: grid;
  grid-template-columns: minmax(140px, 190px) minmax(0, 1fr);
  align-items: center;
  gap: clamp(22px, 6vw, 54px);
  min-width: 0;
}

.donut-chart__visual {
  display: grid;
  place-items: center;
  width: min(100%, 190px);
  aspect-ratio: 1;
  justify-self: center;
  border-radius: 50%;
}

.donut-chart__center {
  display: grid;
  place-items: center;
  width: 58%;
  aspect-ratio: 1;
  padding: 10px;
  border-radius: 50%;
  background: #fff;
  text-align: center;
}

.donut-chart__center strong {
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: 19px;
}

.donut-chart__center span {
  color: #667085;
  font-size: 10px;
}

.donut-legend {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.donut-legend > div {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f2f4f7;
  color: #475467;
  font-size: 12px;
}

.donut-legend span {
  overflow-wrap: anywhere;
}

.funnel-row {
  display: grid;
  grid-template-columns: minmax(72px, 110px) minmax(0, 1fr) minmax(44px, auto);
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.funnel-row__track {
  display: flex;
  justify-content: center;
  height: 24px;
  background: #f8fafc;
}

.line-chart {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.line-chart svg {
  width: 100%;
  height: 220px;
  overflow: visible;
}

.line-chart__grid {
  stroke: #eaecf0;
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.line-chart__path {
  fill: none;
  stroke: #2563eb;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.line-chart__point {
  fill: #fff;
  stroke: #2563eb;
  stroke-width: 3;
  vector-effect: non-scaling-stroke;
}

.line-chart__labels {
  display: grid;
  grid-template-columns: repeat(var(--line-label-count, 1), minmax(0, 1fr));
  min-width: 0;
}

.line-chart__labels span {
  overflow: hidden;
  color: #667085;
  font-size: 10px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.report-footer {
  display: grid;
  gap: 18px;
  padding-top: 18px;
  border-top: 1px solid #eaecf0;
}

.data-sources p {
  margin: 7px 0 0;
  overflow-wrap: anywhere;
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.related-questions {
  display: grid;
  gap: 9px;
}

.related-questions > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.related-questions button {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  gap: 7px;
  padding: 7px 10px;
  border: 1px solid #b8c5d6;
  border-radius: 6px;
  background: #fff;
  color: #175cd3;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  line-height: 1.45;
  text-align: left;
}

.related-questions button span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.related-questions button .el-icon {
  flex: 0 0 auto;
}

.related-questions button:hover,
.related-questions button:focus-visible {
  border-color: #2563eb;
  background: #eff6ff;
  outline: none;
}

@media (max-width: 640px) {
  .insight-report {
    gap: 18px;
    padding: 18px 0;
  }

  .report-header,
  .chart-panel__header {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .report-confidence {
    width: 100%;
    padding: 7px 0 0;
    border-top: 2px solid #2563eb;
    border-left: 0;
  }

  .bar-group,
  .funnel-row {
    grid-template-columns: minmax(62px, 82px) minmax(0, 1fr) minmax(38px, auto);
    gap: 7px;
  }

  .bar-group {
    grid-template-columns: minmax(62px, 82px) minmax(0, 1fr);
  }

  .donut-chart {
    grid-template-columns: 1fr;
  }

  .donut-chart__visual {
    width: min(62vw, 180px);
  }
}
</style>
