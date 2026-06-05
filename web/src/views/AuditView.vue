<template>
  <PageHeader title="审计" description="查看 Agent 执行、工具调用、确认记录和脱敏错误摘要。" />
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>Agent Run 查询</span>
        <div>
          <el-input v-model="agentRunId" class="search-input" placeholder="输入 Agent Run ID" />
          <el-button type="primary" @click="loadAudit">查询</el-button>
        </div>
      </div>
    </template>
    <el-empty v-if="!audit" description="发送一次对话后，可复制 Agent Run ID 到这里查看脱敏审计。" />
    <el-descriptions v-else :column="2" border>
      <el-descriptions-item label="ID">{{ audit.id }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ audit.status }}</el-descriptions-item>
      <el-descriptions-item label="意图">{{ audit.intent }}</el-descriptions-item>
      <el-descriptions-item label="风险等级">{{ audit.riskLevel }}</el-descriptions-item>
      <el-descriptions-item label="计划" :span="2">{{ audit.planJson }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="audit" :data="audit.tools" class="table">
      <el-table-column prop="toolName" label="工具" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="inputJsonMasked" label="输入（脱敏）" />
      <el-table-column prop="outputJsonMasked" label="输出（脱敏）" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import { getAgentRun } from '../services/auditApi'

interface AuditTool {
  id: string
  toolName: string
  status: string
  inputJsonMasked: string
  outputJsonMasked: string
}

interface AuditDetail {
  id: string
  status: string
  intent: string
  riskLevel: string
  planJson: string
  tools: AuditTool[]
}

const agentRunId = ref('')
const audit = ref<AuditDetail>()

async function loadAudit() {
  audit.value = await getAgentRun(agentRunId.value)
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.search-input {
  width: 360px;
  margin-right: 8px;
}

.table {
  margin-top: 16px;
}
</style>
