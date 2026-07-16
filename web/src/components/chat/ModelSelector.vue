<template>
  <el-select
    class="model-selector"
    :model-value="modelValue"
    placeholder="选择模型"
    size="small"
    @update:model-value="emit('update:modelValue', String($event))"
  >
    <template #prefix><el-icon><Cpu /></el-icon></template>
    <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id" />
  </el-select>
</template>

<script setup lang="ts">
import { Cpu } from '@element-plus/icons-vue'
import type { ModelConfigSummary } from '../../types/settings'

defineProps<{
  modelValue: string
  models: ModelConfigSummary[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<style scoped>
.model-selector {
  width: 128px;
}

.model-selector :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 0 8px;
  border-radius: 14px;
  background: transparent;
  box-shadow: none;
  color: var(--chat-text-secondary, #657084);
  font-size: 11.5px;
}

.model-selector :deep(.el-select__selected-item) {
  color: inherit;
  font-weight: 600;
}

.model-selector :deep(.el-select__prefix) {
  color: var(--chat-brand, #4f6ef7);
}

.model-selector :deep(.el-select__caret) {
  width: 12px;
  color: var(--chat-text-tertiary, #98a2b3);
}
</style>
