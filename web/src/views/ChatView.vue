<template>
  <div class="chat-page">
    <aside class="chat-page__history">
      <ConversationHistory
        :conversations="conversations"
        :active-id="conversationId"
        :loading="loadingConversations"
        @select="openConversation"
        @create="startNewConversation"
        @refresh="refreshConversations"
      />
    </aside>

    <section class="conversation" aria-label="当前会话">
      <header class="conversation__header">
        <el-tooltip content="历史会话" placement="bottom">
          <el-button class="conversation__history-button" circle text aria-label="历史会话" @click="historyOpen = true">
            <el-icon><Clock /></el-icon>
          </el-button>
        </el-tooltip>
        <div class="conversation__title">
          <strong>{{ activeConversation?.title || '新会话' }}</strong>
          <span>{{ conversationStatus }}</span>
        </div>
        <el-tooltip content="新建会话" placement="bottom">
          <el-button circle text aria-label="新建会话" @click="startNewConversation">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-tooltip>
      </header>

      <div ref="messageViewport" class="conversation__messages" @scroll="trackScrollPosition">
        <div v-if="loadingConversation" class="conversation__loading">
          <el-skeleton :rows="7" animated />
        </div>

        <div v-else-if="messages.length === 0" class="empty-conversation">
          <div class="empty-conversation__mark">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <h1>今天想处理什么？</h1>
          <p>输入一个业务目标，我会先执行并展示可核验的过程，再给出结果。</p>
        </div>

        <div v-else class="message-list">
          <article v-for="message in messages" :key="message.id" :class="['message', `message--${message.role}`]">
            <div class="message__meta">
              <strong>{{ roleLabel(message.role) }}</strong>
              <time>{{ formatMessageTime(message.createdAt) }}</time>
            </div>
            <div class="message__content">
              <ThinkingProcess
                v-if="message.role === 'assistant' && processByMessage[message.id]?.steps.length"
                :steps="processByMessage[message.id].steps"
                :streaming="processByMessage[message.id].streaming"
                :completed="processByMessage[message.id].completed"
              />
              <MarkdownMessage :content="message.content" />
              <span v-if="message.role === 'assistant' && processByMessage[message.id]?.streaming" class="stream-cursor" />
            </div>
          </article>
        </div>

        <el-alert v-if="errorMessage" class="conversation__alert" type="error" show-icon closable :title="errorMessage" @close="errorMessage = ''" />

        <el-alert v-if="confirmationSummary" class="conversation__alert" type="warning" show-icon :closable="false">
          <template #title>这个操作会修改云枢数据，请确认后继续。</template>
          <div class="confirmation__summary">{{ confirmationSummary }}</div>
          <el-button class="confirmation__button" type="warning" :loading="confirming" @click="confirmLastOperation">确认继续</el-button>
        </el-alert>
      </div>

      <footer class="composer">
        <div class="composer__surface">
          <el-input
            ref="composerInput"
            v-model="input"
            type="textarea"
            resize="none"
            :autosize="{ minRows: 1, maxRows: 7 }"
            placeholder="输入业务目标"
            :disabled="loadingConversation"
            @keydown="handleComposerKeydown"
            @compositionstart="composing = true"
            @compositionend="composing = false"
          />
          <div class="composer__actions">
            <span class="composer__count">{{ input.length }}</span>
            <el-tooltip v-if="submitting" content="停止接收" placement="top">
              <el-button circle aria-label="停止接收" @click="stopStreaming">
                <el-icon><Close /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip v-else content="发送" placement="top">
              <el-button circle type="primary" :disabled="!input.trim()" aria-label="发送" @click="submit">
                <el-icon><Promotion /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
        </div>
      </footer>
    </section>

    <el-drawer v-model="historyOpen" direction="ltr" size="min(88vw, 360px)" :with-header="false" append-to-body>
      <ConversationHistory
        :conversations="conversations"
        :active-id="conversationId"
        :loading="loadingConversations"
        @select="openConversationFromDrawer"
        @create="startNewConversation"
        @refresh="refreshConversations"
      />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Clock, Close, Plus, Promotion } from '@element-plus/icons-vue'
import ConversationHistory from '../components/chat/ConversationHistory.vue'
import MarkdownMessage from '../components/chat/MarkdownMessage.vue'
import ThinkingProcess from '../components/chat/ThinkingProcess.vue'
import {
  createConversation,
  getConversation,
  listConversations,
  sendMessageStream
} from '../services/conversationApi'
import { confirmOperation } from '../services/auditApi'
import { listModelConfigs } from '../services/settingsApi'
import type { AgentProcessState, AgentResponse, ExecutionStep } from '../types/agent'
import type { ConversationSummary, MessageItem } from '../types/conversation'
import type { ModelConfigSummary } from '../types/settings'

const route = useRoute()
const router = useRouter()
const selectedModelId = ref('')
const thinkingEnabled = ref(false)
const models = ref<ModelConfigSummary[]>([])
const conversations = ref<ConversationSummary[]>([])
const conversationId = ref('')
const messages = ref<MessageItem[]>([])
const processByMessage = ref<Record<string, AgentProcessState>>({})
const input = ref('')
const lastAgent = ref<AgentResponse>()
const submitting = ref(false)
const confirming = ref(false)
const loadingConversations = ref(false)
const loadingConversation = ref(false)
const historyOpen = ref(false)
const errorMessage = ref('')
const composing = ref(false)
const messageViewport = ref<HTMLElement>()
const composerInput = ref()
const shouldFollowStream = ref(true)
let streamController: AbortController | undefined
let scrollFrame = 0

const activeConversation = computed(() => conversations.value.find((item) => item.id === conversationId.value))
const confirmationSummary = computed(() => (lastAgent.value?.requiresConfirmation ? lastAgent.value.planSummary : ''))
const conversationStatus = computed(() => {
  if (submitting.value) {
    return '正在执行'
  }
  if (loadingConversation.value) {
    return '正在同步'
  }
  return conversationId.value ? '已同步' : '未开始'
})

onMounted(async () => {
  window.addEventListener('focus', syncActiveConversation)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  try {
    const [modelItems] = await Promise.all([listModelConfigs(), refreshConversations(false)])
    models.value = modelItems
    selectedModelId.value = modelItems[0]?.id ?? ''
    thinkingEnabled.value = modelItems[0]?.defaultThinkingEnabled ?? false
    const requestedId = typeof route.query.conversation === 'string' ? route.query.conversation : ''
    const initialId = requestedId || conversations.value[0]?.id || ''
    if (initialId) {
      await openConversation(initialId, false)
    }
  } catch (error) {
    errorMessage.value = messageFromError(error)
  }
})

onBeforeUnmount(() => {
  streamController?.abort()
  window.removeEventListener('focus', syncActiveConversation)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  cancelAnimationFrame(scrollFrame)
})

async function refreshConversations(showLoading = true) {
  if (showLoading) {
    loadingConversations.value = true
  }
  try {
    conversations.value = await listConversations()
  } finally {
    loadingConversations.value = false
  }
}

async function openConversation(id: string, updateRoute = true) {
  if (!id || submitting.value || loadingConversation.value) {
    return
  }
  loadingConversation.value = true
  errorMessage.value = ''
  try {
    const detail = await getConversation(id)
    conversationId.value = detail.conversation.id
    messages.value = detail.messages
    processByMessage.value = restoreProcessStates(detail.messages)
    lastAgent.value = undefined
    if (updateRoute) {
      await router.replace({ name: 'chat', query: { conversation: id } })
    }
    await nextTick()
    scrollToBottom(true)
  } catch (error) {
    errorMessage.value = messageFromError(error)
  } finally {
    loadingConversation.value = false
  }
}

async function openConversationFromDrawer(id: string) {
  historyOpen.value = false
  await openConversation(id)
}

async function startConversation() {
  const conversation = await createConversation({
    title: '新会话',
    modelConfigId: selectedModelId.value,
    thinkingEnabled: thinkingEnabled.value
  })
  conversationId.value = conversation.id
  await router.replace({ name: 'chat', query: { conversation: conversation.id } })
  return conversation
}

async function startNewConversation() {
  if (submitting.value) {
    return
  }
  historyOpen.value = false
  conversationId.value = ''
  messages.value = []
  processByMessage.value = {}
  lastAgent.value = undefined
  errorMessage.value = ''
  await router.replace({ name: 'chat' })
  await nextTick()
  composerInput.value?.focus?.()
}

async function submit() {
  const userContent = input.value.trim()
  if (!userContent || submitting.value) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  shouldFollowStream.value = true
  const draft = input.value
  input.value = ''
  lastAgent.value = undefined
  let assistantMessage: MessageItem | undefined
  let processState: AgentProcessState | undefined
  streamController = new AbortController()
  const controller = streamController

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
    processState = { steps: [], streaming: true, completed: false }
    messages.value.push(localMessage, assistantMessage)
    processByMessage.value[assistantMessage.id] = processState
    await nextTick()
    scrollToBottom(true)

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
            scheduleScroll()
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
            scheduleScroll()
          }
        },
        onDone() {
          if (processState) {
            processState.streaming = false
            processState.completed = true
          }
        }
      },
      controller.signal
    )
    await refreshConversations(false)
  } catch (error) {
    if (!controller.signal.aborted) {
      input.value = draft
      errorMessage.value = messageFromError(error)
      ElMessage.error(errorMessage.value)
    }
    if (processState) {
      processState.streaming = false
      processState.completed = false
    }
  } finally {
    if (processState?.streaming) {
      processState.streaming = false
      processState.completed = true
    }
    submitting.value = false
    streamController = undefined
    await nextTick()
    composerInput.value?.focus?.()
  }
}

function stopStreaming() {
  streamController?.abort()
}

async function confirmLastOperation() {
  if (!lastAgent.value?.confirmationId || confirming.value) {
    return
  }
  confirming.value = true
  errorMessage.value = ''
  try {
    await confirmOperation(lastAgent.value.confirmationId)
    lastAgent.value = { ...lastAgent.value, requiresConfirmation: false, confirmationId: undefined }
    ElMessage.success('已确认，系统已记录本次操作。')
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    confirming.value = false
  }
}

function restoreProcessStates(items: MessageItem[]) {
  const restored: Record<string, AgentProcessState> = {}
  items.filter((message) => message.role === 'assistant').forEach((message) => {
    const steps = executionStepsFromMetadata(message.metadataJson)
    if (steps.length) {
      restored[message.id] = { steps, streaming: false, completed: true }
    }
  })
  return restored
}

function executionStepsFromMetadata(metadataJson?: string): ExecutionStep[] {
  if (!metadataJson) {
    return []
  }
  try {
    const metadata = JSON.parse(metadataJson) as { executionSteps?: unknown }
    if (!Array.isArray(metadata.executionSteps)) {
      return []
    }
    return metadata.executionSteps.filter(isExecutionStep)
  } catch {
    return []
  }
}

function isExecutionStep(value: unknown): value is ExecutionStep {
  if (!value || typeof value !== 'object') {
    return false
  }
  const step = value as Partial<ExecutionStep>
  return typeof step.id === 'string'
    && typeof step.title === 'string'
    && typeof step.process === 'string'
    && typeof step.conclusion === 'string'
    && ['running', 'completed', 'warning', 'failed'].includes(step.status ?? '')
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey || composing.value) {
    return
  }
  event.preventDefault()
  submit()
}

function trackScrollPosition() {
  const viewport = messageViewport.value
  if (!viewport) {
    return
  }
  shouldFollowStream.value = viewport.scrollHeight - viewport.scrollTop - viewport.clientHeight < 140
}

function scheduleScroll() {
  if (!shouldFollowStream.value) {
    return
  }
  cancelAnimationFrame(scrollFrame)
  scrollFrame = requestAnimationFrame(() => scrollToBottom())
}

function scrollToBottom(force = false) {
  const viewport = messageViewport.value
  if (!viewport || (!force && !shouldFollowStream.value)) {
    return
  }
  viewport.scrollTop = viewport.scrollHeight
}

async function syncActiveConversation() {
  if (submitting.value || document.visibilityState === 'hidden') {
    return
  }
  await refreshConversations(false)
  if (conversationId.value) {
    await openConversation(conversationId.value, false)
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    syncActiveConversation()
  }
}

function roleLabel(role: MessageItem['role']) {
  return role === 'user' ? '你' : role === 'assistant' ? 'CPClaw' : '系统'
}

function formatMessageTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(date)
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
.chat-page {
  height: calc(100dvh - 48px);
  min-height: 560px;
  display: grid;
  grid-template-columns: 272px minmax(0, 1fr);
  overflow: hidden;
  border: 1px solid #e9eaeb;
  border-radius: 8px;
  background: #fff;
}

.chat-page__history {
  min-width: 0;
  padding: 18px 14px;
  border-right: 1px solid #e9eaeb;
  background: #fafafa;
}

.conversation {
  min-width: 0;
  display: grid;
  grid-template-rows: 58px minmax(0, 1fr) auto;
  background: #fff;
}

.conversation__header {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid #e9eaeb;
}

.conversation__history-button {
  display: none;
}

.conversation__title {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.conversation__title strong {
  overflow: hidden;
  color: #181d27;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation__title span {
  color: #717680;
  font-size: 11px;
}

.conversation__messages {
  min-height: 0;
  overflow: auto;
  scroll-behavior: smooth;
  scrollbar-width: thin;
}

.message-list,
.conversation__alert,
.conversation__loading {
  width: min(920px, calc(100% - 40px));
  margin-inline: auto;
}

.message-list {
  display: grid;
  gap: 24px;
  padding: 30px 0 36px;
}

.message {
  min-width: 0;
  display: grid;
  gap: 7px;
}

.message--user {
  width: min(680px, 88%);
  justify-self: end;
}

.message--assistant {
  width: 100%;
}

.message__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-inline: 2px;
  color: #717680;
  font-size: 11px;
}

.message__meta strong {
  color: #414651;
  font-size: 12px;
}

.message--user .message__meta {
  justify-content: flex-end;
}

.message__content {
  min-width: 0;
  padding: 16px 18px;
  border: 1px solid #e9eaeb;
  border-radius: 8px;
  background: #fff;
  color: #252b37;
}

.message--user .message__content {
  border-color: #b2ddff;
  background: #eff8ff;
}

.empty-conversation {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  padding: 32px;
  color: #535862;
  text-align: center;
}

.empty-conversation__mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  margin-bottom: 16px;
  border: 1px solid #d5d7da;
  border-radius: 8px;
  color: #175cd3;
  font-size: 22px;
}

.empty-conversation h1 {
  margin: 0;
  color: #181d27;
  font-size: 22px;
  letter-spacing: 0;
}

.empty-conversation p {
  max-width: 460px;
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
}

.conversation__alert {
  margin-bottom: 16px;
}

.conversation__loading {
  padding-top: 32px;
}

.composer {
  padding: 14px 20px 18px;
  border-top: 1px solid #f0f0f0;
  background: rgba(255, 255, 255, 0.96);
}

.composer__surface {
  width: min(920px, 100%);
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
  margin-inline: auto;
  padding: 10px 10px 10px 14px;
  border: 1px solid #a4a7ae;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(10, 13, 18, 0.06);
}

.composer__surface:focus-within {
  border-color: #1570ef;
  box-shadow: 0 0 0 3px rgba(21, 112, 239, 0.12);
}

.composer__surface :deep(.el-textarea__inner) {
  min-height: 34px !important;
  padding: 6px 0;
  border: 0;
  box-shadow: none;
  line-height: 1.6;
}

.composer__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.composer__count {
  min-width: 20px;
  color: #a4a7ae;
  font-size: 11px;
  text-align: right;
}

.stream-cursor {
  width: 2px;
  height: 1em;
  display: inline-block;
  margin-left: 3px;
  vertical-align: -2px;
  background: #1570ef;
  animation: cursor-blink 0.8s steps(1) infinite;
}

.confirmation__summary {
  margin-top: 8px;
  line-height: 1.6;
}

.confirmation__button {
  margin-top: 12px;
}

@keyframes cursor-blink {
  50% { opacity: 0; }
}

@media (max-width: 980px) {
  .chat-page {
    grid-template-columns: 224px minmax(0, 1fr);
  }
}

@media (max-width: 760px) {
  .chat-page {
    height: calc(100dvh - 58px - env(safe-area-inset-bottom));
    min-height: 0;
    grid-template-columns: minmax(0, 1fr);
    border: 0;
    border-radius: 0;
  }

  .chat-page__history {
    display: none;
  }

  .conversation__history-button {
    display: inline-flex;
  }

  .conversation__header {
    padding: 0 12px;
  }

  .message-list,
  .conversation__alert,
  .conversation__loading {
    width: calc(100% - 24px);
  }

  .message-list {
    gap: 20px;
    padding: 20px 0 24px;
  }

  .message--user {
    width: min(92%, 620px);
  }

  .message__content {
    padding: 14px;
  }

  .composer {
    padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  }

  .composer__surface {
    gap: 8px;
  }

  .empty-conversation h1 {
    font-size: 20px;
  }
}
</style>
