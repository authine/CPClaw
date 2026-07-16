<template>
  <PageHeader title="账号与模型" description="维护云枢账号、元数据环境和模型凭据。" />

  <el-alert v-if="errorMessage" class="settings-error" type="error" show-icon :closable="false" :title="errorMessage" />

  <div class="settings-grid" v-loading="loading">
    <el-card class="settings-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>个人云枢账号</span>
          <el-tag v-if="settings?.userCloudPivot.hasPassword" size="small" type="success">密码已保存</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="云枢访问地址">
          <el-input v-model="userForm.cloudPivotBaseUrl" placeholder="https://your-cloudpivot-domain.com/login" />
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input v-model="userForm.cloudPivotUsername" placeholder="请输入登录账号" />
        </el-form-item>
        <el-form-item :label="settings?.userCloudPivot.hasPassword ? '登录密码（已保存，填写则覆盖）' : '登录密码'">
          <el-input v-model="userForm.cloudPivotPassword" type="password" show-password placeholder="请输入登录密码" />
        </el-form-item>
        <div class="settings-actions">
          <el-button type="primary" :loading="savingUser" @click="saveUser">
            <el-icon><Finished /></el-icon>
            <span>保存账号</span>
          </el-button>
          <el-button :loading="testingUser" @click="testUser">
            <el-icon><Connection /></el-icon>
            <span>测试连接</span>
          </el-button>
        </div>
      </el-form>
    </el-card>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>管理员元数据环境</span>
          <el-tag v-if="settings?.adminMetadata.hasPassword" size="small" type="success">密码已保存</el-tag>
        </div>
      </template>
      <el-form label-position="top">
        <el-form-item label="云枢访问地址">
          <el-input v-model="adminForm.targetBaseUrl" placeholder="https://your-cloudpivot-admin.com/login" />
        </el-form-item>
        <el-form-item label="管理员账号">
          <el-input v-model="adminForm.username" placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item :label="settings?.adminMetadata.hasPassword ? '管理员密码（已保存，填写则覆盖）' : '管理员密码'">
          <el-input v-model="adminForm.password" type="password" show-password placeholder="请输入管理员密码" />
        </el-form-item>
        <div class="settings-row">
          <el-form-item label="检索引擎">
            <el-select v-model="adminForm.searchEngineType">
              <el-option label="MySQL" value="mysql" />
              <el-option label="OpenSearch" value="opensearch" />
            </el-select>
          </el-form-item>
          <el-form-item label="检索端点">
            <el-input v-model="adminForm.searchEndpoint" placeholder="可选" />
          </el-form-item>
        </div>
        <div class="settings-actions">
          <el-button type="primary" :loading="savingAdmin" @click="saveAdmin">
            <el-icon><Finished /></el-icon>
            <span>保存环境</span>
          </el-button>
          <el-button :loading="testingAdmin" @click="testAdmin">
            <el-icon><Connection /></el-icon>
            <span>测试连接</span>
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>

  <el-card class="settings-card settings-card--wide" shadow="never">
    <template #header>
      <div class="card-header">
        <span>模型配置</span>
        <el-button :loading="loading" @click="loadSettings">
          <el-icon><Refresh /></el-icon>
          <span>刷新</span>
        </el-button>
      </div>
    </template>
    <el-form class="model-form" label-position="top">
      <el-form-item label="显示名称">
        <el-input v-model="modelForm.modelDisplayName" placeholder="演示模型" />
      </el-form-item>
      <el-form-item label="模型名称">
        <el-input v-model="modelForm.modelName" placeholder="gpt-4.1-mini" />
      </el-form-item>
      <el-form-item label="API 地址">
        <el-input v-model="modelForm.modelApiBaseUrl" placeholder="https://api.example.com/v1" />
      </el-form-item>
      <el-form-item label="API Key">
        <el-input v-model="modelForm.modelApiKey" type="password" show-password placeholder="请输入 API Key" />
      </el-form-item>
      <div class="model-options">
        <el-checkbox v-model="modelForm.supportsThinking" @change="onSupportsThinkingChange">支持思考模式</el-checkbox>
        <el-checkbox v-model="modelForm.defaultThinkingEnabled" :disabled="!modelForm.supportsThinking">默认开启思考模式</el-checkbox>
      </div>
      <div class="settings-actions">
        <el-button type="primary" :loading="savingModel" @click="saveModel">
          <el-icon><Plus /></el-icon>
          <span>新增模型</span>
        </el-button>
      </div>
    </el-form>

    <el-table :data="models" class="model-table" empty-text="暂无模型配置">
      <el-table-column prop="name" label="显示名称" min-width="160" />
      <el-table-column prop="modelName" label="模型名称" min-width="180" />
      <el-table-column prop="apiBaseUrl" label="API 地址" min-width="240" show-overflow-tooltip />
      <el-table-column label="思考模式" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="row.supportsThinking ? 'success' : 'info'">{{ row.supportsThinking ? '支持' : '不支持' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.defaultThinkingEnabled ? 'success' : 'info'">{{ row.defaultThinkingEnabled ? '开启' : '关闭' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="密钥" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.hasApiKey ? 'success' : 'warning'">{{ row.hasApiKey ? '已保存' : '缺失' }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Finished, Plus, Refresh } from '@element-plus/icons-vue'
import PageHeader from '../components/common/PageHeader.vue'
import {
  getSettings,
  saveAdminSettings,
  saveUserSettings,
  testAdminCloudPivotConnection,
  testUserCloudPivotConnection
} from '../services/settingsApi'
import type { SaveUserSettingsRequest, SettingsResponse } from '../types/settings'

const settings = ref<SettingsResponse>()
const loading = ref(false)
const savingUser = ref(false)
const savingAdmin = ref(false)
const savingModel = ref(false)
const testingUser = ref(false)
const testingAdmin = ref(false)
const errorMessage = ref('')

const userForm = reactive({
  cloudPivotBaseUrl: '',
  cloudPivotUsername: '',
  cloudPivotPassword: ''
})

const adminForm = reactive({
  targetBaseUrl: '',
  username: '',
  password: '',
  searchEngineType: 'mysql',
  searchEndpoint: ''
})

const modelForm = reactive({
  modelDisplayName: '',
  modelName: '',
  modelApiBaseUrl: '',
  modelApiKey: '',
  supportsThinking: false,
  defaultThinkingEnabled: false
})

const models = computed(() => settings.value?.models ?? [])

onMounted(loadSettings)

async function loadSettings() {
  errorMessage.value = ''
  loading.value = true
  try {
    const nextSettings = await getSettings()
    settings.value = nextSettings
    populateForms(nextSettings)
  } catch (error) {
    errorMessage.value = messageFromError(error)
  } finally {
    loading.value = false
  }
}

async function saveUser() {
  errorMessage.value = ''
  savingUser.value = true
  try {
    settings.value = await saveUserSettings(buildUserSettingsPayload())
    userForm.cloudPivotPassword = ''
    ElMessage.success('个人账号已保存')
  } catch (error) {
    reportError(error)
  } finally {
    savingUser.value = false
  }
}

async function saveAdmin() {
  errorMessage.value = ''
  savingAdmin.value = true
  try {
    settings.value = await saveAdminSettings({
      targetBaseUrl: adminForm.targetBaseUrl,
      username: adminForm.username,
      password: adminForm.password,
      searchEngineType: adminForm.searchEngineType,
      searchEndpoint: adminForm.searchEndpoint
    })
    adminForm.password = ''
    ElMessage.success('管理员环境已保存')
  } catch (error) {
    reportError(error)
  } finally {
    savingAdmin.value = false
  }
}

async function saveModel() {
  errorMessage.value = ''
  if (!modelForm.modelName.trim() || !modelForm.modelApiBaseUrl.trim()) {
    ElMessage.warning('请填写模型名称和 API 地址')
    return
  }

  savingModel.value = true
  try {
    settings.value = await saveUserSettings(buildUserSettingsPayload({
      modelName: modelForm.modelName,
      modelApiBaseUrl: modelForm.modelApiBaseUrl,
      modelApiKey: modelForm.modelApiKey,
      modelDisplayName: modelForm.modelDisplayName,
      supportsThinking: modelForm.supportsThinking,
      defaultThinkingEnabled: modelForm.supportsThinking && modelForm.defaultThinkingEnabled
    }))
    resetModelForm()
    ElMessage.success('模型配置已新增')
  } catch (error) {
    reportError(error)
  } finally {
    savingModel.value = false
  }
}

async function testUser() {
  errorMessage.value = ''
  testingUser.value = true
  try {
    const result = await testUserCloudPivotConnection()
    showConnectionResult(result.success, result.message)
  } catch (error) {
    reportError(error)
  } finally {
    testingUser.value = false
  }
}

async function testAdmin() {
  errorMessage.value = ''
  testingAdmin.value = true
  try {
    const result = await testAdminCloudPivotConnection()
    showConnectionResult(result.success, result.message)
  } catch (error) {
    reportError(error)
  } finally {
    testingAdmin.value = false
  }
}

function populateForms(nextSettings: SettingsResponse) {
  userForm.cloudPivotBaseUrl = nextSettings.userCloudPivot.baseUrl ?? ''
  userForm.cloudPivotUsername = nextSettings.userCloudPivot.username ?? ''
  adminForm.targetBaseUrl = nextSettings.adminMetadata.targetBaseUrl ?? ''
  adminForm.username = nextSettings.adminMetadata.username ?? ''
  adminForm.searchEngineType = nextSettings.adminMetadata.searchEngineType || 'mysql'
  adminForm.searchEndpoint = nextSettings.adminMetadata.searchEndpoint ?? ''
}

function buildUserSettingsPayload(model?: Partial<SaveUserSettingsRequest>): SaveUserSettingsRequest {
  return {
    cloudPivotBaseUrl: userForm.cloudPivotBaseUrl,
    cloudPivotUsername: userForm.cloudPivotUsername,
    cloudPivotPassword: userForm.cloudPivotPassword,
    modelDisplayName: model?.modelDisplayName ?? '',
    modelApiBaseUrl: model?.modelApiBaseUrl ?? '',
    modelApiKey: model?.modelApiKey ?? '',
    modelName: model?.modelName ?? '',
    supportsThinking: model?.supportsThinking ?? false,
    defaultThinkingEnabled: model?.defaultThinkingEnabled ?? false
  }
}

function onSupportsThinkingChange(value: boolean) {
  if (!value) {
    modelForm.defaultThinkingEnabled = false
  }
}

function resetModelForm() {
  modelForm.modelDisplayName = ''
  modelForm.modelName = ''
  modelForm.modelApiBaseUrl = ''
  modelForm.modelApiKey = ''
  modelForm.supportsThinking = false
  modelForm.defaultThinkingEnabled = false
}

function showConnectionResult(success: boolean, message: string) {
  if (success) {
    ElMessage.success(message)
    return
  }
  ElMessage.warning(message)
}

function reportError(error: unknown) {
  errorMessage.value = messageFromError(error)
  ElMessage.error(errorMessage.value)
}

function messageFromError(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}
</script>

<style scoped>
.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(320px, 1fr));
  gap: 16px;
}

.settings-card {
  border-color: #e4e7ec;
}

.settings-card--wide {
  margin-top: 16px;
}

.card-header,
.settings-actions,
.model-options {
  display: flex;
  align-items: center;
}

.card-header {
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

.settings-row {
  display: grid;
  grid-template-columns: minmax(140px, 180px) minmax(180px, 1fr);
  gap: 12px;
}

.settings-error {
  margin-bottom: 16px;
}

.settings-actions {
  gap: 12px;
}

.model-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px 16px;
  align-items: end;
}

.model-options {
  grid-column: span 2;
  gap: 16px;
  min-height: 32px;
}

.model-table {
  margin-top: 16px;
}

@media (max-width: 1100px) {
  .settings-grid,
  .model-form {
    grid-template-columns: 1fr;
  }

  .model-options {
    grid-column: auto;
  }
}
</style>