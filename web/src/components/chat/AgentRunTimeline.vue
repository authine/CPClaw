<template>
  <ol :class="['agent-run', { 'agent-run--compact': compact }]">
    <li
      v-for="(step, index) in visibleSteps"
      :key="step.id || `${index}-${step.kind}-${step.title}`"
      :class="['agent-run__item', `agent-run__item--${stepKind(step)}`, `agent-run__item--${stepState(step)}`]"
    >
      <div class="agent-run__rail" aria-hidden="true">
        <span class="agent-run__icon">
          <el-icon v-if="stepState(step) === 'running'" class="is-loading"><Loading /></el-icon>
          <el-icon v-else-if="stepState(step) === 'cancelled'"><VideoPause /></el-icon>
          <el-icon v-else-if="stepState(step) === 'fallback' || stepState(step) === 'needs_input'"><WarningFilled /></el-icon>
          <el-icon v-else-if="stepKind(step) === 'execution'"><Connection /></el-icon>
          <el-icon v-else-if="stepKind(step) === 'thought'"><Cpu /></el-icon>
          <el-icon v-else><Check /></el-icon>
        </span>
      </div>

      <div class="agent-run__content">
        <div class="agent-run__heading">
          <strong>{{ step.title }}</strong>
          <span v-if="formatElapsed(step.elapsedMs)" class="agent-run__elapsed">{{ formatElapsed(step.elapsedMs) }}</span>
        </div>
        <p>{{ step.status }}</p>

        <div v-if="summaryEntries(step).length" class="agent-run__facts">
          <span v-for="entry in summaryEntries(step)" :key="entry.key">
            <small>{{ entry.label }}</small>
            <strong>{{ entry.value }}</strong>
          </span>
        </div>

        <details v-if="detailEntries(step).length" class="agent-run__details">
          <summary>查看请求与返回数据</summary>
          <dl>
            <template v-for="entry in detailEntries(step)" :key="entry.key">
              <dt>{{ entry.label }}</dt>
              <dd>{{ entry.value }}</dd>
            </template>
          </dl>
        </details>
      </div>
    </li>
  </ol>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check, Connection, Cpu, Loading, VideoPause, WarningFilled } from '@element-plus/icons-vue'
import type { ExecutionStep } from '../../types/agent'

const props = withDefaults(defineProps<{
  steps: ExecutionStep[]
  compact?: boolean
}>(), {
  compact: false
})

const visibleSteps = computed(() => compactTimeline(props.steps))

interface DisplayEntry {
  key: string
  label: string
  value: string
  summary: boolean
}

const labels: Record<string, string> = {
  entityName: '业务对象',
  schemaCode: '对象编码',
  objectType: '对象类型',
  appCode: '应用编码',
  apiOperation: '业务动作',
  sourceEndpoint: '调用接口',
  endpoint: '调用接口',
  total: '数据总数',
  runtimeTotal: '接口总数',
  returnedRecords: '返回记录',
  reportRecords: '分析记录',
  pageSize: '分页大小',
  recordLimit: '读取上限',
  entityCount: '关联对象',
  kpiCount: '指标数量',
  chartCount: '图表数量',
  mode: '生成方式',
  role: '链路角色',
  query: '用户问题',
  effectiveQuery: '有效问题',
  filters: '筛选条件',
  metricFields: '指标字段',
  resultSummary: '结果摘要',
  outputPreview: '回答摘要',
  thinkingElapsedMs: '思考耗时',
  answerElapsedMs: '回答耗时'
}

const priorityKeys = [
  'entityName', 'schemaCode', 'sourceEndpoint', 'endpoint', 'total', 'runtimeTotal',
  'returnedRecords', 'reportRecords', 'mode', 'kpiCount', 'chartCount'
]

function stepKind(step: ExecutionStep) {
  return step.kind || (step.data && Object.keys(step.data).length ? 'execution' : 'progress')
}

function stepState(step: ExecutionStep) {
  return step.state || 'completed'
}

function compactTimeline(steps: ExecutionStep[]) {
  return collapseLifecycleSteps(steps).filter((step) => (
    stepKind(step) !== 'answer_start' && stepKind(step) !== 'answer_end'
  ))
}

function collapseLifecycleSteps(steps: ExecutionStep[]) {
  return steps.reduce<ExecutionStep[]>((result, step) => {
    if (!isTerminalState(stepState(step))) {
      result.push(step)
      return result
    }

    const runningIndex = findRunningLifecycleStep(result, step)
    if (runningIndex >= 0) {
      result.splice(runningIndex, 1, step)
      return result
    }

    result.push(step)
    return result
  }, [])
}

function findRunningLifecycleStep(steps: ExecutionStep[], terminalStep: ExecutionStep) {
  for (let index = steps.length - 1; index >= 0; index -= 1) {
    const candidate = steps[index]
    if (stepKind(candidate) === 'progress') {
      continue
    }
    if (stepState(candidate) !== 'running') {
      return -1
    }
    return sameLifecycle(candidate, terminalStep) ? index : -1
  }
  return -1
}

function sameLifecycle(left: ExecutionStep, right: ExecutionStep) {
  if (left.id && right.id) {
    return left.id === right.id
  }
  return stepKind(left) === stepKind(right)
    && left.title === right.title
    && (left.phase || '') === (right.phase || '')
}

function isTerminalState(state: string) {
  return state === 'completed' || state === 'fallback' || state === 'needs_input' || state === 'failed' || state === 'cancelled'
}

function summaryEntries(step: ExecutionStep) {
  const entries = entriesFor(step).filter((entry) => entry.summary)
  const prioritized = priorityKeys
    .map((key) => entries.find((entry) => entry.key === key))
    .filter((entry): entry is DisplayEntry => Boolean(entry))
  const remaining = entries.filter((entry) => !priorityKeys.includes(entry.key))
  return [...prioritized, ...remaining].slice(0, props.compact ? 3 : 5)
}

function detailEntries(step: ExecutionStep) {
  const summaryKeys = new Set(summaryEntries(step).map((entry) => entry.key))
  return entriesFor(step).filter((entry) => !summaryKeys.has(entry.key))
}

function entriesFor(step: ExecutionStep): DisplayEntry[] {
  if (!step.data) {
    return []
  }
  return Object.entries(step.data)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => ({
      key,
      label: labels[key] || humanizeKey(key),
      value: formatValue(key, value),
      summary: isSummaryValue(key, value)
    }))
}

function isSummaryValue(key: string, value: unknown) {
  if (value && typeof value === 'object') {
    return false
  }
  const text = String(value)
  return priorityKeys.includes(key) || text.length <= 80
}

function humanizeKey(key: string) {
  return key.replace(/([A-Z])/g, ' $1').replace(/[_-]+/g, ' ').trim()
}

function formatValue(key: string, value: unknown) {
  if (key.endsWith('ElapsedMs')) {
    return formatElapsed(Number(value))
  }
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value)
  }
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

function formatElapsed(value?: number) {
  if (!value || value <= 0) {
    return ''
  }
  return value < 1000 ? `${Math.round(value)}ms` : `${(value / 1000).toFixed(2)}s`
}
</script>

<style scoped>
.agent-run {
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.agent-run__item {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  min-width: 0;
}

.agent-run__rail {
  position: relative;
  display: flex;
  justify-content: center;
}

.agent-run__rail::after {
  position: absolute;
  top: 28px;
  bottom: 0;
  width: 1px;
  background: #e4e7ec;
  content: '';
}

.agent-run__item:last-child .agent-run__rail::after {
  display: none;
}

.agent-run__icon {
  position: relative;
  z-index: 1;
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border: 1px solid #d0d5dd;
  border-radius: 50%;
  color: #667085;
  background: #fff;
  font-size: 12px;
}

.agent-run__item--execution .agent-run__icon {
  border-color: #b2ddff;
  color: #175cd3;
  background: #eff8ff;
}

.agent-run__item--thought .agent-run__icon {
  border-color: #d6bbfb;
  color: #6941c6;
  background: #f9f5ff;
}

.agent-run__item--running .agent-run__icon {
  border-color: #84adff;
  color: #155eef;
  background: #eff4ff;
}

.agent-run__item--fallback .agent-run__icon,
.agent-run__item--needs_input .agent-run__icon {
  border-color: #fedf89;
  color: #b54708;
  background: #fffaeb;
}

.agent-run__item--cancelled .agent-run__icon {
  border-color: #d0d5dd;
  color: #667085;
  background: #f2f4f7;
}

.agent-run__content {
  min-width: 0;
  padding: 0 0 18px 10px;
}

.agent-run__heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.agent-run__heading strong {
  min-width: 0;
  color: #1d2939;
  font-size: 13px;
  font-weight: 650;
  line-height: 20px;
}

.agent-run__elapsed {
  flex: 0 0 auto;
  color: #98a2b3;
  font-size: 11px;
}

.agent-run__content > p {
  margin: 3px 0 0;
  color: #667085;
  font-size: 12px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.agent-run__facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 8px;
  padding: 8px 10px;
  border-left: 2px solid #d0d5dd;
  background: #f8fafc;
}

.agent-run__facts span {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 5px;
}

.agent-run__facts small {
  color: #98a2b3;
  font-size: 11px;
}

.agent-run__facts strong {
  max-width: 260px;
  color: #344054;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  font-weight: 550;
  overflow-wrap: anywhere;
}

.agent-run__details {
  margin-top: 7px;
}

.agent-run__details summary {
  width: fit-content;
  color: #475467;
  cursor: pointer;
  font-size: 11px;
  user-select: none;
}

.agent-run__details dl {
  display: grid;
  grid-template-columns: minmax(80px, auto) minmax(0, 1fr);
  gap: 6px 12px;
  margin: 8px 0 0;
  padding: 10px;
  border: 1px solid #eaecf0;
  background: #fff;
  font-size: 11px;
}

.agent-run__details dt {
  color: #98a2b3;
}

.agent-run__details dd {
  min-width: 0;
  margin: 0;
  color: #344054;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  overflow-wrap: anywhere;
}

.agent-run--compact .agent-run__content {
  padding-bottom: 14px;
}

.agent-run--compact .agent-run__facts {
  padding: 7px 9px;
}

@media (max-width: 560px) {
  .agent-run__facts {
    display: grid;
    grid-template-columns: 1fr;
  }

  .agent-run__details dl {
    grid-template-columns: 1fr;
  }
}
</style>
