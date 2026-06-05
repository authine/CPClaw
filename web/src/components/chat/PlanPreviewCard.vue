<template>
  <el-card shadow="never">
    <template #header>执行计划预览</template>
    <p>{{ summary }}</p>
  </el-card>
</template>

<script setup lang="ts">
defineProps<{
  summary: string
}>()
</script>
