<template>
  <PageHeader title="审计" description="查看 Agent 执行、工具调用、确认记录和脱敏错误摘要。" />

  <el-alert v-if="errorMessage" class="page-error" type="error" show-icon :closable="false" :title="errorMessage" />

  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>Agent Run 查询</span>
        <div class="card-actions">
          <el-input v-model="agentRunId" class="search-input" clearable placeholder="输入 Agent Run ID" @keydown.enter="loadAudit" />
          <el-button type="primary" :loading="loading" @click="loadAudit">
            <el-icon><Search /></el-icon>
            <span>查询</span>
          </el-button>
        </div>
      </div>
    </template>
    <el-empty v-if="!audit && !loading" description="发送一次对话后，可复制 Agent Run ID 到这里查看脱敏审计。" />
    <div v-loading="loading">
      <el-result v-if="audit?.status === 'not-found'" icon="warning" title="未找到审计记录" sub-title="请检查 Agent Run ID 是否完整。" />
      <template v-else-if="audit">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ audit.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ audit.status }}</el-descriptions-item>
          <el-descriptions-item label="意图">{{ audit.intent }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">{{ audit.riskLevel }}</el-descriptions-item>
          <el-descriptions-item label="计划" :span="2">{{ audit.planJson }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="audit.tools" class="table" empty-text="暂无工具调用">
          <el-table-column prop="toolName" label="工具" width="180" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="inputJsonMasked" label="输入（脱敏）" min-width="260" show-overflow-tooltip />
          <el-table-column prop="outputJsonMasked" label="输出（脱敏）" min-width="260" show-overflow-tooltip />
        </el-table>
      </template>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import PageHeader from '../components/common/PageHeader.vue'
import { getAgentRun } from '../services/auditApi'
import type { AuditDetail } from '../services/auditApi'

const agentRunId = ref('')
const audit = ref<AuditDetail>()
const loading = ref(false)
const errorMessage = ref('')

async function loadAudit() {
  const id = agentRunId.value.trim()
  if (!id) {
    ElMessage.warning('请输入 Agent Run ID')
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    audit.value = await getAgentRun(id)
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.card-header,
.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  justify-content: space-between;
}

.search-input {
  width: 360px;
}

.page-error {
  margin-bottom: 16px;
}

.table {
  margin-top: 16px;
}

@media (max-width: 760px) {
  .card-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .card-actions,
  .search-input {
    width: 100%;
  }
}
</style>