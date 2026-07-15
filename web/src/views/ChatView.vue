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
        <ThinkingProcess
          v-if="message.role === 'assistant' && processByMessage[message.id]"
          :steps="processByMessage[message.id].steps"
          :streaming="processByMessage[message.id].streaming"
          :completed="processByMessage[message.id].completed"
        />
        <MarkdownMessage :content="message.content" />
        <span v-if="message.role === 'assistant' && processByMessage[message.id]?.streaming" class="stream-cursor" />
      </div>

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
import ThinkingProcess from '../components/chat/ThinkingProcess.vue'
import { createConversation, sendMessageStream } from '../services/conversationApi'
import { confirmOperation } from '../services/auditApi'
import { listModelConfigs } from '../services/settingsApi'
import type { AgentProcessState, AgentResponse, ExecutionStep } from '../types/agent'
import type { MessageItem } from '../types/conversation'
import type { ModelConfigSummary } from '../types/settings'

const selectedModelId = ref('')
const thinkingEnabled = ref(false)
const models = ref<ModelConfigSummary[]>([])
const conversationId = ref('')
const messages = ref<MessageItem[]>([])
const processByMessage = ref<Record<string, AgentProcessState>>({})
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
  processByMessage.value = {}
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
  lastAgent.value = undefined
  let assistantMessage: MessageItem | undefined
  let processState: AgentProcessState | undefined

  try {
    if (!conversationId.value) {
      await startConversation()
    }
    const localMessage: MessageItem = {
      id: crypto.randomUUID(),
      role: 'user',
      content: userContent,
      createdAt: new Date().toISOString()
    }
    assistantMessage = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      metadataJson: '{"source":"runtime-agent-stream"}'
    }
    processState = {
      steps: [],
      streaming: true,
      completed: false
    }
    messages.value.push(localMessage, assistantMessage)
    processByMessage.value[assistantMessage.id] = processState

    await sendMessageStream(
      {
        conversationId: conversationId.value,
        content: userContent,
        modelConfigId: selectedModelId.value,
        thinkingEnabled: thinkingEnabled.value,
        attachmentIds: []
      },
      {
        onStep(step) {
          if (processState) {
            upsertStep(processState.steps, step)
          }
        },
        onMetadata(response) {
          if (!assistantMessage || !processState) {
            return
          }
          lastAgent.value = response
          const temporaryId = assistantMessage.id
          assistantMessage.id = response.assistantMessage.id
          assistantMessage.createdAt = response.assistantMessage.createdAt
          assistantMessage.metadataJson = response.assistantMessage.metadataJson
          response.steps.forEach((step) => upsertStep(processState!.steps, step))
          delete processByMessage.value[temporaryId]
          processByMessage.value[assistantMessage.id] = processState
        },
        onContent(delta) {
          if (assistantMessage) {
            assistantMessage.content += delta
          }
        },
        onDone() {
          if (processState) {
            processState.streaming = false
            processState.completed = true
          }
        }
      }
    )
  } catch (error) {
    if (processState) {
      processState.streaming = false
      processState.completed = false
    }
    input.value = draft
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    if (processState?.streaming) {
      processState.streaming = false
      processState.completed = true
    }
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

function upsertStep(steps: ExecutionStep[], step: ExecutionStep) {
  const index = steps.findIndex((item) => item.id === step.id)
  if (index >= 0) {
    steps.splice(index, 1, step)
    return
  }
  steps.push(step)
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
  border-radius: 8px;
  background: #f2f4f7;
}

.message--user {
  justify-self: end;
  background: #e8f3ff;
}

.message--assistant {
  justify-self: start;
  width: min(920px, 100%);
  max-width: 920px;
  box-sizing: border-box;
  background: #f7f7fb;
}

.message__role {
  margin-bottom: 8px;
  color: #475467;
  font-size: 13px;
  font-weight: 600;
}

.chat-error,
.confirmation {
  max-width: 920px;
}

.stream-cursor {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 3px;
  vertical-align: -2px;
  background: #2970ff;
  animation: cursor-blink 0.8s steps(1) infinite;
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

@keyframes cursor-blink {
  50% {
    opacity: 0;
  }
}
</style>
