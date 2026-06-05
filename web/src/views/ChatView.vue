<template>
  <PageHeader title="对话操作" description="通过自然语言查询和操作云枢应用。" />
  <el-card class="chat-shell" shadow="never">
    <div class="chat-toolbar">
      <el-button @click="startConversation">新建会话</el-button>
      <ModelSelector v-model="selectedModelId" :models="models" />
      <ThinkingToggle v-model="thinkingEnabled" :supported="selectedModel?.supportsThinking ?? false" />
    </div>
    <div class="chat-messages">
      <MarkdownMessage v-if="messages.length === 0" content="欢迎使用 CPClaw。请选择模型后输入你要操作的云枢业务目标。" />
      <MarkdownMessage v-for="message in messages" :key="message.id" :content="`${roleLabel(message.role)}：\n${message.content}`" />
      <CandidateSelector v-if="lastAgent" :candidates="lastAgent.candidates" />
      <PlanPreviewCard v-if="lastAgent" :summary="lastAgent.planSummary" />
      <RiskConfirmationCard v-if="lastAgent?.requiresConfirmation" :summary="lastAgent.planSummary" @confirm="confirmLastOperation" />
      <ExecutionTimeline v-if="lastAgent" :items="lastAgent.steps" />
      <el-alert v-if="lastAgent" type="info" show-icon>
        <template #title>
          匹配原因：{{ lastAgent.matchReason }}；审计 Agent Run ID：{{ lastAgent.agentRunId }}
        </template>
      </el-alert>
      <el-tag v-for="attachment in attachments" :key="attachment.id" type="info">{{ attachment.filename }}：{{ attachment.status }}</el-tag>
    </div>
    <div class="chat-input">
      <AttachmentUploader @uploaded="attachments.push($event)" />
      <el-input v-model="input" type="textarea" :rows="3" placeholder="请输入你要操作的云枢业务目标" />
      <el-button type="primary" :disabled="!input.trim()" @click="submit">发送</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '../components/common/PageHeader.vue'
import ModelSelector from '../components/chat/ModelSelector.vue'
import ThinkingToggle from '../components/chat/ThinkingToggle.vue'
import AttachmentUploader from '../components/chat/AttachmentUploader.vue'
import MarkdownMessage from '../components/chat/MarkdownMessage.vue'
import CandidateSelector from '../components/chat/CandidateSelector.vue'
import PlanPreviewCard from '../components/chat/PlanPreviewCard.vue'
import RiskConfirmationCard from '../components/chat/RiskConfirmationCard.vue'
import ExecutionTimeline from '../components/chat/ExecutionTimeline.vue'
import { createConversation, sendMessage } from '../services/conversationApi'
import { confirmOperation } from '../services/auditApi'
import { listModelConfigs } from '../services/settingsApi'
import type { AgentResponse, AttachmentResponse } from '../types/agent'
import type { MessageItem } from '../types/conversation'
import type { ModelConfigSummary } from '../types/settings'

const selectedModelId = ref('')
const thinkingEnabled = ref(false)
const models = ref<ModelConfigSummary[]>([])
const conversationId = ref('')
const messages = ref<MessageItem[]>([])
const input = ref('')
const attachments = ref<AttachmentResponse[]>([])
const lastAgent = ref<AgentResponse>()

const selectedModel = computed(() => models.value.find((model) => model.id === selectedModelId.value))

onMounted(async () => {
  models.value = await listModelConfigs()
  selectedModelId.value = models.value[0]?.id ?? ''
  thinkingEnabled.value = models.value[0]?.defaultThinkingEnabled ?? false
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
  ElMessage.success('新会话已创建')
}

async function submit() {
  if (!conversationId.value) {
    await startConversation()
  }
  const userContent = input.value
  messages.value.push({ id: crypto.randomUUID(), role: 'user', content: userContent, createdAt: new Date().toISOString() })
  input.value = ''
  const response = await sendMessage({
    conversationId: conversationId.value,
    content: userContent,
    modelConfigId: selectedModelId.value,
    thinkingEnabled: thinkingEnabled.value,
    attachmentIds: attachments.value.map((attachment) => attachment.id)
  })
  lastAgent.value = response
  messages.value.push(response.assistantMessage)
}

async function confirmLastOperation() {
  if (!lastAgent.value?.confirmationId) {
    return
  }
  await confirmOperation(lastAgent.value.confirmationId)
  ElMessage.success('操作已确认，MVP 阶段已记录确认审计')
}

function roleLabel(role: MessageItem['role']) {
  return role === 'user' ? '用户' : role === 'assistant' ? 'CPClaw' : '系统'
}
</script>

<style scoped>
.chat-shell {
  min-height: calc(100vh - 120px);
}

.chat-toolbar {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 20px;
}

.chat-messages {
  display: grid;
  gap: 16px;
  margin-bottom: 20px;
}

.chat-input {
  display: grid;
  gap: 12px;
}
</style>
