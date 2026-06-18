<template>
  <PageHeader title="AI 助手" description="输入你想在云枢中完成的事情，CPClaw 会自动理解、匹配业务能力并返回结果。" />
  <el-card class="chat-shell" shadow="never">
    <div class="chat-messages">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-state__title">告诉我你想做什么</div>
        <div class="empty-state__description">请输入你想查询或操作的云枢业务目标，例如：查询系统商机情况。系统会展示意图识别、元数据匹配、执行步骤和审计编号。</div>
      </div>

      <div v-for="message in messages" :key="message.id" :class="['message', `message--${message.role}`]">
        <div class="message__role">{{ roleLabel(message.role) }}</div>
        <MarkdownMessage :content="message.content" />
      </div>

      <el-card v-if="lastAgent" class="workflow-card" shadow="never">
        <template #header>
          <div class="workflow-card__header">
            <span>后端处理流程</span>
            <el-tag size="small" type="info">Agent Run: {{ lastAgent.agentRunId }}</el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="识别意图">
            <el-tag size="small">{{ lastAgent.intent }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag size="small" :type="riskTagType(lastAgent.riskLevel)">{{ riskLabel(lastAgent.riskLevel) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="元数据匹配" :span="2">
            {{ lastAgent.matchReason || '暂无匹配说明' }}
          </el-descriptions-item>
          <el-descriptions-item label="处理摘要" :span="2">
            {{ lastAgent.planSummary }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="lastAgent.candidates.length" class="workflow-section">
          <div class="workflow-section__title">匹配到的云枢对象</div>
          <el-table :data="lastAgent.candidates" size="small" border>
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="reason" label="匹配原因" min-width="220" />
          </el-table>
        </div>

        <div v-if="lastAgent.steps.length" class="workflow-section">
          <div class="workflow-section__title">执行步骤</div>
          <el-timeline>
            <el-timeline-item
              v-for="step in lastAgent.steps"
              :key="`${step.title}-${step.status}`"
              :type="stepType(step.status)"
              :timestamp="step.status"
            >
              {{ step.title }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-card>

      <el-alert v-if="errorMessage" class="chat-error" type="error" show-icon :closable="false" :title="errorMessage" />

      <el-alert v-if="confirmationSummary" class="confirmation" type="warning" show-icon :closable="false">
        <template #title>这个操作可能会修改云枢数据，请确认后继续。</template>
        <div class="confirmation__summary">{{ confirmationSummary }}</div>
        <el-button class="confirmation__button" type="warning" :loading="confirming" @click="confirmLastOperation">确认继续</el-button>
      </el-alert>
    </div>

    <div class="chat-input">
      <el-input
        v-model="input"
        type="textarea"
        :rows="4"
        resize="none"
        placeholder="例如：查询系统商机情况"
        :disabled="submitting"
        @keydown.enter.exact.prevent="submit"
      />
      <div class="chat-input__actions">
        <el-button type="primary" :disabled="!input.trim() || submitting" :loading="submitting" @click="submit">
          {{ submitting ? '处理中...' : '发送' }}
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/common/PageHeader.vue'
import MarkdownMessage from '../components/chat/MarkdownMessage.vue'
import { createConversation, sendMessage } from '../services/conversationApi'
import { confirmOperation } from '../services/auditApi'
import { listModelConfigs } from '../services/settingsApi'
import type { AgentResponse } from '../types/agent'
import type { MessageItem } from '../types/conversation'
import type { ModelConfigSummary } from '../types/settings'

const selectedModelId = ref('')
const thinkingEnabled = ref(false)
const models = ref<ModelConfigSummary[]>([])
const conversationId = ref('')
const messages = ref<MessageItem[]>([])
const input = ref('')
const lastAgent = ref<AgentResponse>()
const submitting = ref(false)
const confirming = ref(false)
const errorMessage = ref('')

const confirmationSummary = computed(() => (lastAgent.value?.requiresConfirmation ? lastAgent.value.planSummary : ''))

onMounted(async () => {
  try {
    models.value = await listModelConfigs()
    selectedModelId.value = models.value[0]?.id ?? ''
    thinkingEnabled.value = models.value[0]?.defaultThinkingEnabled ?? false
  } catch (error) {
    errorMessage.value = messageFromError(error)
  }
})

async function startConversation() {
  const conversation = await createConversation({
    title: '新会话',
    modelConfigId: selectedModelId.value,
    thinkingEnabled: thinkingEnabled.value
  })
  conversationId.value = conversation.id
  messages.value = []
  lastAgent.value = undefined
}

async function submit() {
  const userContent = input.value.trim()
  if (!userContent || submitting.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  const draft = input.value
  input.value = ''
  const localMessage: MessageItem = {
    id: crypto.randomUUID(),
    role: 'user',
    content: userContent,
    createdAt: new Date().toISOString()
  }
  messages.value.push(localMessage)

  try {
    if (!conversationId.value) {
      await startConversation()
      messages.value.push(localMessage)
    }
    const response = await sendMessage({
      conversationId: conversationId.value,
      content: userContent,
      modelConfigId: selectedModelId.value,
      thinkingEnabled: thinkingEnabled.value,
      attachmentIds: []
    })
    lastAgent.value = response
    messages.value.push(response.assistantMessage)
  } catch (error) {
    input.value = draft
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    submitting.value = false
  }
}

async function confirmLastOperation() {
  if (!lastAgent.value?.confirmationId || confirming.value) {
    return
  }
  confirming.value = true
  errorMessage.value = ''
  try {
    await confirmOperation(lastAgent.value.confirmationId)
    lastAgent.value = {
      ...lastAgent.value,
      requiresConfirmation: false,
      confirmationId: undefined
    }
    ElMessage.success('已确认，系统已记录本次操作。')
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    confirming.value = false
  }
}

function roleLabel(role: MessageItem['role']) {
  return role === 'user' ? '你' : role === 'assistant' ? 'CPClaw' : '系统'
}

function riskLabel(riskLevel: AgentResponse['riskLevel']) {
  const labels: Record<AgentResponse['riskLevel'], string> = {
    none: '无风险',
    low: '低风险',
    medium: '中风险',
    high: '高风险'
  }
  return labels[riskLevel]
}

function riskTagType(riskLevel: AgentResponse['riskLevel']) {
  const types: Record<AgentResponse['riskLevel'], 'info' | 'success' | 'warning' | 'danger'> = {
    none: 'info',
    low: 'success',
    medium: 'warning',
    high: 'danger'
  }
  return types[riskLevel]
}

function stepType(status: string) {
  const value = status.toLowerCase()
  if (['success', 'completed', 'done'].includes(value)) {
    return 'success'
  }
  if (['processing', 'running'].includes(value)) {
    return 'primary'
  }
  if (['warning', 'skipped', 'pending-confirmation'].includes(value)) {
    return 'warning'
  }
  if (['error', 'failed'].includes(value)) {
    return 'danger'
  }
  return 'info'
}

function messageFromError(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}
</script>

<style scoped>
.chat-shell {
  min-height: calc(100vh - 120px);
  display: grid;
  grid-template-rows: 1fr auto;
  gap: 20px;
}

.chat-messages {
  display: grid;
  align-content: start;
  gap: 16px;
  min-height: 420px;
}

.empty-state {
  align-self: center;
  justify-self: center;
  max-width: 560px;
  padding: 40px;
  text-align: center;
  color: #667085;
}

.empty-state__title {
  margin-bottom: 12px;
  color: #101828;
  font-size: 22px;
  font-weight: 700;
}

.empty-state__description {
  line-height: 1.8;
}

.message {
  max-width: 820px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #f2f4f7;
}

.message--user {
  justify-self: end;
  background: #e8f3ff;
}

.message--assistant {
  justify-self: start;
  background: #f7f7fb;
}

.message__role {
  margin-bottom: 8px;
  color: #475467;
  font-size: 13px;
  font-weight: 600;
}

.chat-error,
.confirmation,
.workflow-card {
  max-width: 920px;
}

.workflow-card {
  border-color: #d0d5dd;
}

.workflow-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-weight: 600;
}

.workflow-section {
  margin-top: 16px;
}

.workflow-section__title {
  margin-bottom: 10px;
  color: #344054;
  font-size: 14px;
  font-weight: 600;
}

.confirmation__summary {
  margin-top: 8px;
  line-height: 1.6;
}

.confirmation__button {
  margin-top: 12px;
}

.chat-input {
  display: grid;
  gap: 12px;
}

.chat-input__actions {
  display: flex;
  justify-content: flex-end;
}
</style>
