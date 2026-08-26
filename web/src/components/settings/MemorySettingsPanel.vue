<template>
  <section class="settings-section memory-settings-panel">
    <div class="section-heading"><div><div class="section-kicker">记忆中心</div><h1>用户设置记忆与平台设置记忆</h1><p>当前用户：{{ memory?.displayName || '黄杰' }}（{{ memory?.principalId || 'huangj' }}）</p></div><el-tag effect="plain" round>{{ memory?.superAdmin ? '超级管理员' : '个人用户' }}</el-tag></div>
    <el-alert type="info" :closable="false" show-icon title="三层记忆边界"><template #default>用户会话记忆只用于当前对话连续性，不在此处展示；用户设置记忆只对当前用户生效；平台设置记忆由超级管理员维护，并作为系统级上下文参与任务处理。</template></el-alert>
    <el-card class="config-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>用户设置记忆</strong><small>当前用户可查看、添加和删除自己的长期偏好与使用约定。</small></div><el-button type="primary" @click="openEditor('USER')"><el-icon><Plus /></el-icon>新增用户设置记忆</el-button></div></template>
      <el-table :data="memory?.personal || []" empty-text="暂无用户设置记忆">
        <el-table-column prop="memoryType" label="类型" width="160" />
        <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column label="更新时间" width="190"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="danger" @click="remove(row.id, 'USER')">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-card v-if="memory?.globalVisible" class="config-card" shadow="never">
      <template #header><div class="card-title-row"><div><strong>平台设置记忆</strong><small>仅超级管理员可以查看、添加或删除；普通用户不会收到该区域数据。</small></div><el-button type="primary" @click="openEditor('SYSTEM')"><el-icon><Plus /></el-icon>新增平台设置记忆</el-button></div></template>
      <el-table :data="memory?.global || []" empty-text="暂无平台设置记忆">
        <el-table-column prop="memoryType" label="类型" width="160" />
        <el-table-column prop="content" label="内容" min-width="360" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column label="更新时间" width="190"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="danger" @click="remove(row.id, 'SYSTEM')">删除</el-button></template></el-table-column>
      </el-table>
    </el-card>
    <el-empty v-else description="平台设置记忆仅对超级管理员开放" />
    <el-dialog v-model="editorVisible" :title="editorScope === 'SYSTEM' ? '新增平台设置记忆' : '新增用户设置记忆'" width="520px">
      <el-form label-position="top"><el-form-item label="记忆类型"><el-input v-model="form.memoryType" placeholder="例如：业务口径、偏好、规则" /></el-form-item><el-form-item label="记忆内容"><el-input v-model="form.content" type="textarea" :rows="6" placeholder="输入需要长期保留的内容" /></el-form-item><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="100" /></el-form-item><el-form-item label="有效期（天，0 表示长期）"><el-input-number v-model="form.ttlDays" :min="0" :max="3650" /></el-form-item></el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="!form.content.trim()" @click="save">保存记忆</el-button></template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { deleteGlobalMemory, deletePersonalMemory, getMemorySettings, saveGlobalMemory, savePersonalMemory } from '../../services/settingsApi'
import type { MemorySettingsResponse, SaveMemoryEntryRequest } from '../../types/settings'

const memory = ref<MemorySettingsResponse>()
const editorVisible = ref(false)
const saving = ref(false)
const editorScope = ref<'USER' | 'SYSTEM'>('USER')
const form = reactive<SaveMemoryEntryRequest>({ memoryType: 'manual', content: '', priority: 0, ttlDays: 365 })

async function load() { memory.value = await getMemorySettings() }
function openEditor(scope: 'USER' | 'SYSTEM') { editorScope.value = scope; Object.assign(form, { memoryType: scope === 'SYSTEM' ? 'system_rule' : 'personal_preference', content: '', priority: scope === 'SYSTEM' ? 50 : 0, ttlDays: scope === 'SYSTEM' ? 0 : 365 }); editorVisible.value = true }
async function save() { saving.value = true; try { editorScope.value === 'SYSTEM' ? await saveGlobalMemory(form) : await savePersonalMemory(form); editorVisible.value = false; await load(); ElMessage.success('记忆已保存') } catch (error) { ElMessage.error(error instanceof Error ? error.message : '记忆保存失败') } finally { saving.value = false } }
async function remove(id: string, scope: 'USER' | 'SYSTEM') { try { await ElMessageBox.confirm('确定删除这条记忆吗？', '删除记忆', { type: 'warning' }); scope === 'SYSTEM' ? await deleteGlobalMemory(id) : await deletePersonalMemory(id); await load(); ElMessage.success('记忆已删除') } catch (error) { if (error === 'cancel' || error === 'close') return; ElMessage.error(error instanceof Error ? error.message : '记忆删除失败') } }
function formatDate(value?: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—' }
onMounted(() => { void load() })
</script>

<style scoped>
.memory-settings-panel :deep(.el-card__header) {
  padding: var(--cp-space-5) var(--cp-space-6);
}

.memory-settings-panel .card-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--cp-space-4);
}

.memory-settings-panel .card-title-row > div {
  display: grid;
  min-width: 0;
  gap: var(--cp-space-1);
}

.memory-settings-panel .card-title-row strong {
  color: var(--cp-text-primary);
  font-size: var(--cp-font-body-lg);
  line-height: var(--cp-line-body-lg);
}

.memory-settings-panel .card-title-row small {
  color: var(--cp-text-secondary);
  font-size: var(--cp-font-sm);
  line-height: var(--cp-line-sm);
}

.memory-settings-panel .card-title-row > .el-button {
  flex: 0 0 auto;
}

@media (max-width: 620px) {
  .memory-settings-panel .card-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .memory-settings-panel .card-title-row > .el-button {
    width: 100%;
  }
}
</style>
