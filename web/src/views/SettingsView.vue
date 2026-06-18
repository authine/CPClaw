<template>
  <PageHeader title="云枢账号设置" description="配置用于查询和操作云枢数据的个人账号。密码只保存不回显。" />
  <el-card class="settings-card" shadow="never">
    <template #header>连接我的云枢账号</template>
    <el-form label-position="top">
      <el-form-item label="云枢访问地址">
        <el-input v-model="userForm.cloudPivotBaseUrl" placeholder="例如：https://your-cloudpivot-domain.com/login" />
      </el-form-item>
      <el-form-item label="登录账号">
        <el-input v-model="userForm.cloudPivotUsername" placeholder="请输入你的云枢登录账号" />
      </el-form-item>
      <el-form-item :label="settings?.userCloudPivot.hasPassword ? '登录密码（已保存，填写则覆盖）' : '登录密码'">
        <el-input v-model="userForm.cloudPivotPassword" type="password" show-password placeholder="请输入你的云枢登录密码" />
      </el-form-item>
      <el-alert v-if="errorMessage" class="settings-error" type="error" show-icon :closable="false" :title="errorMessage" />
      <div class="settings-actions">
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
        <el-button :loading="testing" @click="testUser">测试连接</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/common/PageHeader.vue'
import { getSettings, saveUserSettings, testUserCloudPivotConnection } from '../services/settingsApi'
import type { SettingsResponse } from '../types/settings'

const settings = ref<SettingsResponse>()
const saving = ref(false)
const testing = ref(false)
const errorMessage = ref('')
const userForm = reactive({
  cloudPivotBaseUrl: '',
  cloudPivotUsername: '',
  cloudPivotPassword: ''
})

onMounted(loadSettings)

async function loadSettings() {
  errorMessage.value = ''
  try {
    settings.value = await getSettings()
    userForm.cloudPivotBaseUrl = settings.value.userCloudPivot.baseUrl ?? ''
    userForm.cloudPivotUsername = settings.value.userCloudPivot.username ?? ''
  } catch (error) {
    errorMessage.value = messageFromError(error)
  }
}

async function saveUser() {
  errorMessage.value = ''
  saving.value = true
  try {
    settings.value = await saveUserSettings({
      cloudPivotBaseUrl: userForm.cloudPivotBaseUrl,
      cloudPivotUsername: userForm.cloudPivotUsername,
      cloudPivotPassword: userForm.cloudPivotPassword,
      modelDisplayName: '',
      modelApiBaseUrl: '',
      modelApiKey: '',
      modelName: '',
      supportsThinking: false,
      defaultThinkingEnabled: false
    })
    userForm.cloudPivotPassword = ''
    ElMessage.success('云枢账号已保存，密码不会回显')
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    saving.value = false
  }
}

async function testUser() {
  errorMessage.value = ''
  testing.value = true
  try {
    const result = await testUserCloudPivotConnection()
    ElMessage[result.success ? 'success' : 'warning'](result.message)
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    testing.value = false
  }
}

function messageFromError(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}
</script>

<style scoped>
.settings-card {
  max-width: 720px;
}

.settings-error {
  margin-bottom: 16px;
}

.settings-actions {
  display: flex;
  gap: 12px;
}
</style>
