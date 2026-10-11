<template>
  <section class="feedback-admin">
    <div class="section-heading"><div><div class="section-kicker">运营闭环</div><h2>问题反馈</h2><p>查看用户反馈，识别高频问题并进入后续运营处理。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <el-alert v-if="errorMessage" type="error" :closable="false" show-icon :title="errorMessage" />
    <el-card class="config-card" shadow="never">
      <div class="feedback-toolbar"><el-select v-model="statusFilter" clearable placeholder="全部状态" style="width:160px"><el-option label="待处理" value="OPEN"/><el-option label="处理中" value="IN_PROGRESS"/><el-option label="已解决" value="RESOLVED"/><el-option label="已关闭" value="CLOSED"/></el-select><el-input v-model="keyword" clearable placeholder="搜索标题、内容或应用" style="max-width:360px" /></div>
      <el-table :data="filteredItems" empty-text="暂无反馈" stripe>
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip /><el-table-column prop="category" label="类型" width="130" /><el-table-column prop="appCode" label="应用" width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="150"><template #default="{row}"><el-select v-model="row.status" size="small" @change="updateStatus(row)"><el-option label="待处理" value="OPEN"/><el-option label="处理中" value="IN_PROGRESS"/><el-option label="已解决" value="RESOLVED"/><el-option label="已关闭" value="CLOSED"/></el-select></template></el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="190" /><el-table-column label="管理员备注" min-width="240"><template #default="{ row }"><el-input v-model="row.adminNote" size="small" placeholder="填写处理备注" @change="saveNote(row)" /></template></el-table-column><el-table-column label="内容" min-width="300" show-overflow-tooltip><template #default="{ row }">{{ row.content }}</template></el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'; import { ElMessage } from 'element-plus'; import { requestJson } from '../../services/api'
type FeedbackItem={id:string;title:string;category:string;appCode?:string;status:string;content:string;adminNote?:string;createdAt:string}; const items=ref<FeedbackItem[]>([]); const loading=ref(false); const errorMessage=ref(''); const statusFilter=ref(''); const keyword=ref(''); const filteredItems=computed(()=>{const q=keyword.value.trim().toLowerCase();return items.value.filter(item=>(!statusFilter.value||item.status===statusFilter.value)&&(!q||`${item.title} ${item.content} ${item.appCode??''}`.toLowerCase().includes(q)))})
async function load(){loading.value=true;errorMessage.value='';try{items.value=await requestJson<FeedbackItem[]>('/feedback')}catch(error){errorMessage.value=error instanceof Error?error.message:'反馈列表加载失败';ElMessage.error(errorMessage.value)}finally{loading.value=false}}
async function persist(item:FeedbackItem){await requestJson(`/feedback/${item.id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({status:item.status,adminNote:item.adminNote??''})})}
async function updateStatus(item:FeedbackItem){try{await persist(item);ElMessage.success('反馈状态已更新')}catch(error){ElMessage.error(error instanceof Error?error.message:'反馈状态更新失败');await load()}}
async function saveNote(item:FeedbackItem){try{await persist(item);ElMessage.success('备注已保存')}catch(error){ElMessage.error(error instanceof Error?error.message:'备注保存失败');await load()}}
onMounted(load)
</script>
<style scoped>.feedback-admin{display:grid;gap:18px}.section-heading{display:flex;justify-content:space-between;gap:18px}.section-kicker{color:var(--settings-brand);font-size:12px;font-weight:700}.section-heading h2{margin:6px 0}.section-heading p{margin:0;color:var(--settings-secondary)}.config-card{border-color:var(--settings-line)}.feedback-toolbar{display:flex;gap:10px;margin-bottom:14px}</style>

