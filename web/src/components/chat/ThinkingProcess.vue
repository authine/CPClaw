<template>
  <section class="thinking-process" aria-live="polite">
    <div class="thinking-process__header">
      <div class="thinking-process__heading">
        <el-icon :class="{ 'is-rotating': streaming }">
          <Loading v-if="streaming" />
          <CircleCheck v-else />
        </el-icon>
        <span>思考过程</span>
      </div>
      <span class="thinking-process__state">{{ stateText }}</span>
    </div>

    <div class="thinking-process__steps">
      <article v-for="step in steps" :key="step.id" class="thinking-step">
        <div class="thinking-step__rail">
          <el-icon :class="['thinking-step__icon', `is-${step.status}`, { 'is-rotating': step.status === 'running' }]">
            <Loading v-if="step.status === 'running'" />
            <CircleClose v-else-if="step.status === 'failed'" />
            <WarningFilled v-else-if="step.status === 'warning'" />
            <CircleCheck v-else />
          </el-icon>
          <span class="thinking-step__line" />
        </div>

        <div class="thinking-step__body">
          <div class="thinking-step__title">{{ step.title }}</div>
          <p class="thinking-step__process">{{ step.process }}</p>
          <div class="thinking-step__conclusion">
            <span>结论</span>
            <p>{{ step.conclusion }}</p>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CircleCheck, CircleClose, Loading, WarningFilled } from '@element-plus/icons-vue'
import type { ExecutionStep } from '../../types/agent'

const props = defineProps<{
  steps: ExecutionStep[]
  streaming: boolean
  completed: boolean
}>()

const stateText = computed(() => {
  const runningStep = props.steps.find((step) => step.status === 'running')
  if (runningStep) {
    return runningStep.title
  }
  if (props.streaming) {
    return '正在组织回答'
  }
  return props.completed ? `${props.steps.length} 个步骤已完成` : '执行未完成'
})
</script>

<style scoped>
.thinking-process {
  margin-bottom: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid #e4e7ec;
}

.thinking-process__header,
.thinking-process__heading {
  display: flex;
  align-items: center;
}

.thinking-process__header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.thinking-process__heading {
  gap: 8px;
  color: #344054;
  font-size: 14px;
  font-weight: 650;
}

.thinking-process__state {
  color: #667085;
  font-size: 12px;
}

.thinking-process__steps {
  display: grid;
  gap: 2px;
}

.thinking-step {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 10px;
}

.thinking-step__rail {
  display: grid;
  grid-template-rows: 20px 1fr;
  justify-items: center;
}

.thinking-step__icon {
  color: #12b76a;
  font-size: 17px;
}

.thinking-step__icon.is-running {
  color: #2970ff;
}

.thinking-step__icon.is-warning {
  color: #f79009;
}

.thinking-step__icon.is-failed {
  color: #f04438;
}

.thinking-step__line {
  width: 1px;
  min-height: 14px;
  margin: 3px 0;
  background: #d0d5dd;
}

.thinking-step:last-child .thinking-step__line {
  visibility: hidden;
}

.thinking-step__body {
  min-width: 0;
  padding-bottom: 14px;
}

.thinking-step__title {
  color: #1d2939;
  font-size: 14px;
  font-weight: 650;
  line-height: 20px;
}

.thinking-step__process,
.thinking-step__conclusion p {
  margin: 5px 0 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.thinking-step__conclusion {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
}

.thinking-step__conclusion > span {
  align-self: start;
  padding-top: 2px;
  color: #475467;
  font-size: 12px;
  font-weight: 650;
}

.thinking-step__conclusion p {
  margin: 0;
  color: #344054;
}

.is-rotating {
  animation: thinking-rotate 1s linear infinite;
}

@keyframes thinking-rotate {
  to {
    transform: rotate(360deg);
  }
}
</style>
