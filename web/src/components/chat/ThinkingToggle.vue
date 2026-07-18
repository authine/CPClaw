<template>
  <el-select
    class="thinking-selector"
    :model-value="modelValue ? 'deep' : 'fast'"
    :disabled="!supported"
    size="small"
    aria-label="选择思考深度"
    @update:model-value="emit('update:modelValue', $event === 'deep')"
  >
    <template #prefix><el-icon><MagicStick /></el-icon></template>
    <el-option label="快速回答" value="fast" />
    <el-option label="深度思考" value="deep" />
  </el-select>
</template>

<script setup lang="ts">
import { MagicStick } from '@element-plus/icons-vue'

defineProps<{
  modelValue: boolean
  supported: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>

<style scoped>
.thinking-selector {
  width: 108px;
}

.thinking-selector :deep(.el-select__wrapper) {
  min-height: 28px;
  padding: 0 8px;
  border-radius: 14px;
  background: var(--chat-brand-soft, rgba(79, 110, 247, 0.1));
  box-shadow: none;
  color: var(--chat-brand-strong, #3f5ce0);
  font-size: 11.5px;
}

.thinking-selector :deep(.el-select__selected-item) {
  color: inherit;
  font-weight: 600;
}

.thinking-selector :deep(.el-select__prefix) {
  color: var(--chat-brand, #4f6ef7);
}

.thinking-selector :deep(.el-select__caret) {
  width: 12px;
  color: var(--chat-text-tertiary, #98a2b3);
}
</style>
