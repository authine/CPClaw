<template>
  <div :class="['settings-workbench', { 'settings-workbench--dark': darkMode, 'settings-workbench--nav-open': settingsSidebarOpen }]">
    <div v-if="settingsSidebarOpen" class="settings-nav-backdrop" aria-hidden="true" @click="settingsSidebarOpen = false" />
    <aside class="settings-panel">
      <div class="settings-panel__header"><span class="settings-brand-mark">C</span><strong>CPClaw</strong><el-button class="panel-back" text circle aria-label="返回对话" @click="goBack"><el-icon><ArrowLeft /></el-icon></el-button></div>
      <nav class="settings-nav" aria-label="系统设置菜单">
        <template v-for="group in navigationGroups" :key="group.title">
          <div class="settings-panel__label">{{ group.title }}</div>
          <button v-for="item in group.items" :key="item.id" class="panel-nav-item" :class="{ active: activeSection === item.id }" type="button" :aria-current="activeSection === item.id ? 'page' : undefined" :aria-label="`${item.title}：${item.description}`" @click="selectSection(item.id)"><span class="panel-nav-icon"><el-icon><component :is="item.icon" /></el-icon></span><span><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span></button>
        </template>
      </nav>
      <div class="settings-panel__footer"><span class="settings-avatar">黄</span><span class="settings-identity__copy"><strong>黄杰</strong><small>huangj · 超级管理员</small></span></div>
    </aside>
    <section class="settings-main">
      <header class="settings-toolbar"><div class="settings-toolbar__leading"><el-button class="settings-nav-toggle" text circle aria-label="打开系统设置菜单" :aria-expanded="settingsSidebarOpen" @click="settingsSidebarOpen = !settingsSidebarOpen"><el-icon><Menu /></el-icon></el-button><strong>系统设置</strong></div><div><el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon>返回对话</el-button><el-button class="refresh-button" :loading="loading" @click="loadSettings"><el-icon><Refresh /></el-icon>刷新状态</el-button></div></header>
      <main class="settings-page" v-loading="loading">
        <el-alert v-if="errorMessage" class="settings-error" type="error" show-icon :closable="false" :title="errorMessage" />
        <section v-show="activeSection === 'user-cloudpivot'" class="settings-section"><SectionHeading kicker="云枢环境连接 / 个人账号" title="个人云枢账号" description="管理员统一配置云枢环境地址；当前用户只需配置自己的账号和密码，即可通过 CPClaw 访问云枢。" :tag="credentialLabel(settings?.userCloudPivot.credentialStatus)" /><el-card class="config-card form-card" shadow="never"><el-form label-position="top"><el-form-item label="云枢环境"><div class="environment-readonly"><span :class="['environment-dot', { ready: settings?.userCloudPivot.environmentConfigured }]" /><span>{{ settings?.userCloudPivot.environmentConfigured ? '已连接管理员配置的云枢环境' : '管理员尚未配置云枢环境' }}</span><el-button text type="primary" @click="openSettingsSection('admin-cloudpivot')">查看管理员配置</el-button></div></el-form-item><el-form-item label="登录账号"><el-input v-model="userForm.cloudPivotUsername" placeholder="请输入个人云枢账号" /></el-form-item><el-form-item :label="passwordFieldLabel(settings?.userCloudPivot.credentialStatus, '登录密码')"><el-input v-model="userForm.cloudPivotPassword" type="password" show-password autocomplete="new-password" :placeholder="passwordFieldPlaceholder(settings?.userCloudPivot.credentialStatus, '请输入个人云枢密码')" @focus="revealPasswordField('user')" @blur="restorePasswordMask('user')" /></el-form-item><el-alert v-if="settings?.userCloudPivot.credentialStatus === 'unreadable'" class="credential-alert" type="warning" :closable="false" show-icon title="已保存的密码无法在当前服务中使用"><template #default>重新输入密码并保存即可覆盖旧密文；不会删除其他系统配置或元数据。</template></el-alert><el-alert v-if="userTestMessage" class="connection-test-result" :type="userTestType" show-icon :closable="false" :title="userTestMessage" /><div class="form-actions"><el-button :loading="testingUser" @click="testUser"><el-icon><Connection /></el-icon>验证连接</el-button><el-button type="primary" :loading="savingUser" :disabled="!canSaveUser" @click="saveUser"><el-icon><Finished /></el-icon>保存配置</el-button></div></el-form></el-card></section>
        <section v-show="activeSection === 'admin-cloudpivot'" class="settings-section"><SectionHeading kicker="云枢环境连接 / 管理员环境" title="管理员云枢环境" description="仅用于同步云枢应用、对象、字段、关系与 API；CPClaw 数据库与向量库由后端部署配置管理。" :tag="credentialLabel(settings?.adminMetadata.credentialStatus)" /><el-card class="config-card form-card" shadow="never"><el-form label-position="top"><el-form-item label="云枢访问地址"><el-input v-model="adminForm.targetBaseUrl" placeholder="https://your-cloudpivot-admin.com" /></el-form-item><el-form-item label="管理员账号"><el-input v-model="adminForm.username" placeholder="请输入管理员账号" /></el-form-item><el-form-item :label="passwordFieldLabel(settings?.adminMetadata.credentialStatus, '管理员密码')"><el-input v-model="adminForm.password" type="password" show-password autocomplete="new-password" :placeholder="passwordFieldPlaceholder(settings?.adminMetadata.credentialStatus, '请输入管理员密码')" @focus="revealPasswordField('admin')" @blur="restorePasswordMask('admin')" /></el-form-item><el-alert v-if="settings?.adminMetadata.credentialStatus === 'unreadable'" class="credential-alert" type="warning" :closable="false" show-icon title="已保存的密码无法在当前服务中使用"><template #default>重新输入密码并保存即可覆盖旧密文；之后可重新测试连接和同步元数据。</template></el-alert><el-alert class="deployment-note" type="info" :closable="false" show-icon title="后端部署配置"><template #default>数据库、向量库与加密密钥由后端部署环境管理；本页仅保存管理员云枢连接。</template></el-alert><el-alert v-if="adminTestMessage" class="connection-test-result" :type="adminTestType" show-icon :closable="false" :title="adminTestMessage" /><div class="form-actions"><el-button :loading="testingAdmin" @click="testAdmin"><el-icon><Connection /></el-icon>验证连接</el-button><el-button type="primary" :loading="savingAdmin" :disabled="!canSaveAdmin" @click="saveAdmin"><el-icon><Finished /></el-icon>保存配置</el-button></div></el-form></el-card></section>
        <MetadataSyncPanel v-show="activeSection === 'metadata-sync'" :configured="Boolean(settings?.adminMetadata.hasPassword)" @synced="metadataVersion += 1" />
        <MetadataBrowserPanel v-if="activeSection === 'metadata-browser'" :key="metadataVersion" />
        <TokenUsageDashboardPanel v-if="activeSection === 'usage-dashboard'" />
        <LogAnalyticsPanel v-if="activeSection === 'log-analytics'" />
        <CloudPivotMcpPanel v-if="activeSection === 'mcp-cloudpivot'" />
        <MemorySettingsPanel v-if="activeSection === 'memory'" />
        <section v-show="activeSection === 'models'" class="settings-section"><SectionHeading kicker="Agent 模型" title="选择智能引擎" description="先验证当前输入，再保存模型；通过只代表网络、凭据、模型名和基础接口可用。" :tag="models.length ? `${models.length} 个模型` : '尚未配置'" /><el-card class="config-card model-form-card" shadow="never"><template #header><div class="card-title-row"><div><strong>{{ editingModelId ? '更新模型凭据' : '新增模型配置' }}</strong><small>{{ editingModelId ? '录入新 API Key 并测试成功后，会覆盖该模型的旧密文。' : '测试不会创建模型或保存 API Key；字段变更后需重新测试。' }}</small></div><el-button v-if="editingModelId" text @click="resetModelForm">取消更新</el-button></div></template><el-form class="model-form" label-position="top"><el-form-item label="显示名称"><el-input v-model="modelForm.modelDisplayName" placeholder="例如：企业问数模型" /></el-form-item><el-form-item label="模型名称"><el-input v-model="modelForm.modelName" placeholder="gpt-4.1-mini" /></el-form-item><el-form-item label="API 地址"><el-input v-model="modelForm.modelApiBaseUrl" placeholder="https://api.example.com/v1" /></el-form-item><el-form-item label="API Key"><el-input v-model="modelForm.modelApiKey" type="password" show-password autocomplete="new-password" placeholder="请输入 API Key" /></el-form-item><div class="model-options"><el-checkbox v-model="modelForm.supportsThinking" @change="onSupportsThinkingChange">支持思考模式</el-checkbox><el-checkbox v-model="modelForm.defaultThinkingEnabled" :disabled="!modelForm.supportsThinking">默认开启</el-checkbox></div><div class="model-actions"><el-button :loading="testingNewModel" @click="testUnsavedModel"><el-icon><Connection /></el-icon>测试连接</el-button><el-button type="primary" :loading="savingModel" :disabled="!canSaveModel" @click="saveModel"><el-icon><Plus /></el-icon>{{ editingModelId ? '覆盖保存' : '保存模型' }}</el-button></div><el-alert v-if="modelTestMessage" class="model-test-result" :type="modelTestType" show-icon :closable="false" :title="modelTestMessage" /></el-form></el-card><el-card class="config-card" shadow="never"><template #header><div class="card-title-row"><div><strong>已保存模型</strong><small>可按需复测已加密保存的凭据，不会回显 API Key；删除只移除模型配置，不删除历史日志。</small></div></div></template><el-table :data="models" empty-text="暂无模型配置"><el-table-column prop="name" label="显示名称" min-width="150" /><el-table-column prop="modelName" label="模型名称" min-width="160" /><el-table-column prop="apiBaseUrl" label="API 地址" min-width="220" show-overflow-tooltip /><el-table-column label="思考" width="90"><template #default="{ row }"><el-tag size="small" :type="row.supportsThinking ? 'success' : 'info'">{{ row.supportsThinking ? '支持' : '关闭' }}</el-tag></template></el-table-column><el-table-column label="密钥" width="110"><template #default="{ row }"><el-tag size="small" :type="credentialTagType(row.credentialStatus)">{{ credentialLabel(row.credentialStatus) }}</el-tag></template></el-table-column><el-table-column label="操作" width="290"><template #default="{ row }"><el-button size="small" :loading="testingModelId === row.id" @click="testModel(row.id)"><el-icon><Connection /></el-icon>重新测试</el-button><el-button size="small" text type="primary" @click="editModelCredential(row)">重新录入</el-button><el-button size="small" text type="danger" @click="removeModel(row)">删除</el-button></template></el-table-column></el-table></el-card></section>
        <section v-show="activeSection === 'security'" class="settings-section">
          <SectionHeading kicker="安全控制台" title="安全状态与操作边界" description="查看脱敏配置状态，了解写操作门禁与部署边界。" />
          <div class="security-console">
            <section class="security-status-panel"><div class="security-panel-heading"><div><span class="security-eyebrow">配置状态</span><h2>连接与模型</h2><p>只显示配置完成状态，不展示密码或 API Key。</p></div><span class="security-count">{{ configuredSecurityItems }}/3 已配置</span></div><button class="security-status-row" type="button" @click="openSettingsSection('user-cloudpivot')"><span class="security-status-icon"><el-icon><UserFilled /></el-icon></span><span class="security-status-copy"><strong>个人云枢账号</strong><small>问数与业务操作</small></span><span :class="['security-state', { 'is-ready': settings?.userCloudPivot.credentialStatus === 'available' }]">{{ credentialLabel(settings?.userCloudPivot.credentialStatus) }}</span><el-icon class="security-row-arrow"><ArrowRight /></el-icon></button><button class="security-status-row" type="button" @click="openSettingsSection('admin-cloudpivot')"><span class="security-status-icon"><el-icon><CollectionTag /></el-icon></span><span class="security-status-copy"><strong>管理员云枢环境</strong><small>元数据同步与图谱构建</small></span><span :class="['security-state', { 'is-ready': settings?.adminMetadata.credentialStatus === 'available' }]">{{ credentialLabel(settings?.adminMetadata.credentialStatus) }}</span><el-icon class="security-row-arrow"><ArrowRight /></el-icon></button><button class="security-status-row" type="button" @click="openSettingsSection('models')"><span class="security-status-icon"><el-icon><Cpu /></el-icon></span><span class="security-status-copy"><strong>Agent 模型</strong><small>已保存 {{ models.length }} 个模型</small></span><span :class="['security-state', { 'is-ready': models.length > 0 && models.every((model) => model.credentialStatus === 'available') }]">{{ models.some((model) => model.credentialStatus === 'unreadable') ? '需重新录入' : models.length ? '已配置' : '待配置' }}</span><el-icon class="security-row-arrow"><ArrowRight /></el-icon></button></section>
            <div class="security-lower-grid"><section class="security-rule-panel"><div class="security-panel-heading"><div><span class="security-eyebrow">系统规则</span><h2>写操作保护</h2></div><el-icon class="security-rule-icon"><CircleCheck /></el-icon></div><ol class="security-rule-list"><li><span>01</span><p><strong>先生成确认计划</strong><small>新增、修改、删除不会直接执行。</small></p></li><li><span>02</span><p><strong>用户确认后执行</strong><small>系统绑定当前查询结果与目标记录。</small></p></li><li><span>03</span><p><strong>过程与结果可追溯</strong><small>意图、工具调用与校验结果会进入日志分析。</small></p></li></ol></section><aside class="security-audit-panel"><span class="security-eyebrow">可追溯性</span><h2>日志分析</h2><p>按时间、模型和状态查看调用次数、输入输出摘要、Token 与耗时。</p><el-button type="primary" @click="openSettingsSection('log-analytics')">进入日志分析<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button></aside></div>
            <div class="security-deployment-note"><el-icon><Lock /></el-icon><span><strong>部署边界：</strong>数据库、向量库与加密密钥由后端部署配置管理，不在此页面编辑。</span></div>
          </div>
        </section>
      </main>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, ElTag } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, CircleCheck, CollectionTag, Connection, Cpu, DataAnalysis, Finished, Lock, Menu, Plus, Refresh, UserFilled, Share } from '@element-plus/icons-vue'
import MetadataBrowserPanel from '../components/settings/MetadataBrowserPanel.vue'
import MetadataSyncPanel from '../components/settings/MetadataSyncPanel.vue'
import LogAnalyticsPanel from '../components/settings/LogAnalyticsPanel.vue'
import TokenUsageDashboardPanel from '../components/settings/TokenUsageDashboardPanel.vue'
import CloudPivotMcpPanel from '../components/settings/CloudPivotMcpPanel.vue'
import MemorySettingsPanel from '../components/settings/MemorySettingsPanel.vue'
import { deleteModelConfig, getSettings, saveAdminSettings, saveModelConfig, saveUserSettings, testAdminCloudPivotConnection, testModelConnection, testUnsavedModelConnection, testUserCloudPivotConnection, updateModelConfig } from '../services/settingsApi'
import type { ConnectionTestResponse, ModelConfigSummary, ModelConnectionTestResponse, SaveAdminSettingsRequest, SaveUserSettingsRequest, SettingsResponse } from '../types/settings'

const router = useRouter(); const route = useRoute(); const settings = ref<SettingsResponse>(); const loading = ref(false); const savingUser = ref(false); const savingAdmin = ref(false); const savingModel = ref(false); const testingUser = ref(false); const testingAdmin = ref(false); const testingNewModel = ref(false); const testingModelId = ref(''); const deletingModelId = ref(''); const editingModelId = ref(''); const errorMessage = ref(''); const activeSection = ref('user-cloudpivot'); const metadataVersion = ref(0); const darkMode = ref(window.localStorage.getItem('cpclaw-theme') === 'dark'); const settingsSidebarOpen = ref(window.innerWidth > 900); const PASSWORD_MASK = '********'
const navigationGroups = [{ title: '云枢环境连接', items: [{ id: 'user-cloudpivot', title: '个人云枢账号', description: '问数与业务操作', icon: UserFilled }, { id: 'admin-cloudpivot', title: '管理员云枢环境', description: '元数据同步连接', icon: CollectionTag }] }, { title: '元数据管理', items: [{ id: 'metadata-sync', title: '元数据同步', description: '同步云枢应用与能力', icon: Refresh }, { id: 'metadata-browser', title: '元数据查看', description: '应用、字段、关联与 API', icon: DataAnalysis }] }, { title: '集成与发布', items: [{ id: 'mcp-cloudpivot', title: '云枢 MCP 服务', description: 'OpenClaw 客户端接入与能力发布', icon: Share }] }, { title: '智能与安全', items: [{ id: 'memory', title: '记忆设置', description: '个人记忆与系统全局记忆', icon: UserFilled }, { id: 'models', title: 'Agent 模型', description: '模型配置与连通性验证', icon: Cpu }, { id: 'usage-dashboard', title: '用量看板', description: 'Token 趋势与模型分布', icon: DataAnalysis }, { id: 'log-analytics', title: '调用明细', description: '筛选、审计与调用详情', icon: DataAnalysis }, { id: 'security', title: '安全控制台', description: '状态、确认与部署边界', icon: CircleCheck }] }]
const userForm = reactive({ cloudPivotUsername: '', cloudPivotPassword: '' }); const adminForm = reactive({ targetBaseUrl: '', username: '', password: '', searchEngineType: 'mysql', searchEndpoint: '' }); const modelForm = reactive({ modelDisplayName: '', modelName: '', modelApiBaseUrl: '', modelApiKey: '', supportsThinking: false, defaultThinkingEnabled: false }); const models = computed<ModelConfigSummary[]>(() => settings.value?.models ?? [])
const userFormVersion = ref(0); const testedUserFormVersion = ref(-1); const userTestResult = ref<ConnectionTestResponse>(); const canSaveUser = computed(() => userTestResult.value?.success === true && testedUserFormVersion.value === userFormVersion.value); const userTestMessage = computed(() => connectionTestMessage(userTestResult.value, testedUserFormVersion.value, userFormVersion.value)); const userTestType = computed(() => connectionTestType(userTestResult.value, testedUserFormVersion.value, userFormVersion.value)); const adminFormVersion = ref(0); const testedAdminFormVersion = ref(-1); const adminTestResult = ref<ConnectionTestResponse>(); const canSaveAdmin = computed(() => adminTestResult.value?.success === true && testedAdminFormVersion.value === adminFormVersion.value); const adminTestMessage = computed(() => connectionTestMessage(adminTestResult.value, testedAdminFormVersion.value, adminFormVersion.value)); const adminTestType = computed(() => connectionTestType(adminTestResult.value, testedAdminFormVersion.value, adminFormVersion.value)); const modelFormVersion = ref(0); const testedModelFormVersion = ref(-1); const newModelTestResult = ref<ModelConnectionTestResponse>(); const canSaveModel = computed(() => newModelTestResult.value?.success === true && testedModelFormVersion.value === modelFormVersion.value); const modelTestMessage = computed(() => { if (!newModelTestResult.value) return ''; if (testedModelFormVersion.value !== modelFormVersion.value) return '连接字段已变更，请重新测试后保存'; const message = newModelTestResult.value.message; return newModelTestResult.value.latencyMs > 0 ? `${message}（${newModelTestResult.value.latencyMs}ms）` : message }); const modelTestType = computed(() => testedModelFormVersion.value !== modelFormVersion.value ? 'info' : newModelTestResult.value?.success ? 'success' : 'warning')
const configuredSecurityItems = computed(() => [settings.value?.userCloudPivot.credentialStatus === 'available', settings.value?.adminMetadata.credentialStatus === 'available', models.value.length > 0 && models.value.every((model) => model.credentialStatus === 'available')].filter(Boolean).length)
const SectionHeading = defineComponent({ props: { kicker: String, title: String, description: String, tag: String }, setup(props) { return () => h('div', { class: 'section-heading' }, [h('div', [h('div', { class: 'section-kicker' }, props.kicker), h('h1', props.title), h('p', props.description)]), props.tag ? h(ElTag, { effect: 'plain', round: true }, () => props.tag) : null]) } })
function handleSettingsEscape(event: KeyboardEvent) { if (event.key === 'Escape' && window.innerWidth <= 900) settingsSidebarOpen.value = false }
onMounted(() => { syncSectionFromRoute(); void loadSettings(); window.addEventListener('keydown', handleSettingsEscape) }); onBeforeUnmount(() => window.removeEventListener('keydown', handleSettingsEscape)); watch(() => route.query.section, syncSectionFromRoute); watch(activeSection, (section) => { if (route.query.section !== section) void router.replace({ query: { ...route.query, section } }) }); watch(() => [userForm.cloudPivotUsername, userForm.cloudPivotPassword], () => { userFormVersion.value += 1 }, { flush: 'sync' }); watch(() => [adminForm.targetBaseUrl, adminForm.username, adminForm.password], () => { adminFormVersion.value += 1 }, { flush: 'sync' }); watch(() => [modelForm.modelName, modelForm.modelApiBaseUrl, modelForm.modelApiKey], () => { modelFormVersion.value += 1 }, { flush: 'sync' })
async function loadSettings() { errorMessage.value = ''; loading.value = true; try { const next = await getSettings(); settings.value = next; userForm.cloudPivotUsername = next.userCloudPivot.username ?? ''; userForm.cloudPivotPassword = next.userCloudPivot.credentialStatus === 'available' ? PASSWORD_MASK : ''; adminForm.targetBaseUrl = next.adminMetadata.targetBaseUrl ?? ''; adminForm.username = next.adminMetadata.username ?? ''; adminForm.password = next.adminMetadata.credentialStatus === 'available' ? PASSWORD_MASK : '' } catch (error) { reportError(error) } finally { loading.value = false } }
async function saveUser() { if (!canSaveUser.value) { ElMessage.warning('请先验证当前输入，验证通过后再保存'); return } savingUser.value = true; try { settings.value = await saveUserSettings(userConnectionPayload()); userForm.cloudPivotPassword = settings.value.userCloudPivot.credentialStatus === 'available' ? PASSWORD_MASK : ''; userTestResult.value = undefined; testedUserFormVersion.value = -1; ElMessage.success('个人云枢账号已保存') } catch (error) { reportError(error) } finally { savingUser.value = false } }
async function saveAdmin() { if (!canSaveAdmin.value) { ElMessage.warning('请先验证当前输入，验证通过后再保存'); return } savingAdmin.value = true; try { settings.value = await saveAdminSettings(adminConnectionPayload()); adminForm.password = settings.value.adminMetadata.credentialStatus === 'available' ? PASSWORD_MASK : ''; adminTestResult.value = undefined; testedAdminFormVersion.value = -1; ElMessage.success('管理员云枢环境已保存') } catch (error) { reportError(error) } finally { savingAdmin.value = false } }
async function testUnsavedModel() { if (!modelForm.modelName.trim() || !modelForm.modelApiBaseUrl.trim() || !modelForm.modelApiKey.trim()) { ElMessage.warning('请填写模型名称、API 地址和 API Key'); return } const requestedVersion = modelFormVersion.value; errorMessage.value = ''; testingNewModel.value = true; try { newModelTestResult.value = await testUnsavedModelConnection({ ...modelForm }); testedModelFormVersion.value = requestedVersion } catch (error) { newModelTestResult.value = undefined; reportError(error, 'model-test') } finally { testingNewModel.value = false } }
async function saveModel() { if (!canSaveModel.value) { ElMessage.warning('请先测试当前模型配置，验证通过后再保存'); return } savingModel.value = true; try { if (editingModelId.value) await updateModelConfig(editingModelId.value, modelForm); else await saveModelConfig(modelForm); const replaced = Boolean(editingModelId.value); resetModelForm(); await loadSettings(); ElMessage.success(replaced ? '模型凭据已覆盖保存' : '模型已保存') } catch (error) { reportError(error) } finally { savingModel.value = false } }
async function testUser() { const requestedVersion = userFormVersion.value; testingUser.value = true; try { userTestResult.value = await testUserCloudPivotConnection(userConnectionPayload()); testedUserFormVersion.value = requestedVersion } catch (error) { userTestResult.value = undefined; reportError(error) } finally { testingUser.value = false } }; async function testAdmin() { const requestedVersion = adminFormVersion.value; testingAdmin.value = true; try { adminTestResult.value = await testAdminCloudPivotConnection(adminConnectionPayload()); testedAdminFormVersion.value = requestedVersion } catch (error) { adminTestResult.value = undefined; reportError(error) } finally { testingAdmin.value = false } }
async function testModel(id: string) { errorMessage.value = ''; testingModelId.value = id; try { const result = await testModelConnection(id); const message = result.latencyMs > 0 ? `${result.message}（${result.latencyMs}ms）` : result.message; result.success ? ElMessage.success(message) : ElMessage.warning(message) } catch (error) { reportError(error, 'model-test') } finally { testingModelId.value = '' } }
async function removeModel(model: ModelConfigSummary) { try { await ElMessageBox.confirm(`确定删除模型“${model.name}”吗？这会移除模型配置和 API Key，但会保留历史会话、日志和 Token 记录。`, '删除模型配置', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }); deletingModelId.value = model.id; await deleteModelConfig(model.id); if (editingModelId.value === model.id) resetModelForm(); await loadSettings(); ElMessage.success('模型配置已删除') } catch (error) { if (error === 'cancel' || error === 'close') return; reportError(error) } finally { deletingModelId.value = '' } }
function onSupportsThinkingChange(value: boolean) { if (!value) modelForm.defaultThinkingEnabled = false }; function resetModelForm() { editingModelId.value = ''; newModelTestResult.value = undefined; testedModelFormVersion.value = -1; Object.assign(modelForm, { modelDisplayName: '', modelName: '', modelApiBaseUrl: '', modelApiKey: '', supportsThinking: false, defaultThinkingEnabled: false }) }; function editModelCredential(model: ModelConfigSummary) { editingModelId.value = model.id; newModelTestResult.value = undefined; testedModelFormVersion.value = -1; Object.assign(modelForm, { modelDisplayName: model.name, modelName: model.modelName, modelApiBaseUrl: model.apiBaseUrl, modelApiKey: '', supportsThinking: model.supportsThinking, defaultThinkingEnabled: model.defaultThinkingEnabled }); activeSection.value = 'models' }; function credentialLabel(status?: string) { return status === 'available' ? '已配置' : status === 'unreadable' ? '需重新录入' : '待配置' }; function credentialTagType(status?: string) { return status === 'available' ? 'success' : 'warning' }; function passwordFieldLabel(status: string | undefined, label: string) { return status === 'available' ? `${label}（已保存，填写则覆盖）` : status === 'unreadable' ? `${label}（需重新输入）` : label }; function passwordFieldPlaceholder(status: string | undefined, missingPlaceholder: string) { return status === 'available' ? '已保存，点击后输入新密码' : status === 'unreadable' ? '请重新输入密码以覆盖旧凭据' : missingPlaceholder }; function reportError(error: unknown, source?: 'model-test') { const rawMessage = error instanceof Error && error.message ? error.message : ''; const dependencyMismatch = /PartialMatchHelper|Handler dispatch failed/i.test(rawMessage); const routeMismatch = /No static resource.*(settings\/models|audit\/analytics)/i.test(rawMessage); errorMessage.value = dependencyMismatch ? '后端服务版本或依赖不一致，请重启新版后端后重试。' : routeMismatch ? (source === 'model-test' ? '当前后端未加载模型验证接口，请重启新版后端后重试。' : '当前后端未加载对应接口，请重启新版后端后重试。') : (rawMessage || '请求失败，请稍后重试'); if (source !== 'model-test') ElMessage.error(errorMessage.value) }; function goBack() { void router.push('/') }; function syncSectionFromRoute() { const section = route.query.section; if (typeof section === 'string' && navigationGroups.some((group) => group.items.some((item) => item.id === section))) activeSection.value = section }
function selectSection(section: string) { activeSection.value = section; if (window.innerWidth <= 900) settingsSidebarOpen.value = false }
function openSettingsSection(section: string) { selectSection(section) }
function goAudit() { openSettingsSection('log-analytics') }
function userConnectionPayload(): SaveUserSettingsRequest { return { ...userForm, cloudPivotPassword: editablePassword(userForm.cloudPivotPassword), modelDisplayName: '', modelName: '', modelApiBaseUrl: '', modelApiKey: '', supportsThinking: false, defaultThinkingEnabled: false } }
function adminConnectionPayload(): SaveAdminSettingsRequest { return { ...adminForm, password: editablePassword(adminForm.password) } }
function connectionTestMessage(result: ConnectionTestResponse | undefined, testedVersion: number, currentVersion: number) { if (!result) return ''; return testedVersion === currentVersion ? result.message : '连接字段已变更，请重新验证后保存' }
function connectionTestType(result: ConnectionTestResponse | undefined, testedVersion: number, currentVersion: number) { return testedVersion !== currentVersion ? 'info' : result?.success ? 'success' : 'warning' }
function editablePassword(value: string) { return value === PASSWORD_MASK ? '' : value }
function revealPasswordField(scope: 'user' | 'admin') { if (scope === 'user' && userForm.cloudPivotPassword === PASSWORD_MASK) userForm.cloudPivotPassword = ''; if (scope === 'admin' && adminForm.password === PASSWORD_MASK) adminForm.password = '' }
function restorePasswordMask(scope: 'user' | 'admin') { if (scope === 'user' && settings.value?.userCloudPivot.credentialStatus === 'available' && !userForm.cloudPivotPassword) userForm.cloudPivotPassword = PASSWORD_MASK; if (scope === 'admin' && settings.value?.adminMetadata.credentialStatus === 'available' && !adminForm.password) adminForm.password = PASSWORD_MASK }
</script>

<style scoped>
.settings-workbench {
  /* Shared CPClaw shell tokens: keep Settings and Chat as two content modes
     inside the same application frame. */
  --settings-page: #f7f8fa;
  --settings-sidebar: #eef1f5;
  --settings-surface: #fff;
  --settings-surface-soft: #f2f4f7;
  --settings-line: rgba(18, 28, 45, .08);
  --settings-line-strong: rgba(18, 28, 45, .14);
  --settings-text: #171c2b;
  --settings-secondary: #657084;
  --settings-tertiary: #98a2b3;
  --settings-brand: #4f6ef7;
  --settings-brand-strong: #3f5ce0;
  --settings-brand-soft: rgba(79, 110, 247, .10);
  --settings-radius: 12px;
  --settings-content-width: 1120px;
  --settings-section-gap: 24px;
  --settings-header-height: 56px;
  /* Match the conversation shell so the settings route feels like the same
     product surface, not a separate admin application. */
  --settings-sidebar-width: 288px;
  --settings-footer-height: 60px;
  display: flex;
  min-height: 100vh;
  background: var(--settings-page);
  color: var(--settings-text);
}
.settings-workbench--dark {
  --settings-page:#0f141d;
  --settings-sidebar:#111722;
  --settings-surface:#171e29;
  --settings-surface-soft:#1b2330;
  --settings-line:rgba(255,255,255,.07);
  --settings-line-strong:rgba(255,255,255,.13);
  --settings-text:#eef2f7;
  --settings-secondary:#a6afbe;
  --settings-tertiary:#727d8e;
  --settings-brand:#6f89ff;
  --settings-brand-strong:#8da1ff;
  --settings-brand-soft:rgba(111,137,255,.13);
  --el-bg-color:#171e29;
  --el-bg-color-overlay:#1b2330;
  --el-fill-color-blank:#171e29;
  --el-fill-color:#1b2330;
  --el-fill-color-light:#202a39;
  --el-fill-color-lighter:#263142;
  --el-fill-color-dark:#111722;
  --el-border-color:#2a3546;
  --el-border-color-light:#253142;
  --el-border-color-lighter:#202a39;
  --el-text-color-primary:#eef2f7;
  --el-text-color-regular:#c2cad6;
  --el-text-color-secondary:#a6afbe;
  --el-text-color-placeholder:#727d8e;
  --el-disabled-bg-color:#141b25;
  --el-disabled-text-color:#626d7d;
}
.settings-panel { display:flex; flex:0 0 var(--settings-sidebar-width); flex-direction:column; border-right:1px solid var(--settings-line); background:var(--settings-sidebar); }
.settings-panel__header, .settings-toolbar { display:flex; height:var(--settings-header-height); min-height:var(--settings-header-height); align-items:center; border-bottom:1px solid var(--settings-line); }
.settings-panel__header { gap:10px; padding:0 14px 0 18px; }
.settings-brand-mark { display:grid; width:32px; height:32px; border-radius:8px; background:var(--settings-brand); color:#fff; font-size:17px; font-weight:700; place-items:center; box-shadow:0 7px 18px rgba(79,110,247,.22); }
.settings-panel__header>strong { overflow:hidden; color:var(--settings-text); font-size:16px; font-weight:700; text-overflow:ellipsis; white-space:nowrap; }
.panel-back { margin-left:auto; color:var(--settings-secondary); }
.settings-nav { padding:12px 0; overflow:auto; }
.settings-panel__label { padding:16px 18px 8px; color:var(--settings-tertiary); font-size:12px; font-weight:600; letter-spacing:.08em; line-height:18px; }
.panel-nav-item { display:flex; width:calc(100% - 20px); align-items:center; gap:12px; margin:2px 10px; padding:10px 12px; border:0; border-radius:10px; background:transparent; color:var(--settings-secondary); text-align:left; cursor:pointer; transition:background-color .16s ease, color .16s ease; }
.panel-nav-item:hover { background:var(--settings-brand-soft); color:var(--settings-text); }
.panel-nav-item.active { background:var(--settings-brand-soft); color:var(--settings-brand); box-shadow:inset 3px 0 var(--settings-brand); }
.panel-nav-icon { display:grid; flex:0 0 34px; width:34px; height:34px; border-radius:9px; background:var(--settings-surface); color:currentColor; place-items:center; }
.panel-nav-item>span:last-child { display:grid; min-width:0; gap:3px; }
.panel-nav-item strong { overflow:hidden; font-size:14px; font-weight:600; line-height:20px; text-overflow:ellipsis; white-space:nowrap; }
.panel-nav-item small { overflow:hidden; color:var(--settings-tertiary); font-size:12px; line-height:17px; text-overflow:ellipsis; white-space:nowrap; }
.settings-panel__footer { display:grid; grid-template-columns:32px minmax(0,1fr); min-height:var(--settings-footer-height); align-items:center; gap:9px; margin-top:auto; padding:8px 14px; border-top:1px solid var(--settings-line); }
.settings-avatar { display:grid; width:32px; height:32px; border-radius:50%; background:linear-gradient(135deg,#7159e8,var(--settings-brand)); color:#fff; font-size:10px; font-weight:700; place-items:center; }
.settings-identity__copy { display:grid; min-width:0; gap:2px; }
.settings-identity__copy strong, .settings-identity__copy small { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.settings-identity__copy strong { color:var(--settings-text); font-size:13px; font-weight:600; line-height:18px; }
.settings-identity__copy small { color:var(--settings-tertiary); font-size:11px; line-height:16px; }
.settings-main { min-width:0; flex:1; }
.settings-toolbar { position:sticky; top:0; z-index:10; justify-content:space-between; padding:0 clamp(18px, 4vw, 54px); background:color-mix(in srgb, var(--settings-page) 92%, transparent); backdrop-filter:blur(14px); }
.settings-toolbar>strong { color:var(--settings-text); font-size:15px; font-weight:600; letter-spacing:0; }
.settings-toolbar>div { display:flex; gap:8px; }
.settings-toolbar .el-button { color:var(--settings-secondary); }
.refresh-button { border-color:var(--settings-line); background:var(--settings-surface); }
.settings-page { width:100%; padding:32px clamp(18px, 4vw, 54px) 48px; background:var(--settings-page); }
.settings-section { display:grid; max-width:var(--settings-content-width); gap:var(--settings-section-gap); }
.settings-error { max-width:var(--settings-content-width); margin-bottom:16px; }
.section-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:24px; }
.section-kicker { color:var(--settings-brand); font-size:12px; font-weight:600; letter-spacing:.08em; line-height:18px; }
.section-heading h1 { margin:8px 0 7px; font-size:clamp(25px,3vw,34px); font-weight:650; letter-spacing:-.025em; line-height:1.25; }
.section-heading p { max-width:760px; margin:0; color:var(--settings-secondary); font-size:14px; line-height:1.7; }
.config-card { border-color:var(--settings-line); border-radius:var(--settings-radius); background:var(--settings-surface); box-shadow:none; }
.config-card :deep(.el-card__header) { border-color:var(--settings-line); padding:18px 22px; }
.config-card :deep(.el-card__body) { padding:22px; }
.form-card { max-width:960px; }
.config-card :deep(.el-form-item) { margin-bottom:20px; }
.config-card :deep(.el-form-item__label) { padding-bottom:8px; color:var(--settings-secondary); font-size:14px; font-weight:600; line-height:20px; }
.config-card :deep(.el-input__wrapper), .config-card :deep(.el-select__wrapper) { min-height:44px; background:var(--settings-surface); box-shadow:0 0 0 1px var(--settings-line) inset; }
.config-card :deep(.el-input__inner), .config-card :deep(.el-select__selected-item) { color:var(--settings-text); }
.environment-readonly { display:flex; min-height:44px; align-items:center; gap:9px; padding:0 12px; border:1px solid var(--settings-line); border-radius:8px; color:var(--settings-secondary); font-size:14px; }
.environment-readonly .environment-dot { width:8px; height:8px; border-radius:50%; background:#f59e0b; }.environment-readonly .environment-dot.ready { background:#22c55e; }.environment-readonly .el-button { margin-left:auto; }
.deployment-note { align-items:flex-start; margin:2px 0 20px; padding:13px 14px; border-radius:10px; }
.credential-alert { align-items:flex-start; margin:2px 0 20px; padding:13px 14px; border-radius:10px; }
.deployment-note :deep(.el-alert__title) { font-weight:700; }
.deployment-note :deep(.el-alert__description) { margin-top:4px; color:var(--settings-secondary); line-height:1.55; }
.form-actions, .model-actions { display:flex; flex-wrap:wrap; gap:10px; }
.form-actions { padding-top:18px; border-top:1px solid var(--settings-line); }
.admin-options { display:grid; grid-template-columns:160px minmax(0,1fr); gap:12px; }
.card-title-row { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; }
.card-title-row>div { display:grid; gap:4px; }
.card-title-row small { color:var(--settings-secondary); font-size:12px; line-height:1.5; }
.model-form { display:grid; grid-template-columns:repeat(4,minmax(150px,1fr)); gap:0 16px; }
.model-options { display:flex; align-items:center; gap:18px; }
.model-actions { grid-column:2 / -1; justify-content:flex-start; }
.model-test-result { grid-column:1 / -1; margin-top:4px; }
.panel-nav-item:focus-visible, .panel-back:focus-visible, .settings-nav-toggle:focus-visible { outline:2px solid var(--settings-brand); outline-offset:2px; }
.settings-toolbar__leading { display:flex; min-width:0; align-items:center; gap:8px; }
.settings-nav-toggle { display:none; color:var(--settings-secondary); }
.settings-nav-backdrop { display:none; }
.security-console { display:grid; max-width:var(--settings-content-width); gap:16px; }
.security-status-panel, .security-rule-panel, .security-audit-panel { border:1px solid var(--settings-line); border-radius:var(--settings-radius); background:var(--settings-surface); }
.security-status-panel { overflow:hidden; }
.security-panel-heading { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; padding:22px 24px 18px; }
.security-eyebrow { display:block; margin-bottom:5px; color:var(--settings-brand); font-size:11px; font-weight:600; letter-spacing:.08em; }
.security-panel-heading h2, .security-audit-panel h2 { margin:0; font-size:18px; letter-spacing:-.01em; }
.security-panel-heading p, .security-audit-panel p { margin:6px 0 0; color:var(--settings-secondary); font-size:13px; line-height:1.55; }
.security-count { padding:5px 9px; border-radius:999px; background:var(--settings-brand-soft); color:var(--settings-brand); font-size:12px; font-weight:700; white-space:nowrap; }
.security-status-row { display:grid; width:100%; grid-template-columns:34px minmax(0,1fr) auto 18px; align-items:center; gap:12px; padding:14px 24px; border:0; border-top:1px solid var(--settings-line); background:transparent; color:var(--settings-text); text-align:left; cursor:pointer; }
.security-status-row:hover { background:var(--settings-brand-soft); }
.security-status-row:focus-visible { outline:2px solid var(--settings-brand); outline-offset:-2px; }
.security-status-icon { display:grid; width:34px; height:34px; border-radius:9px; background:var(--settings-brand-soft); color:var(--settings-brand); place-items:center; }
.security-status-copy { display:grid; gap:3px; }
.security-status-copy strong { font-size:13px; }.security-status-copy small { color:var(--settings-secondary); font-size:12px; }
.security-state { padding:4px 8px; border-radius:999px; background:rgba(245,158,11,.12); color:#a16207; font-size:12px; font-weight:700; }.security-state.is-ready { background:rgba(34,197,94,.12); color:#15803d; }
.security-row-arrow { color:var(--settings-tertiary); }.security-lower-grid { display:grid; grid-template-columns:minmax(0,1.45fr) minmax(250px,.8fr); gap:16px; }.security-rule-panel { padding:2px 0; }.security-rule-icon { margin:2px; color:var(--settings-brand); font-size:22px; }
.security-rule-list { display:grid; gap:2px; margin:0; padding:0 24px 20px; list-style:none; }.security-rule-list li { display:flex; gap:12px; align-items:flex-start; padding:11px 0; }.security-rule-list li+li { border-top:1px solid var(--settings-line); }.security-rule-list>li>span { padding-top:1px; color:var(--settings-tertiary); font-size:11px; font-weight:600; }.security-rule-list p { display:grid; gap:3px; margin:0; }.security-rule-list strong { font-size:13px; font-weight:600; }.security-rule-list small { color:var(--settings-secondary); font-size:12px; }
.security-audit-panel { display:flex; flex-direction:column; align-items:flex-start; padding:24px; }.security-audit-panel p { flex:1; margin-bottom:20px; }.security-deployment-note { display:flex; align-items:flex-start; gap:8px; padding:2px 4px; color:var(--settings-secondary); font-size:12px; line-height:1.55; }.security-deployment-note .el-icon { margin-top:2px; color:var(--settings-brand); }
.settings-page :deep(.mcp-panel) { max-width:var(--settings-content-width); gap:var(--settings-section-gap); }
.settings-page :deep(.mcp-eyebrow) { font-size:12px; }
.settings-page :deep(.mcp-hero p) { font-size:14px; line-height:1.7; }

/* Element Plus components are globally light by default. Keep every settings
   panel on the same dark surface instead of allowing white tables, inputs or
   alerts to leak through the dark shell. */
.settings-workbench--dark :deep(.el-card),
.settings-workbench--dark :deep(.el-table),
.settings-workbench--dark :deep(.el-table tr),
.settings-workbench--dark :deep(.el-table th.el-table__cell),
.settings-workbench--dark :deep(.el-table__expanded-cell) { background:var(--settings-surface); color:var(--settings-text); }
.settings-workbench--dark :deep(.el-table th.el-table__cell) { background:var(--settings-surface-soft); color:var(--settings-secondary); font-weight:650; }
.settings-workbench--dark :deep(.el-table td.el-table__cell),
.settings-workbench--dark :deep(.el-table th.el-table__cell.is-leaf),
.settings-workbench--dark :deep(.el-table--border .el-table__cell) { border-color:var(--settings-line); }
.settings-workbench--dark :deep(.el-table--enable-row-hover .el-table__body tr:hover > td.el-table__cell),
.settings-workbench--dark :deep(.el-table__body tr.current-row > td.el-table__cell) { background:rgba(111,137,255,.09); }
.settings-workbench--dark :deep(.el-table__inner-wrapper::before),
.settings-workbench--dark :deep(.el-table--border::after),
.settings-workbench--dark :deep(.el-table--group::after) { background:var(--settings-line); }
.settings-workbench--dark :deep(.el-input__wrapper),
.settings-workbench--dark :deep(.el-select__wrapper),
.settings-workbench--dark :deep(.el-textarea__inner),
.settings-workbench--dark :deep(.el-input-group__append),
.settings-workbench--dark :deep(.el-input-group__prepend) { background:var(--settings-surface-soft); color:var(--settings-text); box-shadow:0 0 0 1px var(--settings-line-strong) inset; }
.settings-workbench--dark :deep(.el-input__wrapper:hover),
.settings-workbench--dark :deep(.el-input__wrapper.is-focus),
.settings-workbench--dark :deep(.el-select__wrapper:hover),
.settings-workbench--dark :deep(.el-select__wrapper.is-focused),
.settings-workbench--dark :deep(.el-textarea__inner:focus) { box-shadow:0 0 0 1px var(--settings-brand) inset,0 0 0 3px rgba(111,137,255,.12); }
.settings-workbench--dark :deep(.el-input__inner),
.settings-workbench--dark :deep(.el-textarea__inner),
.settings-workbench--dark :deep(.el-select__selected-item),
.settings-workbench--dark :deep(.el-select__placeholder) { color:var(--settings-text); }
.settings-workbench--dark :deep(.el-input__inner::placeholder),
.settings-workbench--dark :deep(.el-textarea__inner::placeholder) { color:var(--settings-tertiary); }
.settings-workbench--dark :deep(.el-input-group__append),
.settings-workbench--dark :deep(.el-input-group__prepend) { border-color:var(--settings-line); color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-alert) { border:1px solid var(--settings-line-strong); background:var(--settings-surface-soft); color:var(--settings-text); }
.settings-workbench--dark :deep(.el-alert--error) { border-color:rgba(248,113,113,.28); background:rgba(127,29,29,.22); }
.settings-workbench--dark :deep(.el-alert--warning) { border-color:rgba(251,191,36,.26); background:rgba(120,73,10,.2); }
.settings-workbench--dark :deep(.el-alert--info) { border-color:rgba(96,165,250,.26); background:rgba(30,64,175,.18); }
.settings-workbench--dark :deep(.el-alert--success) { border-color:rgba(74,222,128,.26); background:rgba(20,83,45,.2); }
.settings-workbench--dark :deep(.el-alert__title),
.settings-workbench--dark :deep(.el-alert__description) { color:var(--settings-text); }
.settings-workbench--dark :deep(.el-alert__description) { color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-tabs__nav-wrap::after),
.settings-workbench--dark :deep(.el-tabs__active-bar) { background:var(--settings-line); }
.settings-workbench--dark :deep(.el-tabs__item) { color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-tabs__item.is-active),
.settings-workbench--dark :deep(.el-tabs__item:hover) { color:var(--settings-brand-strong); }
.settings-workbench--dark :deep(.el-empty__description p),
.settings-workbench--dark :deep(.el-loading-text) { color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-pagination button),
.settings-workbench--dark :deep(.el-pager li) { background:var(--settings-surface-soft); color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-pager li.is-active) { background:var(--settings-brand); color:#fff; }
.settings-workbench--dark :deep(.el-pagination button:disabled) { background:var(--settings-surface); color:var(--settings-tertiary); }
.settings-workbench--dark :deep(.el-tag--info) { border-color:rgba(148,163,184,.28); background:rgba(100,116,139,.16); color:#cbd5e1; }
.settings-workbench--dark :deep(.el-button:not(.is-text):not(.is-link)) { border-color:var(--settings-line-strong); background:var(--settings-surface-soft); color:var(--settings-text); }
.settings-workbench--dark :deep(.el-button--primary:not(.is-text):not(.is-link)) { border-color:var(--settings-brand); background:var(--settings-brand); color:#fff; }
.settings-workbench--dark :deep(.el-button--danger:not(.is-text):not(.is-link)) { border-color:#b45353; background:#8f3d49; color:#fff; }
.settings-workbench--dark :deep(.el-button.is-text),
.settings-workbench--dark :deep(.el-button.is-link) { color:var(--settings-brand-strong); }
.settings-workbench--dark :deep(.el-checkbox__label),
.settings-workbench--dark :deep(.el-form-item__label) { color:var(--settings-secondary); }
.settings-workbench--dark :deep(.el-checkbox__inner) { border-color:#526174; background:var(--settings-surface-soft); }
.settings-workbench--dark :deep(.el-checkbox.is-checked .el-checkbox__inner) { border-color:var(--settings-brand); background:var(--settings-brand); }
.settings-workbench--dark :deep(.el-drawer),
.settings-workbench--dark :deep(.el-dialog) { background:var(--settings-surface); color:var(--settings-text); }
.settings-workbench--dark :deep(.el-drawer__header),
.settings-workbench--dark :deep(.el-dialog__header) { color:var(--settings-text); border-color:var(--settings-line); }
.settings-workbench--dark :deep(.el-drawer__close),
.settings-workbench--dark :deep(.el-dialog__headerbtn) { color:var(--settings-secondary); }
@media(max-width:900px) { .settings-nav-toggle { display:inline-flex; }.settings-nav-backdrop { position:fixed; inset:0; z-index:19; display:block; background:rgba(13,21,36,.22); }.settings-panel { position:fixed; inset:0 auto 0 0; z-index:20; width:min(var(--settings-sidebar-width),86vw); flex-basis:auto; box-shadow:16px 0 40px rgba(13,21,36,.18); transform:translateX(-105%); transition:transform .2s ease; }.settings-workbench--nav-open .settings-panel { transform:translateX(0); }.settings-panel__header { justify-content:flex-start; padding:0 14px 0 18px; }.settings-panel__header>strong,.panel-back,.settings-panel__label,.panel-nav-item>span:last-child,.settings-panel__footer>span:last-child { display:initial; }.panel-back { display:inline-flex; }.settings-panel__label { display:block; }.panel-nav-item>span:last-child { display:grid; }.settings-panel__footer { justify-content:initial; padding:8px 14px; }.settings-main { width:100%; }.settings-page { padding:28px 28px 40px; }.model-form { grid-template-columns:repeat(2,minmax(0,1fr)); }.model-options,.model-actions { grid-column:1 / -1; }.security-lower-grid { grid-template-columns:1fr; } }
@media(max-width:620px) { .settings-toolbar { padding:0 14px; }.settings-toolbar>div .el-button:first-child { font-size:0; }.settings-page { padding:24px 14px 36px; }.section-heading { flex-direction:column; gap:12px; }.section-heading h1 { font-size:25px; }.section-heading .el-tag { align-self:flex-start; }.config-card :deep(.el-card__body) { padding:18px; }.admin-options,.model-form { grid-template-columns:1fr; }.model-options { flex-wrap:wrap; }.form-actions { align-items:stretch; flex-direction:column; }.form-actions .el-button { width:100%; margin-left:0; }.security-status-row { grid-template-columns:34px minmax(0,1fr) auto; gap:10px; padding:13px 16px; }.security-row-arrow { display:none; }.security-panel-heading,.security-audit-panel { padding:18px; }.security-rule-list { padding:0 18px 14px; } }
</style>
