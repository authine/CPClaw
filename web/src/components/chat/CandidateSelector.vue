<template>
  <el-card shadow="never">
    <template #header>候选功能</template>
    <el-table :data="candidates" size="small">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" width="120" />
      <el-table-column prop="reason" label="匹配原因" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import type { CandidateOption } from '../../types/agent'

defineProps<{
  candidates: CandidateOption[]
}>()
</script>
