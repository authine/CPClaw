<template>
  <PageHeader title="元数据" description="查看管理员初始化到本地的云枢应用级知识图谱。" />
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>本地元数据应用</span>
        <div>
          <el-input v-model="query" class="search-input" placeholder="搜索应用、实体或功能" />
          <el-button @click="search">搜索</el-button>
          <el-button type="primary" @click="sync">初始化云枢元数据</el-button>
        </div>
      </div>
    </template>
    <el-alert v-if="syncResult" :title="`同步完成：应用 ${syncResult.appCount} 个，实体 ${syncResult.entityCount} 个，索引文档 ${syncResult.searchDocumentCount} 个`" type="success" show-icon />
    <el-table :data="apps" class="table">
      <el-table-column prop="name" label="应用" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="entityCount" label="实体数" />
      <el-table-column prop="syncedAt" label="同步时间" />
    </el-table>
  </el-card>

  <el-card class="table" shadow="never">
    <template #header>检索结果</template>
    <el-table :data="results">
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="objectType" label="类型" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="graphPath" label="图谱路径" />
      <el-table-column prop="reason" label="匹配原因" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '../components/common/PageHeader.vue'
import { listMetadataApps, searchMetadata, syncMetadata } from '../services/metadataApi'
import type { MetadataAppSummary, MetadataSearchResult, MetadataSyncResponse } from '../types/metadata'

const apps = ref<MetadataAppSummary[]>([])
const results = ref<MetadataSearchResult[]>([])
const syncResult = ref<MetadataSyncResponse>()
const query = ref('')

onMounted(load)

async function load() {
  apps.value = await listMetadataApps()
}

async function sync() {
  syncResult.value = await syncMetadata()
  await load()
  await search()
}

async function search() {
  results.value = await searchMetadata(query.value)
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
  width: 220px;
  margin-right: 8px;
}

.table {
  margin-top: 16px;
}
</style>
