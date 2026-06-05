<template>
  <el-alert type="warning" show-icon :closable="false">
    <template #title>高风险操作确认</template>
    <p>{{ summary }}</p>
    <el-button type="warning" size="small" @click="$emit('confirm')">确认执行</el-button>
  </el-alert>
</template>

<script setup lang="ts">
defineProps<{
  summary: string
}>()

defineEmits<{
  confirm: []
}>()
</script>
