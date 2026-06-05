<template>
  <el-select :model-value="modelValue" placeholder="选择模型" style="width: 220px" @update:model-value="emit('update:modelValue', String($event))">
    <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id" />
  </el-select>
</template>

<script setup lang="ts">
import type { ModelConfigSummary } from '../../types/settings'

defineProps<{
  modelValue: string
  models: ModelConfigSummary[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>
