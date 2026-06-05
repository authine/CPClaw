<template>
  <el-timeline>
    <el-timeline-item v-for="item in items" :key="item.title" :timestamp="item.status">
      {{ item.title }}
    </el-timeline-item>
  </el-timeline>
</template>

<script setup lang="ts">
import type { ExecutionStep } from '../../types/agent'

defineProps<{
  items: ExecutionStep[]
}>()
</script>
