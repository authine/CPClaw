<template>
  <el-switch
    :model-value="modelValue"
    :disabled="!supported"
    active-text="思考模式"
    @update:model-value="emit('update:modelValue', Boolean($event))"
  />
</template>

<script setup lang="ts">
defineProps<{
  modelValue: boolean
  supported: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()
</script>
