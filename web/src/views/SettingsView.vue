<template>
  <PageHeader title="系统设置" description="普通用户配置个人云枢账号和模型 API；管理员配置元数据初始化账号。" />
  <el-row :gutter="16">
    <el-col :span="12">
      <el-card shadow="never">
        <template #header>普通用户配置</template>
        <el-form label-position="top">
          <el-form-item label="云枢访问地址"><el-input v-model="userForm.cloudPivotBaseUrl" /></el-form-item>
          <el-form-item label="云枢账号"><el-input v-model="userForm.cloudPivotUsername" /></el-form-item>
          <el-form-item :label="settings?.userCloudPivot.hasPassword ? '云枢密码（已保存，填写则覆盖）' : '云枢密码'">
            <el-input v-model="userForm.cloudPivotPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="模型显示名称"><el-input v-model="userForm.modelDisplayName" /></el-form-item>
          <el-form-item label="模型 API 地址"><el-input v-model="userForm.modelApiBaseUrl" /></el-form-item>
          <el-form-item label="模型 API Key"><el-input v-model="userForm.modelApiKey" type="password" show-password /></el-form-item>
          <el-form-item label="模型名称"><el-input v-model="userForm.modelName" /></el-form-item>
          <el-form-item label="模型能力">
            <el-checkbox v-model="userForm.supportsThinking">支持思考模式</el-checkbox>
            <el-checkbox v-model="userForm.defaultThinkingEnabled">默认启用思考模式</el-checkbox>
          </el-form-item>
          <el-button type="primary" @click="saveUser">保存普通用户配置</el-button>
          <el-button @click="testUser">测试连接</el-button>
        </el-form>
      </el-card>
    </el-col>
    <el-col :span="12">
      <el-card shadow="never">
        <template #header>管理员元数据初始化配置</template>
        <el-form label-position="top">
          <el-form-item label="目标云枢环境"><el-input v-model="adminForm.targetBaseUrl" /></el-form-item>
          <el-form-item label="管理员云枢账号"><el-input v-model="adminForm.username" /></el-form-item>
          <el-form-item :label="settings?.adminMetadata.hasPassword ? '管理员云枢密码（已保存，填写则覆盖）' : '管理员云枢密码'">
            <el-input v-model="adminForm.password" type="password" show-password />
          </el-form-item>
          <el-form-item label="检索类型"><el-input v-model="adminForm.searchEngineType" placeholder="mysql / opensearch" /></el-form-item>
          <el-form-item label="检索中间件地址"><el-input v-model="adminForm.searchEndpoint" /></el-form-item>
          <el-button type="primary" @click="saveAdmin">保存管理员配置</el-button>
          <el-button @click="testAdmin">测试连接</el-button>
        </el-form>
      </el-card>
    </el-col>
  </el-row>

  <el-card class="models-card" shadow="never">
    <template #header>已配置模型</template>
    <el-table :data="settings?.models ?? []">
      <el-table-column prop="name" label="显示名称" />
      <el-table-column prop="modelName" label="模型名称" />
      <el-table-column prop="apiBaseUrl" label="API 地址" />
      <el-table-column prop="supportsThinking" label="思考模式" />
      <el-table-column prop="hasApiKey" label="API Key 已保存" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/common/PageHeader.vue'
import {
  getSettings,
  saveAdminSettings,
  saveUserSettings,
  testAdminCloudPivotConnection,
  testUserCloudPivotConnection
} from '../services/settingsApi'
import type { SettingsResponse } from '../types/settings'

const settings = ref<SettingsResponse>()
const userForm = reactive({
  cloudPivotBaseUrl: '',
  cloudPivotUsername: '',
  cloudPivotPassword: '',
  modelDisplayName: '',
  modelApiBaseUrl: '',
  modelApiKey: '',
  modelName: '',
  supportsThinking: false,
  defaultThinkingEnabled: false
})
const adminForm = reactive({
  targetBaseUrl: '',
  username: '',
  password: '',
  searchEngineType: 'mysql',
  searchEndpoint: ''
})

onMounted(loadSettings)

async function loadSettings() {
  settings.value = await getSettings()
  userForm.cloudPivotBaseUrl = settings.value.userCloudPivot.baseUrl ?? ''
  userForm.cloudPivotUsername = settings.value.userCloudPivot.username ?? ''
  adminForm.targetBaseUrl = settings.value.adminMetadata.targetBaseUrl ?? ''
  adminForm.username = settings.value.adminMetadata.username ?? ''
  adminForm.searchEngineType = settings.value.adminMetadata.searchEngineType ?? 'mysql'
  adminForm.searchEndpoint = settings.value.adminMetadata.searchEndpoint ?? ''
}

async function saveUser() {
  settings.value = await saveUserSettings(userForm)
  userForm.cloudPivotPassword = ''
  userForm.modelApiKey = ''
  ElMessage.success('普通用户配置已保存，敏感值不会回显')
}

async function saveAdmin() {
  settings.value = await saveAdminSettings(adminForm)
  adminForm.password = ''
  ElMessage.success('管理员配置已保存，敏感值不会回显')
}

async function testUser() {
  const result = await testUserCloudPivotConnection()
  ElMessage[result.success ? 'success' : 'warning'](result.message)
}

async function testAdmin() {
  const result = await testAdminCloudPivotConnection()
  ElMessage[result.success ? 'success' : 'warning'](result.message)
}
</script>

<style scoped>
.models-card {
  margin-top: 16px;
}
</style>
