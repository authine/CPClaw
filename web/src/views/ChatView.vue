<template>
  <div :class="['chat-workbench', { 'chat-workbench--history-collapsed': historyCollapsed, 'chat-workbench--dark': darkMode }]">
    <aside class="conversation-panel">
      <div class="conversation-panel__header">
        <div class="brand">
          <span class="brand__mark">C</span>
          <span class="brand__name">CPClaw</span>
        </div>
        <el-tooltip content="收起侧栏" placement="right" :show-after="300">
          <el-button class="icon-button" circle text aria-label="收起侧栏" @click="historyCollapsed = true">
            <el-icon><Fold /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <el-button class="new-conversation" :loading="creatingConversation" @click="startConversation">
        <el-icon><Plus /></el-icon>
        <span>新建对话</span>
      </el-button>
      <el-scrollbar class="conversation-list">
        <div class="conversation-list__label">
          <span>最近对话</span>
          <el-tooltip content="刷新历史对话" placement="right" :show-after="300">
            <el-button class="conversation-refresh" text circle aria-label="刷新历史会话" :loading="loadingConversations" @click="loadConversations">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
        <div
          v-for="conversation in conversations"
          :key="conversation.id"
          :class="['conversation-item', { 'conversation-item--active': conversation.id === conversationId }]"
          @click="openConversation(conversation.id)"
        >
          <button class="conversation-item__main" type="button" :disabled="loadingConversation || submitting">
            <span class="conversation-item__title">{{ conversation.title || '新会话' }}</span>
            <span class="conversation-item__time">{{ formatTime(conversation.updatedAt) }}</span>
          </button>
          <el-button
            class="conversation-item__delete"
            circle
            text
            type="danger"
            :aria-label="`删除历史会话：${conversation.title || '未命名会话'}`"
            :disabled="loadingConversation || submitting"
            :loading="deletingConversationId === conversation.id"
            @click.stop="deleteHistoryConversation(conversation)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>
        </div>
        <el-empty v-if="!loadingConversations && conversations.length === 0" description="暂无历史会话" />
      </el-scrollbar>
      <div class="conversation-panel__footer">
        <div class="identity">
          <span class="identity__avatar">CP</span>
          <span class="identity__copy"><strong>当前用户</strong><small>个人云枢账号</small></span>
        </div>
        <el-tooltip content="系统设置" placement="right" :show-after="300">
          <el-button class="icon-button" circle text aria-label="系统设置" @click="openSettings">
            <el-icon><Setting /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </aside>

    <section class="chat-shell">
      <div class="chat-toolbar">
        <div class="chat-toolbar__leading">
          <el-tooltip v-if="historyCollapsed" content="展开侧栏" placement="right" :show-after="300">
            <el-button class="icon-button" circle text aria-label="展开侧栏" @click="historyCollapsed = false">
              <el-icon><Expand /></el-icon>
            </el-button>
          </el-tooltip>
          <div class="chat-toolbar__title">{{ currentConversationTitle }}</div>
        </div>
        <div class="chat-toolbar__actions">
          <el-tooltip :content="tokenUsageTooltip" placement="bottom" :show-after="300">
            <div class="token-usage" aria-label="当前会话 Token 消耗">
              <el-icon><Timer /></el-icon>
              <span>{{ tokenUsageText }}</span>
            </div>
          </el-tooltip>
          <el-tooltip :content="darkMode ? '切换浅色主题' : '切换深色主题'" placement="bottom" :show-after="300">
            <el-button class="theme-button" circle :aria-label="darkMode ? '切换浅色主题' : '切换深色主题'" @click="toggleTheme">
              <el-icon><Sunny v-if="darkMode" /><Moon v-else /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </div>

      <div ref="messagesContainer" v-loading="loadingConversation" class="chat-messages">
        <div v-if="messages.length === 0" class="empty-state">
          <div class="empty-state__mark"><el-icon><MagicStick /></el-icon></div>
          <div class="empty-state__title">告诉我你想了解什么</div>
          <div class="empty-state__description">直接询问云枢业务数据，CPClaw 会结合实体、数据项、关联关系和运行态数据理解问题并完成分析。</div>
          <div class="quick-prompts">
            <button v-for="prompt in quickPrompts" :key="prompt.title" class="quick-prompt" type="button" @click="fillPrompt(prompt.question)">
              <strong>{{ prompt.title }}</strong>
              <span>{{ prompt.question }}</span>
            </button>
          </div>
        </div>

        <div
          v-for="message in orderedMessages"
          :key="message.id"
          :class="[
            'message',
            `message--${message.role}`,
            {
              'message--selected': message.id === selectedTraceMessageId,
              'message--pending': message.id === pendingAssistantId
            }
          ]"
          @click="selectMessageTrace(message.id)"
        >
          <div v-if="message.role !== 'user'" class="message__avatar" aria-hidden="true"><el-icon><MagicStick /></el-icon></div>
          <div class="message__body">
            <div v-if="message.role === 'system'" class="message__role">{{ roleLabel(message.role) }}</div>
            <div v-if="message.id === pendingAssistantId" class="pending-thinking" role="region" aria-label="Agent 执行过程">
              <div class="pending-thinking__header">
                <div class="pending-thinking__title">
                  <span class="processing-dot"></span>
                  <span>正在运行</span>
                </div>
                <strong aria-hidden="true">{{ pendingElapsedSeconds }}s</strong>
              </div>
              <div v-if="pendingTraceSteps(message.id).length" class="pending-thinking__body" aria-live="polite">
                <AgentRunTimeline :steps="pendingTraceSteps(message.id)" compact />
              </div>
            </div>
            <details
              v-else-if="message.role === 'assistant' && messageTrace(message.id)"
              class="message-thinking-summary"
              :open="Boolean(expandedTraceByMessageId[message.id])"
              @toggle="syncTraceExpanded(message.id, $event)"
            >
              <summary>
                <div>
                  <el-icon class="message-thinking-summary__chevron"><ArrowRight /></el-icon>
                  <span>思考与执行过程</span>
                </div>
                <strong>{{ messageIsCancelled(message) ? '已中止' : (messageDurationText(message) || '已完成执行链路') }}</strong>
              </summary>
              <AgentRunTimeline :steps="messageTrace(message.id)?.steps || []" />
            </details>
            <div v-else-if="message.role === 'assistant' && messageDurationText(message)" class="message-duration">
              {{ messageDurationText(message) }}
            </div>
            <MarkdownMessage :content="message.content" />
            <InsightReport
              v-if="message.role === 'assistant' && messageInsightReport(message)"
              :report="messageInsightReport(message)!"
              @question="submitRelatedQuestion"
            />
            <div v-if="message.content" class="message-actions">
              <div class="message-actions__commands">
                <el-tooltip content="复制内容" placement="bottom" :show-after="300">
                  <el-button class="message-copy" text circle size="small" aria-label="复制消息内容" @click.stop="copyMessageContent(message)">
                    <el-icon><CopyDocument /></el-icon>
                  </el-button>
                </el-tooltip>
                <span v-if="messageActionInfo(message)" :class="['message-action-info', { 'message-action-info--cancelled': messageIsCancelled(message) }]">
                  {{ messageActionInfo(message) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <el-alert v-if="errorMessage" class="chat-error" type="error" show-icon :closable="false" :title="errorMessage" />

        <el-alert v-if="confirmationSummary" class="confirmation" type="warning" show-icon :closable="false">
          <template #title>这个操作可能会修改云枢数据，请确认后继续。</template>
          <div class="confirmation__summary">{{ confirmationSummary }}</div>
          <el-button class="confirmation__button" type="warning" :loading="confirming" @click="confirmLastOperation">确认继续</el-button>
        </el-alert>
      </div>

      <div class="chat-input">
        <div class="composer">
          <div v-if="uploadedAttachments.length" class="attachment-list">
            <el-tag
              v-for="attachment in uploadedAttachments"
              :key="attachment.id"
              closable
              type="info"
              @close="removeAttachment(attachment.id)"
            >
              {{ attachment.filename }}
            </el-tag>
          </div>
          <el-input
            v-model="input"
            class="composer__input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 7 }"
            resize="none"
            placeholder="询问云枢数据，或让 CPClaw 分析业务问题"
            :disabled="submitting || loadingConversation"
            @keydown.enter.exact.prevent="submit"
          />
          <div class="composer__footer">
            <div class="composer__options">
              <ModelSelector v-model="selectedModelId" :models="models" />
              <ThinkingToggle v-model="thinkingEnabled" :supported="Boolean(selectedModel?.supportsThinking)" />
            </div>
            <div class="composer__actions">
              <AttachmentUploader @uploaded="handleAttachmentUploaded" />
              <el-tooltip :content="executionInProgress ? '中止执行' : '发送消息'" placement="top" :show-after="300">
                <el-button
                  :class="['composer__send', { 'composer__send--stop': executionInProgress }]"
                  type="primary"
                  circle
                  :aria-label="executionInProgress ? (stopping ? '正在中止执行' : '中止执行') : '发送消息'"
                  :disabled="executionInProgress ? stopping : !input.trim() || submitting || loadingConversation"
                  @click="executionInProgress ? cancelCurrentExecution() : submit()"
                >
                  <span v-if="executionInProgress" class="composer__stop-icon" aria-hidden="true"></span>
                  <el-icon v-else><Promotion /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, CopyDocument, Delete, Expand, Fold, MagicStick, Moon, Plus, Promotion, Refresh, Setting, Sunny, Timer } from '@element-plus/icons-vue'
import MarkdownMessage from '../components/chat/MarkdownMessage.vue'
import InsightReport from '../components/chat/InsightReport.vue'
import AttachmentUploader from '../components/chat/AttachmentUploader.vue'
import AgentRunTimeline from '../components/chat/AgentRunTimeline.vue'
import ModelSelector from '../components/chat/ModelSelector.vue'
import ThinkingToggle from '../components/chat/ThinkingToggle.vue'
import { cancelMessageExecution, createConversation, deleteConversation, getConversation, listConversations, sendMessageStream } from '../services/conversationApi'
import { confirmOperation, getAgentRun } from '../services/auditApi'
import { listModelConfigs } from '../services/settingsApi'
import type { AgentResponse, AttachmentResponse, CandidateOption, ExecutionStep, InsightChart, InsightReport as InsightReportData } from '../types/agent'
import type { ConversationSummary, MessageItem, SendMessageRequest } from '../types/conversation'
import type { MessageStreamEvent } from '../services/conversationApi'
import type { ModelConfigSummary } from '../types/settings'
import type { AuditDetail, AuditTool } from '../services/auditApi'

type JsonRecord = Record<string, unknown>

const router = useRouter()
const selectedModelId = ref('')
const thinkingEnabled = ref(false)
const models = ref<ModelConfigSummary[]>([])
const conversations = ref<ConversationSummary[]>([])
const conversationId = ref('')
const messages = ref<MessageItem[]>([])
const uploadedAttachments = ref<AttachmentResponse[]>([])
const input = ref('')
const lastAgent = ref<AgentResponse>()
const traceByMessageId = ref<Record<string, AgentResponse>>({})
const expandedTraceByMessageId = ref<Record<string, boolean>>({})
const selectedTraceMessageId = ref('')
const pendingAssistantId = ref('')
const pendingElapsedSeconds = ref(0)
const loadingTraceMessageId = ref('')
const messagesContainer = ref<HTMLElement>()
const submitting = ref(false)
const stopping = ref(false)
const streamFinalReceived = ref(false)
const activeStreamController = shallowRef<AbortController>()
const activeExecutionId = ref('')
const confirming = ref(false)
const loadingConversations = ref(false)
const loadingConversation = ref(false)
const creatingConversation = ref(false)
const deletingConversationId = ref('')
const errorMessage = ref('')
const historyCollapsed = ref(false)
const darkMode = ref(window.localStorage.getItem('cpclaw-theme') === 'dark')

const quickPrompts = [
  { title: '查询业务数据', question: '系统现在有多少商机？分别处于什么阶段？' },
  { title: '分析经营情况', question: '分析 2025 年上半年我的商机经营情况。' },
  { title: '发现重点问题', question: '金额最高的商机有哪些？存在什么风险？' }
]

const selectedModel = computed(() => models.value.find((model) => model.id === selectedModelId.value))
const currentConversation = computed(() => conversations.value.find((conversation) => conversation.id === conversationId.value))
const currentConversationTitle = computed(() => currentConversation.value?.title || '新会话')
const confirmationSummary = computed(() => (lastAgent.value?.requiresConfirmation ? lastAgent.value.planSummary : ''))
const orderedMessages = computed(() => [...messages.value].sort(compareMessages))
const tokenUsageSummary = computed(() => messages.value.reduce(
  (summary, message) => {
    const usage = messageTokenUsage(message)
    return {
      tracked: summary.tracked || usage.tracked,
      promptTokens: summary.promptTokens + usage.promptTokens,
      completionTokens: summary.completionTokens + usage.completionTokens,
      totalTokens: summary.totalTokens + usage.totalTokens
    }
  },
  { tracked: false, promptTokens: 0, completionTokens: 0, totalTokens: 0 }
))
const tokenUsageText = computed(() => tokenUsageSummary.value.tracked
  ? `Token ${formatTokenCount(tokenUsageSummary.value.totalTokens)}`
  : 'Token --')
const tokenUsageTooltip = computed(() => tokenUsageSummary.value.tracked
  ? `输入 ${formatTokenCount(tokenUsageSummary.value.promptTokens)} · 输出 ${formatTokenCount(tokenUsageSummary.value.completionTokens)} · 合计 ${formatTokenCount(tokenUsageSummary.value.totalTokens)}`
  : '当前历史消息未记录 Token 使用量')
const executionInProgress = computed(() => submitting.value && !streamFinalReceived.value)
let pendingTimer: number | undefined
let messageResizeObserver: ResizeObserver | undefined
let autoFollowFrame: number | undefined
let lastPageScrollY = window.scrollY
let componentActive = true
let activeAbortReason: 'user' | 'unmount' | '' = ''
const autoFollowEnabled = ref(true)
const pendingAnswerModes = new Map<string, string>()

onMounted(async () => {
  collapseHistoryOnMobile()
  window.addEventListener('scroll', handleWindowScroll, { passive: true })
  await Promise.all([loadModels(), loadConversations()])
  if (conversations.value[0]) {
    await openConversation(conversations.value[0].id)
  }
  if (!componentActive) {
    return
  }
  await nextTick()
  observeMessageSizeChanges()
})

onBeforeUnmount(() => {
  componentActive = false
  if (activeStreamController.value && !streamFinalReceived.value) {
    activeAbortReason = 'unmount'
    activeStreamController.value.abort()
  }
  stopPendingTimer()
  window.removeEventListener('scroll', handleWindowScroll)
  messageResizeObserver?.disconnect()
  if (autoFollowFrame !== undefined) {
    window.cancelAnimationFrame(autoFollowFrame)
  }
})

watch(selectedModelId, (modelId) => {
  const model = models.value.find((item) => item.id === modelId)
  if (!model?.supportsThinking) {
    thinkingEnabled.value = false
    return
  }
  thinkingEnabled.value = model.defaultThinkingEnabled
})

watch(darkMode, (enabled) => {
  window.localStorage.setItem('cpclaw-theme', enabled ? 'dark' : 'light')
})

function toggleTheme() {
  darkMode.value = !darkMode.value
}

function openSettings() {
  void router.push('/settings')
}

function fillPrompt(question: string) {
  input.value = question
  void nextTick(() => document.querySelector<HTMLTextAreaElement>('.composer textarea')?.focus())
}

function collapseHistoryOnMobile() {
  if (window.innerWidth <= 900) {
    historyCollapsed.value = true
  }
}

function messageTokenUsage(message: MessageItem) {
  const metadata = parseMetadata(message.metadataJson)
  const usage = recordValue(metadata.usage)
  const tracked = Object.prototype.hasOwnProperty.call(usage, 'total_tokens') || Object.prototype.hasOwnProperty.call(usage, 'totalTokens')
  return {
    tracked,
    promptTokens: numericTokenValue(usage.prompt_tokens ?? usage.promptTokens),
    completionTokens: numericTokenValue(usage.completion_tokens ?? usage.completionTokens),
    totalTokens: numericTokenValue(usage.total_tokens ?? usage.totalTokens)
  }
}

function numericTokenValue(value: unknown) {
  const numeric = Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : 0
}

function formatTokenCount(value: number) {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}M`
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(1)}K`
  }
  return String(Math.round(value))
}

async function loadModels() {
  try {
    models.value = await listModelConfigs()
    selectedModelId.value = models.value[0]?.id ?? ''
    thinkingEnabled.value = models.value[0]?.defaultThinkingEnabled ?? false
  } catch (error) {
    errorMessage.value = messageFromError(error)
  }
}

async function loadConversations() {
  loadingConversations.value = true
  try {
    conversations.value = await listConversations()
  } catch (error) {
    errorMessage.value = messageFromError(error)
  } finally {
    loadingConversations.value = false
  }
}

async function startConversation() {
  if (creatingConversation.value || submitting.value) {
    return
  }

  creatingConversation.value = true
  errorMessage.value = ''
  try {
    const conversation = await createConversation({
      title: '新会话',
      modelConfigId: selectedModelId.value || undefined,
      thinkingEnabled: thinkingEnabled.value
    })
    conversationId.value = conversation.id
    messages.value = []
    traceByMessageId.value = {}
    expandedTraceByMessageId.value = {}
    selectedTraceMessageId.value = ''
    uploadedAttachments.value = []
    lastAgent.value = undefined
    conversations.value = [conversation, ...conversations.value.filter((item) => item.id !== conversation.id)]
    collapseHistoryOnMobile()
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    creatingConversation.value = false
  }
}

async function openConversation(id: string) {
  if (!id || loadingConversation.value || submitting.value) {
    return
  }

  loadingConversation.value = true
  errorMessage.value = ''
  try {
    const detail = await getConversation(id)
    conversationId.value = detail.conversation.id
    messages.value = detail.messages
    traceByMessageId.value = restoreMessageTraces(detail.messages)
    expandedTraceByMessageId.value = {}
    selectedTraceMessageId.value = ''
    uploadedAttachments.value = []
    lastAgent.value = undefined
    upsertConversation(detail.conversation)
    collapseHistoryOnMobile()
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    loadingConversation.value = false
  }
}

async function deleteHistoryConversation(conversation: ConversationSummary) {
  if (deletingConversationId.value || submitting.value) {
    return
  }
  const conversationIdToDelete = conversation.id?.trim()
  if (!conversationIdToDelete) {
    ElMessage.error('会话ID缺失，无法删除')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除历史会话“${conversation.title || '新会话'}”吗？删除后该会话消息不可恢复。`,
      '删除历史会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }

  deletingConversationId.value = conversationIdToDelete
  errorMessage.value = ''
  try {
    await deleteConversation(conversationIdToDelete)
    const nextConversations = conversations.value.filter((item) => item.id !== conversationIdToDelete)
    conversations.value = nextConversations
    if (conversationId.value === conversationIdToDelete) {
      clearCurrentConversation()
      if (nextConversations[0]) {
        await openConversation(nextConversations[0].id)
      }
    }
    ElMessage.success('历史会话已删除')
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    deletingConversationId.value = ''
  }
}

function clearCurrentConversation() {
  conversationId.value = ''
  messages.value = []
  traceByMessageId.value = {}
  expandedTraceByMessageId.value = {}
  selectedTraceMessageId.value = ''
  uploadedAttachments.value = []
  lastAgent.value = undefined
}

async function submit() {
  const userContent = input.value.trim()
  if (!userContent || submitting.value) {
    return
  }

  const controller = new AbortController()
  const executionId = crypto.randomUUID()
  let messageQueued = false
  let pendingMessageId = ''
  submitting.value = true
  stopping.value = false
  streamFinalReceived.value = false
  activeStreamController.value = controller
  activeExecutionId.value = executionId
  activeAbortReason = ''
  autoFollowEnabled.value = true
  lastPageScrollY = window.scrollY
  errorMessage.value = ''
  const draft = input.value
  input.value = ''

  try {
    if (!conversationId.value) {
      const conversation = await createConversation({
        title: '新会话',
        modelConfigId: selectedModelId.value || undefined,
        thinkingEnabled: thinkingEnabled.value
      })
      if (controller.signal.aborted) {
        throw new DOMException('Aborted', 'AbortError')
      }
      conversationId.value = conversation.id
      upsertConversation(conversation)
    }

    const localMessage: MessageItem = {
      id: crypto.randomUUID(),
      role: 'user',
      content: userContent,
      createdAt: new Date().toISOString()
    }
    messages.value.push(localMessage)
    const pendingMessage = createPendingAssistantMessage()
    pendingMessageId = pendingMessage.id
    messageQueued = true
    messages.value.push(pendingMessage)
    traceByMessageId.value = {
      ...traceByMessageId.value,
      [pendingMessage.id]: createPendingTrace(pendingMessage)
    }
    selectedTraceMessageId.value = pendingMessage.id
    startPendingTimer()
    await scrollMessagesToBottom(true)

    const payload: SendMessageRequest = {
      conversationId: conversationId.value,
      content: userContent,
      modelConfigId: selectedModelId.value || undefined,
      thinkingEnabled: thinkingEnabled.value,
      attachmentIds: uploadedAttachments.value.map((attachment) => attachment.id),
      executionId
    }
    await sendMessageStream(
      payload,
      (event) => handleMessageStreamEvent(event, pendingMessage.id),
      { signal: controller.signal }
    )
    pendingAssistantId.value = ''
    uploadedAttachments.value = []
    await scrollMessagesToBottom()
    await loadConversations()
  } catch (error) {
    if (controller.signal.aborted) {
      if (activeAbortReason === 'user' && messageQueued && pendingMessageId) {
        finalizeCancelledAssistantMessage(pendingMessageId)
        uploadedAttachments.value = []
      } else if (activeAbortReason === 'user' && !messageQueued) {
        input.value = draft
      }
      return
    }
    input.value = draft
    removePendingAssistantMessage()
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    stopPendingTimer()
    if (activeStreamController.value === controller) {
      activeStreamController.value = undefined
      activeExecutionId.value = ''
      activeAbortReason = ''
      stopping.value = false
      streamFinalReceived.value = false
      submitting.value = false
    }
  }
}

async function cancelCurrentExecution() {
  const controller = activeStreamController.value
  if (!controller || stopping.value || streamFinalReceived.value) {
    return
  }
  stopping.value = true
  activeAbortReason = 'user'
  const executionId = activeExecutionId.value
  if (!executionId) {
    controller.abort()
    return
  }
  try {
    const result = await cancelMessageExecution(executionId)
    if (result.cancelled) {
      controller.abort()
      return
    }
    activeAbortReason = ''
    stopping.value = false
  } catch {
    controller.abort()
  }
}

async function confirmLastOperation() {
  if (!lastAgent.value?.confirmationId || confirming.value) {
    return
  }
  confirming.value = true
  errorMessage.value = ''
  try {
    const result = await confirmOperation(lastAgent.value.confirmationId)
    lastAgent.value = {
      ...lastAgent.value,
      requiresConfirmation: false,
      confirmationId: undefined
    }
    if (result.executed) {
      ElMessage.success(result.message || '已确认，云枢操作已执行。')
    } else if (result.status === 'failed' || result.status === 'expired') {
      ElMessage.error(result.message || '确认后未能执行操作。')
    } else if (result.status === 'executed' || result.status === 'confirmed') {
      ElMessage.info(result.message || '该确认单已处理，本次不会重复执行。')
    } else {
      ElMessage.warning(result.message || '已确认，但当前操作暂不支持自动执行。')
    }
  } catch (error) {
    errorMessage.value = messageFromError(error)
    ElMessage.error(errorMessage.value)
  } finally {
    confirming.value = false
  }
}

function handleAttachmentUploaded(attachment: AttachmentResponse) {
  uploadedAttachments.value = [...uploadedAttachments.value.filter((item) => item.id !== attachment.id), attachment]
}

function removeAttachment(id: string) {
  uploadedAttachments.value = uploadedAttachments.value.filter((attachment) => attachment.id !== id)
}

function submitRelatedQuestion(question: string) {
  if (!question.trim() || submitting.value || loadingConversation.value) {
    return
  }
  input.value = question
  void submit()
}

async function selectMessageTrace(id: string) {
  const cachedTrace = traceByMessageId.value[id]
  if (cachedTrace) {
    selectedTraceMessageId.value = id
    return
  }

  const message = messages.value.find((item) => item.id === id)
  if (!message || message.role !== 'assistant') {
    return
  }

  const metadata = parseMetadata(message.metadataJson)
  const agentRunId = stringValue(metadata.agentRunId)
  if (!agentRunId) {
    const legacyTrace = createLegacyMetadataTrace(message, metadata)
    if (legacyTrace) {
      setTraceForMessage(message.id, legacyTrace)
      selectedTraceMessageId.value = message.id
      return
    }
    ElMessage.info('这条历史消息没有关联的执行流程，请发送新消息后查看。')
    return
  }

  selectedTraceMessageId.value = message.id
  loadingTraceMessageId.value = message.id
  setTraceForMessage(message.id, createLoadingTrace(message, agentRunId))

  try {
    const audit = await getAgentRun(agentRunId)
    setTraceForMessage(message.id, createTraceFromAudit(audit, message))
  } catch (error) {
    const legacyTrace = createLegacyMetadataTrace(message, metadata)
    if (legacyTrace) {
      setTraceForMessage(message.id, legacyTrace)
      ElMessage.warning('完整流程加载失败，已展示消息中的运行态摘要。')
    } else {
      selectedTraceMessageId.value = ''
      ElMessage.error(`流程加载失败：${messageFromError(error)}`)
    }
  } finally {
    if (loadingTraceMessageId.value === message.id) {
      loadingTraceMessageId.value = ''
    }
  }
}

function setTraceForMessage(messageId: string, trace: AgentResponse) {
  traceByMessageId.value = {
    ...traceByMessageId.value,
    [messageId]: trace
  }
}

function createLoadingTrace(message: MessageItem, agentRunId: string): AgentResponse {
  return {
    agentRunId,
    intent: 'query_data',
    riskLevel: 'low',
    requiresConfirmation: false,
    planSummary: '正在从审计记录恢复本轮后端处理流程。',
    matchReason: '这条回复已关联 Agent Run，系统正在读取审计记录。',
    candidates: [],
    steps: [
      { title: '理解问题', status: '正在恢复用户目标和上下文摘要。' },
      { title: '规划路径', status: '正在恢复意图识别、对象匹配和置信度。' },
      { title: '查询数据', status: '正在恢复云枢元数据检索和运行态查询记录。' },
      { title: '整理回答', status: '正在恢复本轮执行结果和校验摘要。' }
    ],
    assistantMessage: message
  }
}

function createTraceFromAudit(audit: AuditDetail, assistantMessage: MessageItem): AgentResponse {
  const plan = parseJsonObject(audit.planJson)
  const reflection = parseJsonObject(audit.reflectionJson)
  const observe = recordValue(plan.observe)
  const think = recordValue(plan.think)
  const planNode = recordValue(plan.plan)
  const runtimeTool = audit.tools?.find((tool) => tool.toolName === 'cloudpivot_runtime_query')
  const runtimeOutput = parseToolOutput(runtimeTool)
  const tools = audit.tools ?? []
  const metadataObject = stringValue(think.metadataObject)
  const metadataCode = stringValue(think.metadataCode)
  const reasoningSummary = stringValue(think.reasoningSummary)
  const candidates: CandidateOption[] = metadataObject
    ? [{ name: metadataObject, type: 'entity', reason: reasoningSummary || `schemaCode=${metadataCode}` }]
    : []

  return {
    agentRunId: audit.id,
    intent: stringValue(think.intent) || stringValue(audit.intent) || 'query_data',
    riskLevel: normalizeRisk(audit.riskLevel),
    requiresConfirmation: stringValue(reflection.status) === 'pending_confirmation',
    planSummary: stringValue(planNode.summary) || '已从审计记录恢复执行计划。',
    matchReason: reasoningSummary || '本轮执行依据来自 Agent Run 审计记录。',
    candidates,
    steps: createAuditSteps(observe, think, reflection, runtimeTool, runtimeOutput, tools),
    assistantMessage
  }
}

function createAuditSteps(
  observe: JsonRecord,
  think: JsonRecord,
  reflection: JsonRecord,
  _runtimeTool: AuditTool | undefined,
  _runtimeOutput: JsonRecord,
  tools: AuditTool[]
): ExecutionStep[] {
  const steps: ExecutionStep[] = [
    {
      id: 'audit-observe',
      title: '理解用户问题',
      status: `已结合最近 ${stringValue(observe.recentMessageCount) || '0'} 条消息确认本轮有效目标。`,
      kind: 'thought',
      state: 'completed',
      data: {
        query: shortText(stringValue(observe.normalizedUserGoal), 120),
        effectiveQuery: shortText(stringValue(observe.effectiveUserGoal), 160),
        referencesPreviousResult: Boolean(observe.referencesPreviousResult),
        inheritedRuntimeObject: Boolean(observe.inheritedRuntimeObject)
      }
    },
    {
      id: 'audit-plan',
      title: '规划执行路径',
      status: stringValue(think.reasoningSummary) || '已完成意图识别、元数据匹配和动作规划。',
      kind: 'thought',
      state: 'completed',
      data: {
        intent: stringValue(think.intent) || stringValue(think.detectedIntent),
        action: stringValue(think.action),
        entityName: stringValue(think.metadataObject),
        schemaCode: stringValue(think.metadataCode),
        confidence: stringValue(think.confidence),
        filters: think.modelPlanRuntimeFilters,
        metricFields: think.modelPlanMetricFieldCodes,
        apiOperation: stringValue(think.modelPlanApiOperation)
      }
    }
  ]

  tools.forEach((tool, index) => {
    const input = parseJsonObject(tool.inputJsonMasked)
    const output = parseJsonObject(tool.outputJsonMasked)
    steps.push({
      id: `audit-tool-${index + 1}-call`,
      title: `调用 ${toolLabel(tool.toolName)}`,
      status: '已按执行计划发起调用。',
      kind: 'execution',
      state: 'completed',
      data: input
    })
    steps.push({
      id: `audit-tool-${index + 1}-result`,
      title: `${toolLabel(tool.toolName)}返回结果`,
      status: toolResultStatus(tool, output),
      kind: 'execution',
      state: tool.status === 'failed' ? 'fallback' : 'completed',
      data: output
    })
  })

  steps.push({
    id: 'audit-reflect',
    title: '校验并生成回答',
    status: stringValue(reflection.summary) || '已完成数据来源、执行状态和回答可返回性校验。',
    kind: 'thought',
    state: Boolean(reflection.passed) ? 'completed' : 'needs_input',
    data: {
      status: stringValue(reflection.status),
      passed: Boolean(reflection.passed),
      needsUserInput: Boolean(reflection.needsUserInput),
      checks: reflection.checks
    }
  })
  return steps
}

function toolLabel(name: string) {
  const labels: Record<string, string> = {
    metadata_search: '元数据检索',
    cloudpivot_runtime_query: '云枢运行态接口',
    cloudpivot_insight_report: '智能问数报告链路'
  }
  return labels[name] || name
}

function toolResultStatus(tool: AuditTool, output: JsonRecord) {
  if (tool.status === 'failed') {
    return `调用失败：${stringValue(output.error) || '未记录失败原因'}`
  }
  const total = stringValue(output.total) || stringValue(output.primaryCount)
  const returned = stringValue(output.returnedRecords)
  if (total || returned) {
    return `调用完成${total ? `，总数 ${total}` : ''}${returned ? `，返回 ${returned} 条` : ''}。`
  }
  return '调用完成，结果已写入本轮执行记录。'
}

function createLegacyMetadataTrace(message: MessageItem, metadata: JsonRecord): AgentResponse | undefined {
  if (stringValue(metadata.source) !== 'runtime-query') {
    return undefined
  }
  const entityName = stringValue(metadata.entityName) || '运行态对象'
  const schemaCode = stringValue(metadata.schemaCode) || '未记录'
  return {
    agentRunId: stringValue(metadata.agentRunId) || message.id,
    intent: 'query_data',
    riskLevel: 'low',
    requiresConfirmation: false,
    planSummary: '已从历史消息元数据恢复运行态查询摘要。',
    matchReason: '这条历史消息没有完整 Agent Run 关联，只能展示消息 metadata 中保留的有限流程信息。',
    candidates: [{ name: entityName, type: 'entity', reason: `schemaCode=${schemaCode}` }],
    steps: [
      { title: '理解问题', status: '历史消息保留了运行态对象信息，但没有完整审计上下文。' },
      { title: '规划路径', status: `对象=${entityName}；schemaCode=${schemaCode}` },
      { title: '查询数据', status: `来源=${stringValue(metadata.sourceEndpoint) || '未记录'}；total=${stringValue(metadata.total) || '未记录'}；returned=${stringValue(metadata.returnedRecords) || '未记录'}` },
      { title: '整理回答', status: '仅恢复有限摘要；新回复会保存 agentRunId 并支持完整流程查看。' }
    ],
    assistantMessage: message
  }
}

function parseMetadata(metadataJson?: string): JsonRecord {
  return parseJsonObject(metadataJson)
}

function restoreMessageTraces(items: MessageItem[]) {
  return items.reduce<Record<string, AgentResponse>>((result, message) => {
    if (message.role !== 'assistant') {
      return result
    }
    const metadata = parseMetadata(message.metadataJson)
    const timeline = normalizeExecutionSteps(metadata.executionTimeline)
    if (!timeline.length) {
      return result
    }
    const restored: AgentResponse = {
      agentRunId: stringValue(metadata.agentRunId) || message.id,
      intent: stringValue(metadata.intent) || 'query_data',
      riskLevel: normalizeRisk(stringValue(metadata.riskLevel)),
      requiresConfirmation: Boolean(metadata.requiresConfirmation),
      planSummary: stringValue(metadata.planSummary) || '已完成本轮意图理解、数据执行和回答生成。',
      matchReason: stringValue(metadata.matchReason) || '执行过程已随本轮消息保存。',
      candidates: [],
      steps: timeline,
      thinkingElapsedMs: numericDuration(metadata.thinkingElapsedMs),
      answerElapsedMs: numericDuration(metadata.answerElapsedMs),
      insightReport: normalizeInsightReport(metadata.insightReport),
      assistantMessage: message
    }
    if (stringValue(metadata.status) === 'cancelled') {
      result[message.id] = {
        ...restored,
        intent: 'cancelled',
        planSummary: '用户已中止本次执行。',
        steps: coalesceTimeline(timeline).map((step, index) => ({
          ...normalizeVisibleStep(step),
          id: step.id || `${message.id}-cancelled-event-${index + 1}`
        }))
      }
    } else {
      result[message.id] = withCompletedTimeline(restored, timeline)
    }
    return result
  }, {})
}

function messageInsightReport(message: MessageItem): InsightReportData | undefined {
  const responseReport = traceByMessageId.value[message.id]?.insightReport
  return normalizeInsightReport(responseReport ?? parseMetadata(message.metadataJson).insightReport)
}

function normalizeInsightReport(value: unknown): InsightReportData | undefined {
  const report = recordValue(value)
  if (!stringValue(report.title) && !stringValue(report.subject)) {
    return undefined
  }
  return {
    title: stringValue(report.title),
    subject: stringValue(report.subject),
    periodLabel: stringValue(report.periodLabel),
    scopeLabel: stringValue(report.scopeLabel),
    confidence: stringValue(report.confidence),
    kpis: arrayValue(report.kpis).map(recordValue).map((kpi) => ({
      label: stringValue(kpi.label),
      value: stringValue(kpi.value),
      unit: stringValue(kpi.unit),
      tone: stringValue(kpi.tone),
      description: stringValue(kpi.description)
    })).filter((kpi) => kpi.label || kpi.value),
    charts: arrayValue(report.charts).map(normalizeInsightChart).filter((chart): chart is InsightChart => Boolean(chart)),
    sections: arrayValue(report.sections).map(recordValue).map((section) => ({
      title: stringValue(section.title),
      findings: stringArray(section.findings)
    })).filter((section) => section.title || section.findings.length),
    relatedQuestions: stringArray(report.relatedQuestions),
    dataSources: stringArray(report.dataSources),
    warnings: stringArray(report.warnings)
  }
}

function normalizeInsightChart(value: unknown, index: number): InsightChart | undefined {
  const chart = recordValue(value)
  const type = stringValue(chart.type)
  if (!['bar', 'donut', 'funnel', 'line'].includes(type)) {
    return undefined
  }
  return {
    id: stringValue(chart.id) || `insight-chart-${type}-${index}`,
    type: type as InsightChart['type'],
    title: stringValue(chart.title),
    unit: stringValue(chart.unit),
    semantic: stringValue(chart.semantic),
    description: stringValue(chart.description),
    labels: stringArray(chart.labels),
    series: arrayValue(chart.series).map(recordValue).map((series) => ({
      name: stringValue(series.name),
      values: arrayValue(series.values).map(numberValue)
    })).filter((series) => series.values.length > 0)
  }
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function stringArray(value: unknown): string[] {
  return arrayValue(value).map(stringValue).filter(Boolean)
}

function numberValue(value: unknown) {
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) ? number : 0
}

function parseToolOutput(tool?: AuditTool): JsonRecord {
  return parseJsonObject(tool?.outputJsonMasked)
}

function parseJsonObject(value?: string): JsonRecord {
  if (!value) {
    return {}
  }
  try {
    return recordValue(JSON.parse(value))
  } catch {
    return {}
  }
}

function recordValue(value: unknown): JsonRecord {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : {}
}

function stringValue(value: unknown) {
  if (value === undefined || value === null) {
    return ''
  }
  return typeof value === 'string' ? value : String(value)
}

function normalizeRisk(value?: string): AgentResponse['riskLevel'] {
  return value === 'none' || value === 'low' || value === 'medium' || value === 'high' ? value : 'low'
}

function shortText(value: string, maxLength: number) {
  return value.length <= maxLength ? value : `${value.slice(0, maxLength)}...`
}
function yesNo(value: boolean) {
  return value ? '是' : '否'
}
function createPendingAssistantMessage(): MessageItem {
  const message: MessageItem = {
    id: `pending-${crypto.randomUUID()}`,
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString()
  }
  pendingAssistantId.value = message.id
  pendingElapsedSeconds.value = 0
  return message
}

async function handleMessageStreamEvent(event: MessageStreamEvent, pendingMessageId: string) {
  if (event.type === 'progress' || event.type === 'thought' || event.type === 'execution') {
    updatePendingTraceStep(pendingMessageId, event.step)
    return
  }
  if (event.type === 'answer_start') {
    pendingAnswerModes.set(pendingMessageId, event.mode)
    clearPendingAssistantContent(pendingMessageId)
    updatePendingTraceStep(pendingMessageId, {
      id: `${pendingMessageId}-answer`,
      title: '正式回答',
      status: event.mode === 'model' ? '大模型正在生成正式回答' : '正在生成可解释的业务回答',
      kind: 'execution',
      state: 'running',
      data: { mode: event.mode }
    })
    return
  }
  if (event.type === 'answer_delta') {
    appendPendingAssistantContent(pendingMessageId, event.content)
    await nextTick()
    await scrollMessagesToBottom()
    if (pendingAnswerModes.get(pendingMessageId) !== 'model') {
      await waitForVisibleStreamFrame()
    }
    return
  }
  if (event.type === 'answer_reset') {
    pendingAnswerModes.set(pendingMessageId, 'fallback')
    clearPendingAssistantContent(pendingMessageId)
    updatePendingTraceStep(pendingMessageId, {
      id: `${pendingMessageId}-answer-reset-${Date.now()}`,
      title: '调整回答策略',
      status: event.reason || '模型输出未完成，已切换为规则总结',
      kind: 'execution',
      state: 'fallback',
      data: { reason: event.reason }
    })
    return
  }
  if (event.type === 'answer_end') {
    updatePendingTraceStep(pendingMessageId, {
      id: `${pendingMessageId}-answer`,
      title: '正式回答',
      status: event.mode === 'model' ? '大模型回答生成完成' : '业务回答生成完成',
      kind: 'execution',
      state: 'completed',
      data: { mode: event.mode }
    })
    return
  }
  if (event.type === 'final') {
    streamFinalReceived.value = true
    pendingAnswerModes.delete(pendingMessageId)
    const response = event.response
    const pendingSteps = traceByMessageId.value[pendingMessageId]?.steps ?? []
    const completedResponse = withCompletedTimeline(response, pendingSteps)
    lastAgent.value = completedResponse
    replacePendingAssistantMessage(pendingMessageId, completedResponse.assistantMessage, completedResponse)
    clearPendingTrace(pendingMessageId)
    traceByMessageId.value = {
      ...traceByMessageId.value,
      [completedResponse.assistantMessage.id]: completedResponse
    }
    selectedTraceMessageId.value = completedResponse.assistantMessage.id
    return
  }
  if (event.type === 'error') {
    throw new Error(event.message)
  }
}

function waitForVisibleStreamFrame() {
  return new Promise<void>((resolve) => window.setTimeout(resolve, 36))
}

function updatePendingTraceStep(messageId: string, step: ExecutionStep) {
  const trace = traceByMessageId.value[messageId]
  if (!trace) {
    return
  }
  const normalizedStep = normalizeVisibleStep(step)
  const nextSteps = [...trace.steps]
  const existingIndex = findTimelineUpdateIndex(nextSteps, normalizedStep)
  if (existingIndex >= 0) {
    nextSteps.splice(existingIndex, 1, mergeTimelineStep(nextSteps[existingIndex], normalizedStep))
  } else {
    nextSteps.push({
      ...normalizedStep,
      id: normalizedStep.id || `${messageId}-event-${nextSteps.length + 1}`
    })
  }
  traceByMessageId.value = {
    ...traceByMessageId.value,
    [messageId]: {
      ...trace,
      planSummary: normalizedStep.status,
      steps: nextSteps
    }
  }
}

function normalizeVisibleStep(step: ExecutionStep): ExecutionStep {
  return {
    id: step.id,
    title: step.title || '执行活动',
    status: sanitizeVisibleProcessText(step.status),
    phase: step.phase,
    state: step.state,
    kind: step.kind,
    data: step.data,
    elapsedMs: step.elapsedMs
  }
}

function findTimelineUpdateIndex(steps: ExecutionStep[], incoming: ExecutionStep) {
  if (incoming.id) {
    const byId = steps.findIndex((step) => step.id === incoming.id)
    if (byId >= 0) {
      return byId
    }
  }
  if (!['completed', 'fallback', 'needs_input', 'cancelled'].includes(incoming.state || '')) {
    return -1
  }
  for (let index = steps.length - 1; index >= 0; index -= 1) {
    const step = steps[index]
    if (step.title === incoming.title && step.kind === incoming.kind && step.state === 'running') {
      return index
    }
  }
  return -1
}

function mergeTimelineStep(current: ExecutionStep, incoming: ExecutionStep): ExecutionStep {
  return {
    ...current,
    ...incoming,
    id: incoming.id || current.id,
    data: { ...(current.data || {}), ...(incoming.data || {}) },
    elapsedMs: incoming.elapsedMs ?? current.elapsedMs
  }
}

function withCompletedTimeline(response: AgentResponse, pendingSteps: ExecutionStep[]): AgentResponse {
  const responseSteps = normalizeExecutionSteps(response.steps)
  const hasRichResponseSteps = responseSteps.some((step) => step.id || step.kind || Boolean(step.data && Object.keys(step.data).length))
  const sourceSteps = hasRichResponseSteps ? responseSteps : pendingSteps
  const steps = coalesceTimeline(sourceSteps).map((step, index) => ({
    ...normalizeVisibleStep(step),
    id: step.id || `${response.agentRunId}-event-${index + 1}`
  }))
  const completionStep: ExecutionStep = {
    id: `${response.agentRunId}-completed`,
    title: '任务完成',
    status: response.requiresConfirmation ? '执行计划已生成，等待用户确认后继续。' : '执行链路已结束，正式回答已返回。',
    kind: 'progress',
    state: response.requiresConfirmation ? 'needs_input' : 'completed',
    data: {
      thinkingElapsedMs: response.thinkingElapsedMs,
      answerElapsedMs: response.answerElapsedMs,
      mode: response.insightReport ? 'insight-report' : 'answer'
    }
  }
  const completionIndex = steps.findIndex((step) => step.id === completionStep.id || step.title === '任务完成')
  if (completionIndex >= 0) {
    steps.splice(completionIndex, 1, mergeTimelineStep(steps[completionIndex], completionStep))
  } else {
    steps.push(completionStep)
  }
  return { ...response, steps }
}

function normalizeExecutionSteps(value: unknown): ExecutionStep[] {
  return arrayValue(value)
    .map(recordValue)
    .map((step, index) => ({
      id: stringValue(step.id) || undefined,
      title: stringValue(step.title) || `执行活动 ${index + 1}`,
      status: stringValue(step.status),
      phase: stringValue(step.phase) || undefined,
      state: stringValue(step.state) || undefined,
      kind: normalizeStepKind(step.kind),
      data: recordValue(step.data),
      elapsedMs: numericDuration(step.elapsedMs) || undefined
    }))
}

function normalizeStepKind(value: unknown): ExecutionStep['kind'] {
  const kind = stringValue(value)
  if (kind === 'answer_start' || kind === 'answer_reset' || kind === 'answer_end') {
    return 'execution'
  }
  return kind === 'thought' || kind === 'execution' || kind === 'progress' ? kind : undefined
}

function coalesceTimeline(steps: ExecutionStep[]) {
  return steps.reduce<ExecutionStep[]>((result, rawStep) => {
    const step = normalizeVisibleStep(rawStep)
    const index = findTimelineUpdateIndex(result, step)
    if (index >= 0) {
      result.splice(index, 1, mergeTimelineStep(result[index], step))
    } else {
      result.push(step)
    }
    return result
  }, [])
}

function clearPendingAssistantContent(messageId: string) {
  const index = messages.value.findIndex((message) => message.id === messageId)
  if (index < 0) {
    return
  }
  messages.value.splice(index, 1, { ...messages.value[index], content: '' })
}

function sanitizeVisibleProcessText(text: string) {
  return text
}

function appendPendingAssistantContent(messageId: string, chunk: string) {
  if (!chunk) {
    return
  }
  const index = messages.value.findIndex((message) => message.id === messageId)
  if (index < 0) {
    return
  }
  const current = messages.value[index]
  messages.value.splice(index, 1, {
    ...current,
    content: `${current.content}${chunk}`
  })
}

function createPendingTrace(assistantMessage: MessageItem): AgentResponse {
  return {
    agentRunId: assistantMessage.id,
    intent: 'pending',
    riskLevel: 'low',
    requiresConfirmation: false,
    planSummary: '',
    matchReason: '',
    candidates: [],
    steps: [],
    assistantMessage
  }
}

function pendingTraceSteps(messageId: string) {
  return traceByMessageId.value[messageId]?.steps ?? []
}

function messageTrace(id: string) {
  return traceByMessageId.value[id]
}

function syncTraceExpanded(messageId: string, event: Event) {
  const details = event.currentTarget as HTMLDetailsElement
  if (expandedTraceByMessageId.value[messageId] === details.open) {
    return
  }
  expandedTraceByMessageId.value = {
    ...expandedTraceByMessageId.value,
    [messageId]: details.open
  }
}

function replacePendingAssistantMessage(pendingId: string, assistantMessage: MessageItem, response?: AgentResponse) {
  const nextMessage: MessageItem = {
    ...assistantMessage,
    thinkingElapsedMs: response?.thinkingElapsedMs,
    answerElapsedMs: response?.answerElapsedMs
  }
  const index = messages.value.findIndex((message) => message.id === pendingId)
  if (index >= 0) {
    messages.value.splice(index, 1, nextMessage)
    return
  }
  messages.value.push(nextMessage)
}

function messageDurationText(message: MessageItem) {
  const metadata = parseMetadata(message.metadataJson)
  const thinkingMs = numericDuration(message.thinkingElapsedMs ?? metadata.thinkingElapsedMs)
  const answerMs = numericDuration(message.answerElapsedMs ?? metadata.answerElapsedMs)
  if (thinkingMs <= 0 && answerMs <= 0) {
    return ''
  }
  return `思考耗时 ${formatDuration(thinkingMs)} · 回答耗时 ${formatDuration(answerMs)}`
}

function messageActionInfo(message: MessageItem) {
  if (message.role !== 'assistant') {
    return ''
  }
  if (messageIsCancelled(message)) {
    return '已中止'
  }
  const usage = messageTokenUsage(message)
  if (!usage.tracked) {
    return ''
  }
  return `输出 ${formatTokenCount(usage.completionTokens)} · 总计 ${formatTokenCount(usage.totalTokens)}`
}

function messageIsCancelled(message: MessageItem) {
  return stringValue(parseMetadata(message.metadataJson).status) === 'cancelled'
}

function numericDuration(value: unknown) {
  const numeric = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numeric) && numeric > 0 ? numeric : 0
}

function formatDuration(milliseconds: number) {
  if (milliseconds <= 0) {
    return '0ms'
  }
  if (milliseconds < 1000) {
    return `${Math.round(milliseconds)}ms`
  }
  return `${(milliseconds / 1000).toFixed(2)}s`
}

async function copyMessageContent(message: MessageItem) {
  const content = message.content?.trim()
  if (!content) {
    ElMessage.warning('没有可复制的内容')
    return
  }
  try {
    await writeClipboardText(content)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择内容复制')
  }
}

async function writeClipboardText(content: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(content)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = content
  textarea.setAttribute('readonly', 'true')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const copied = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!copied) {
    throw new Error('copy failed')
  }
}

function removePendingAssistantMessage() {
  if (!pendingAssistantId.value) {
    return
  }
  const id = pendingAssistantId.value
  pendingAnswerModes.delete(id)
  messages.value = messages.value.filter((message) => message.id !== id)
  clearPendingTrace(id)
  pendingAssistantId.value = ''
}

function finalizeCancelledAssistantMessage(messageId: string) {
  pendingAnswerModes.delete(messageId)
  stopPendingTimer()
  const messageIndex = messages.value.findIndex((message) => message.id === messageId)
  if (messageIndex < 0) {
    pendingAssistantId.value = ''
    return
  }
  const currentMessage = messages.value[messageIndex]
  const cancelledMessage: MessageItem = {
    ...currentMessage,
    content: '本次执行已由用户中止，未生成最终结论。',
    metadataJson: JSON.stringify({
      ...parseMetadata(currentMessage.metadataJson),
      source: 'runtime-agent',
      status: 'cancelled',
      cancelledAt: new Date().toISOString()
    })
  }
  messages.value.splice(messageIndex, 1, cancelledMessage)

  const trace = traceByMessageId.value[messageId] || createPendingTrace(cancelledMessage)
  const steps = trace.steps.map((step) => step.state === 'running'
    ? { ...step, state: 'cancelled', status: `${step.status}（已中止）` }
    : step)
  steps.push({
    id: `${messageId}-cancelled`,
    title: '执行已中止',
    status: '用户已中止本次执行，后续步骤不再继续。',
    phase: 'end',
    kind: 'progress',
    state: 'cancelled',
    data: {},
    elapsedMs: pendingElapsedSeconds.value * 1000
  })
  traceByMessageId.value = {
    ...traceByMessageId.value,
    [messageId]: {
      ...trace,
      intent: 'cancelled',
      planSummary: '用户已中止本次执行。',
      steps,
      assistantMessage: cancelledMessage
    }
  }
  pendingAssistantId.value = ''
  selectedTraceMessageId.value = messageId
  void scrollMessagesToBottom()
}

function clearPendingTrace(id: string) {
  const nextTrace = { ...traceByMessageId.value }
  delete nextTrace[id]
  traceByMessageId.value = nextTrace
}

function startPendingTimer() {
  stopPendingTimer()
  pendingElapsedSeconds.value = 0
  pendingTimer = window.setInterval(() => {
    pendingElapsedSeconds.value += 1
  }, 1000)
}

function stopPendingTimer() {
  if (pendingTimer !== undefined) {
    window.clearInterval(pendingTimer)
    pendingTimer = undefined
  }
}

function isNearPageBottom() {
  return document.documentElement.scrollHeight - (window.scrollY + window.innerHeight) <= 160
}

function handleWindowScroll() {
  const currentScrollY = window.scrollY
  if (currentScrollY < lastPageScrollY - 2) {
    autoFollowEnabled.value = false
  } else if (isNearPageBottom()) {
    autoFollowEnabled.value = true
  }
  lastPageScrollY = currentScrollY
}

function observeMessageSizeChanges() {
  if (!messagesContainer.value || typeof ResizeObserver === 'undefined') {
    return
  }
  messageResizeObserver?.disconnect()
  messageResizeObserver = new ResizeObserver(() => scheduleAutoFollow())
  messageResizeObserver.observe(messagesContainer.value)
}

function scheduleAutoFollow(force = false) {
  if (!componentActive || (!autoFollowEnabled.value && !force)) {
    return
  }
  if (autoFollowFrame !== undefined) {
    if (!force) {
      return
    }
    window.cancelAnimationFrame(autoFollowFrame)
    autoFollowFrame = undefined
  }
  autoFollowFrame = window.requestAnimationFrame(() => {
    autoFollowFrame = undefined
    if (!componentActive || (!autoFollowEnabled.value && !force)) {
      return
    }
    window.scrollTo({ top: document.documentElement.scrollHeight, behavior: 'auto' })
    lastPageScrollY = window.scrollY
  })
}

async function scrollMessagesToBottom(force = false) {
  await nextTick()
  scheduleAutoFollow(force)
}

function upsertConversation(conversation: ConversationSummary) {
  const existingIndex = conversations.value.findIndex((item) => item.id === conversation.id)
  if (existingIndex < 0) {
    conversations.value = [conversation, ...conversations.value]
    return
  }
  const next = [...conversations.value]
  next[existingIndex] = conversation
  conversations.value = next
}

function roleLabel(role: MessageItem['role']) {
  return role === 'user' ? '你' : role === 'assistant' ? 'CPClaw' : '系统'
}

function compareMessages(left: MessageItem, right: MessageItem) {
  const timeDiff = messageTime(left.createdAt) - messageTime(right.createdAt)
  if (timeDiff !== 0) {
    return timeDiff
  }
  const roleDiff = messageRoleOrder(left.role) - messageRoleOrder(right.role)
  return roleDiff !== 0 ? roleDiff : left.id.localeCompare(right.id)
}

function messageTime(value?: string) {
  if (!value) {
    return 0
  }
  const time = new Date(value).getTime()
  return Number.isNaN(time) ? 0 : time
}

function messageRoleOrder(role: MessageItem['role']) {
  if (role === 'user') return 0
  if (role === 'assistant') return 1
  return 2
}

function formatTime(value?: string) {
  if (!value) {
    return '刚刚'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

function shortId(id: string) {
  return id.length > 8 ? id.slice(0, 8) : id
}

function messageFromError(error: unknown) {
  return error instanceof Error && error.message ? error.message : '请求失败，请稍后重试'
}
</script>

<style scoped>
.chat-workbench {
  display: grid;
  grid-template-columns: 236px minmax(560px, 1fr) minmax(320px, 360px);
  align-items: stretch;
  gap: 12px;
  min-height: calc(100vh - 102px);
  padding: 10px 12px 0;
  transition: grid-template-columns 0.18s ease;
}

.chat-workbench--history-collapsed {
  grid-template-columns: 48px minmax(640px, 1fr) minmax(320px, 360px);
}

.chat-workbench--trace-collapsed {
  grid-template-columns: 236px minmax(720px, 1fr) 48px;
}

.chat-workbench--history-collapsed.chat-workbench--trace-collapsed {
  grid-template-columns: 48px minmax(760px, 1fr) 48px;
}

.chat-workbench--history-collapsed .conversation-panel,
.chat-workbench--trace-collapsed .trace-panel {
  border-radius: 8px;
}

.conversation-panel,
.trace-panel {
  position: sticky;
  top: 92px;
  align-self: start;
  height: calc(100vh - 102px);
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.04);
}

.conversation-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  overflow: hidden;
}

.conversation-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 16px 10px;
  font-weight: 600;
}

.chat-workbench--history-collapsed .conversation-panel__header {
  justify-content: center;
  padding: 12px 8px;
}

.chat-workbench--history-collapsed .conversation-panel__header > div:first-child,
.chat-workbench--trace-collapsed .trace-panel__header > div:first-child {
  display: none;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-workbench--history-collapsed .panel-actions,
.chat-workbench--trace-collapsed .trace-panel__header .panel-actions {
  justify-content: center;
}

.chat-workbench--history-collapsed .conversation-panel__header .panel-actions .el-button:first-child {
  display: none;
}

.conversation-panel__header > div:first-child,
.trace-panel__header > div:first-child {
  display: grid;
  gap: 3px;
}

.conversation-panel__header .panel-actions,
.trace-panel__header .panel-actions {
  display: flex;
  flex-shrink: 0;
}

.conversation-panel__header small,
.trace-panel__header small {
  color: #667085;
  font-size: 12px;
  font-weight: 500;
}

.new-conversation {
  margin: 0 16px 12px;
}

.conversation-list {
  padding: 0 8px 12px;
}

.conversation-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-height: 58px;
  padding: 4px 6px 4px 10px;
  border-radius: 8px;
  background: transparent;
  color: #344054;
}

.conversation-item:hover {
  background: #f2f4f7;
}

.conversation-item--active {
  background: #eef4ff;
  color: #175cd3;
}

.conversation-item__main {
  display: grid;
  min-width: 0;
  min-height: 50px;
  padding: 6px 4px 6px 2px;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.conversation-item__main:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.conversation-item__delete {
  align-self: center;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease;
}

.conversation-item:hover .conversation-item__delete,
.conversation-item:focus-within .conversation-item__delete,
.conversation-item__delete.is-loading {
  opacity: 1;
  pointer-events: auto;
}

.conversation-item__title {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item__time {
  margin-top: 4px;
  color: #667085;
  font-size: 12px;
}

.chat-shell {
  display: grid;
  grid-template-rows: auto auto auto;
  align-content: start;
  min-width: 0;
  overflow: visible;
  border: 1px solid #e4e7ec;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.04);
}

.chat-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px 16px;
  position: sticky;
  top: 0;
  z-index: 3;
  padding: 18px 18px 12px;
  border-bottom: 1px solid #f2f4f7;
  background: #fff;
}

.chat-toolbar__title,
.chat-toolbar__controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chat-toolbar__title {
  min-width: 0;
  color: #101828;
  font-weight: 700;
}

.chat-toolbar__subtitle {
  grid-column: 1;
  overflow: hidden;
  color: #667085;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-toolbar__title span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-toolbar__controls {
  grid-column: 2;
  grid-row: 1 / span 2;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 0;
  padding: 22px max(28px, calc((100% - 920px) / 2)) 16px;
  overflow: visible;
}

.chat-messages::before {
  content: none;
}

.empty-state {
  align-self: center;
  margin: auto;
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
  display: block;
  max-width: min(820px, 86%);
  min-width: 120px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 0;
  background: transparent;
  color: #101828;
  cursor: default;
  text-align: left;
  box-shadow: none;
}

.message:focus-visible {
  outline: 2px solid #84adff;
  outline-offset: 2px;
}

.message--user {
  align-self: flex-end;
  max-width: min(640px, 72%);
  padding: 10px 14px;
  border-radius: 18px;
  border-top-right-radius: 6px;
  background: #f2f4f7;
  box-shadow: none;
}

.message--assistant {
  align-self: flex-start;
  max-width: min(880px, 90%);
  padding-left: 2px;
  background: transparent;
  border-color: transparent;
}

.message--system {
  align-self: flex-start;
}

.message--user .message__role {
  text-align: right;
}

.message--assistant:hover {
  border-color: transparent;
}

.message--pending {
  background: transparent;
}

.message--pending .message__role {
  color: #175cd3;
}

.message--selected {
  border-color: transparent;
  box-shadow: none;
}

.message__role {
  margin-bottom: 8px;
  color: #667085;
  font-size: 12px;
  font-weight: 700;
}

.message--assistant .message__role {
  color: #101828;
}

.pending-thinking {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
  padding: 0 0 12px;
  border-bottom: 1px solid #eaecf0;
  background: #fff;
}

.pending-thinking__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #344054;
  font-size: 13px;
  font-weight: 600;
}

.pending-thinking__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.pending-thinking__header strong {
  color: #98a2b3;
  font-size: 12px;
  font-weight: 600;
}

.pending-thinking__status {
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.pending-thinking__body {
  display: grid;
  gap: 10px;
  padding-top: 4px;
}

.pending-thinking__steps {
  position: relative;
  display: grid;
  gap: 0;
  margin: 0;
  padding: 4px 0 0;
  list-style: none;
}

.pending-thinking__steps li {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding-bottom: 10px;
}

.pending-thinking__steps li::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 18px;
  bottom: 0;
  width: 1px;
  background: #eaecf0;
}

.pending-thinking__steps li:last-child::before {
  display: none;
}

.pending-thinking__step-dot {
  position: relative;
  z-index: 1;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 999px;
  border: 2px solid #d0d5dd;
  background: #fff;
}

.pending-thinking__step--active .pending-thinking__step-dot {
  border-color: #1570ef;
  background: #1570ef;
  box-shadow: 0 0 0 4px rgba(21, 112, 239, 0.12);
}

.pending-thinking__step--completed .pending-thinking__step-dot {
  border-color: #12b76a;
  background: #12b76a;
}

.pending-thinking__step--fallback .pending-thinking__step-dot {
  border-color: #f79009;
  background: #f79009;
}

.pending-thinking__steps strong {
  display: block;
  color: #344054;
  font-size: 13px;
  line-height: 1.5;
}

.pending-thinking__steps p {
  margin: 2px 0 0;
  color: #475467;
  font-size: 12px;
  line-height: 1.6;
}

.pending-thinking__step-data {
  display: block;
  margin-top: 4px;
  color: #667085;
  font-size: 11px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.pending-thinking__row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 10px;
  color: #344054;
  font-size: 13px;
  line-height: 1.5;
}

.pending-thinking__row--compact {
  grid-template-columns: minmax(0, 1fr) auto;
  padding-top: 8px;
  border-top: 1px solid #d1e0ff;
}

.pending-thinking__row span {
  color: #667085;
}

.pending-thinking__row strong {
  color: #175cd3;
  font-weight: 700;
}

.message-duration {
  margin-top: 10px;
  color: #667085;
  font-size: 12px;
  line-height: 1.5;
}

.message-thinking-summary {
  margin: 0 0 14px;
  padding: 0 0 10px;
  border-bottom: 1px solid #f2f4f7;
  color: #475467;
  font-size: 12px;
}

.message-thinking-summary summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  cursor: pointer;
  list-style: none;
  padding-bottom: 0;
}

.message-thinking-summary[open] summary {
  padding-bottom: 12px;
}

.message-thinking-summary summary::-webkit-details-marker {
  display: none;
}

.message-thinking-summary summary > div {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.message-thinking-summary__chevron {
  color: #98a2b3;
  transition: transform 0.16s ease;
}

.message-thinking-summary[open] .message-thinking-summary__chevron {
  transform: rotate(90deg);
}

.message-thinking-summary summary span {
  color: #344054;
  font-weight: 650;
}

.message-thinking-summary summary strong {
  color: #98a2b3;
  font-size: 12px;
  font-weight: 600;
}

.message-thinking-summary ol {
  display: grid;
  gap: 8px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}

.message-thinking-summary li {
  display: grid;
  gap: 2px;
}

.message-thinking-summary li strong {
  color: #344054;
  font-size: 13px;
}

.message-thinking-summary li p {
  margin: 0;
  color: #667085;
  line-height: 1.6;
}

.message-actions {
  display: flex;
  justify-content: flex-start;
  min-height: 28px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.14s ease;
}

.message:hover .message-actions,
.message:focus-within .message-actions {
  opacity: 1;
}

.message--user .message-actions {
  justify-content: flex-end;
}

.message-copy {
  color: #667085;
}

.message-copy:hover {
  color: #175cd3;
  background: #eef4ff;
}

.processing-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #12b76a;
  animation: processing-pulse 1.2s ease-in-out infinite;
}

@keyframes processing-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.8);
  }

  50% {
    opacity: 1;
    transform: scale(1.12);
  }
}

.chat-error,
.confirmation {
  max-width: 920px;
}

.confirmation__summary {
  margin-top: 8px;
  line-height: 1.6;
}

.confirmation__button {
  margin-top: 12px;
}

.chat-input {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: block;
  padding: 12px max(24px, calc((100% - 920px) / 2)) 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0), #fff 18%, #fff 100%);
}

.composer {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #d9dce3;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(16, 24, 40, 0.10);
}

.composer:focus-within {
  border-color: #98a2b3;
  box-shadow: 0 12px 34px rgba(16, 24, 40, 0.14);
}

.composer__input :deep(.el-textarea__inner) {
  min-height: 64px !important;
  padding: 4px 2px;
  border: 0;
  box-shadow: none;
  color: #101828;
  font-size: 15px;
  line-height: 1.7;
}

.composer__input :deep(.el-textarea__inner::placeholder) {
  color: #98a2b3;
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 4px;
  border-top: 0;
}

.composer__tools {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-input__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.composer__secondary,
.composer__send {
  border-radius: 999px;
}

.composer__secondary {
  min-width: 88px;
}

.composer__send {
  width: 36px;
  height: 36px;
  min-width: 36px;
  border-color: #1570ef;
  background: #1570ef;
  font-weight: 700;
}

.trace-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  overflow: hidden;
  border-color: #edf0f3;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 24, 40, 0.04);
}

.chat-workbench:not(.chat-workbench--trace-collapsed) .trace-panel {
  min-width: 0;
}

.trace-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px 12px;
  border-bottom: 1px solid #edf0f3;
  color: #101828;
  font-weight: 700;
}

.chat-workbench--trace-collapsed .trace-panel__header {
  justify-content: center;
  padding: 12px 6px;
}

.trace-panel__header div {
  display: grid;
  gap: 3px;
}

.trace-content {
  display: grid;
  align-content: start;
  gap: 18px;
  padding: 14px;
  overflow: auto;
}

.trace-overview {
  display: grid;
  gap: 8px;
  padding: 2px 0 14px;
  border-bottom: 1px solid #edf0f3;
  background: #fff;
}

.trace-overview > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.trace-overview small {
  color: #98a2b3;
  font-size: 11px;
}

.trace-overview span {
  width: fit-content;
  padding: 2px 8px;
  border-radius: 999px;
  background: #ecfdf3;
  color: #067647;
  font-size: 12px;
  font-weight: 700;
}

.trace-overview p {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.65;
}

.trace-object {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px 0;
  border-top: 1px solid #edf0f3;
  border-bottom: 1px solid #edf0f3;
}

.trace-object div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.trace-object span {
  color: #98a2b3;
  font-size: 12px;
  font-weight: 600;
}

.trace-object strong {
  overflow: hidden;
  color: #344054;
  font-size: 13px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-summary {
  display: grid;
  gap: 6px;
}

.trace-summary p,
.trace-reason,
.trace-steps p,
.trace-empty p {
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
}

.trace-summary__label,
.trace-section__title {
  color: #475467;
  font-size: 12px;
  font-weight: 700;
}

.trace-section {
  display: grid;
  gap: 10px;
}

.trace-section--primary {
  padding: 12px;
  border: 1px solid #edf0f3;
  border-radius: 10px;
  background: #fff;
}

.trace-steps {
  position: relative;
  display: grid;
  gap: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}

.trace-steps li {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 10px;
  padding-bottom: 14px;
}

.trace-steps li::before {
  content: '';
  position: absolute;
  left: 5px;
  top: 18px;
  bottom: 0;
  width: 1px;
  background: #eaecf0;
}

.trace-steps li:last-child {
  padding-bottom: 0;
}

.trace-steps li:last-child::before {
  display: none;
}

.trace-steps__dot {
  position: relative;
  z-index: 1;
  width: 11px;
  height: 11px;
  margin-top: 5px;
  border-radius: 999px;
  border: 2px solid #d0d5dd;
  background: #fff;
}

.trace-steps__item--active .trace-steps__dot {
  border-color: #12b76a;
  background: #12b76a;
  box-shadow: 0 0 0 4px #ecfdf3;
}

.trace-steps strong,
.candidate-item strong,
.trace-summary strong {
  color: #101828;
  font-size: 14px;
}

.trace-steps p {
  margin-top: 3px;
  font-size: 12px;
}

.candidate-list {
  display: grid;
  gap: 8px;
}

.candidate-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f2f4f7;
  background: transparent;
}

.candidate-item:last-child {
  border-bottom: 0;
}

.candidate-item span {
  color: #667085;
  font-size: 12px;
}

.trace-empty {
  align-self: center;
  justify-self: center;
  max-width: 230px;
  padding: 24px;
  text-align: center;
}

.trace-empty__title {
  margin-bottom: 8px;
  color: #101828;
  font-weight: 700;
}

@media (max-width: 1180px) {
  .chat-workbench {
    grid-template-columns: 220px minmax(0, 1fr);
    padding: 10px 12px 0;
  }

  .trace-panel {
    grid-column: 1 / -1;
    min-height: 340px;
  }

  .chat-workbench--trace-collapsed .trace-panel {
    grid-column: auto;
  }
}

@media (max-width: 940px) {
  .chat-workbench {
    grid-template-columns: 1fr;
  }

  .conversation-panel,
  .trace-panel {
    position: static;
    height: auto;
    min-height: 280px;
  }

  .chat-shell {
    grid-row: 1;
  }

  .conversation-panel {
    grid-row: 2;
  }

  .trace-panel {
    grid-row: 3;
  }

  .chat-workbench--history-collapsed .conversation-panel,
  .chat-workbench--trace-collapsed .trace-panel {
    min-height: 48px;
  }

  .chat-toolbar {
    display: flex;
    align-items: flex-start;
    flex-direction: column;
  }

  .chat-toolbar__controls {
    justify-content: flex-start;
  }
}

/* ChatUI reference refresh: keep the global top navigation, remove the right trace rail,
   and make the conversation itself the primary workspace. */
.chat-workbench {
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 0;
  min-height: calc(100vh - 64px);
  padding: 0;
  background: #f6f7fa;
}

.chat-workbench--history-collapsed {
  grid-template-columns: 52px minmax(0, 1fr);
}

.conversation-panel {
  top: 64px;
  height: calc(100vh - 64px);
  border: 0;
  border-right: 1px solid rgba(16, 24, 40, 0.08);
  border-radius: 0;
  background: #edeff4;
  box-shadow: none;
}

.conversation-panel__header {
  min-height: 52px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(16, 24, 40, 0.06);
  color: #1a1c2e;
}

.conversation-panel__header small {
  color: #7c8598;
  font-size: 11px;
}

.panel-actions {
  gap: 6px;
}

.panel-actions :deep(.el-button) {
  width: 30px;
  height: 30px;
  border-color: rgba(16, 24, 40, 0.08);
  background: rgba(255, 255, 255, 0.62);
  color: #5a6070;
}

.new-conversation {
  width: calc(100% - 28px);
  margin: 14px;
  border-color: rgba(79, 110, 247, 0.28);
  border-radius: 10px;
  background: rgba(79, 110, 247, 0.12);
  color: #4f6ef7;
  font-weight: 600;
}

.conversation-list {
  padding: 0 10px 14px;
}

.conversation-item {
  min-height: 54px;
  margin-bottom: 2px;
  padding: 4px 6px 4px 10px;
  border-radius: 8px;
  color: #3d4454;
}

.conversation-item:hover {
  background: #e3e6ee;
}

.conversation-item--active {
  background: #dfe2ea;
  color: #1a1c2e;
}

.conversation-item__title {
  font-size: 13px;
  font-weight: 600;
}

.conversation-item__time {
  color: #9098a8;
  font-size: 11px;
}

.chat-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  align-content: stretch;
  min-height: calc(100vh - 64px);
  border: 0;
  border-radius: 0;
  background: #fafbfc;
  box-shadow: none;
}

.chat-toolbar {
  position: sticky;
  top: 64px;
  z-index: 5;
  min-height: 52px;
  padding: 10px 20px;
  border-bottom: 1px solid rgba(16, 24, 40, 0.06);
  background: rgba(250, 251, 252, 0.92);
  backdrop-filter: blur(12px);
}

.chat-toolbar__title {
  color: #1a1c2e;
  font-size: 14px;
  font-weight: 650;
}

.chat-toolbar__subtitle {
  color: #7c8598;
  font-size: 12px;
}

.chat-toolbar__controls :deep(.el-button),
.chat-toolbar__controls :deep(.el-select__wrapper) {
  border-radius: 999px;
}

.chat-messages {
  gap: 24px;
  min-height: 0;
  padding: 28px max(24px, calc((100% - 820px) / 2)) 168px;
  overflow: visible;
}

.empty-state {
  max-width: 560px;
  margin: 13vh auto;
  padding: 36px 24px;
}

.empty-state__title {
  color: #1a1c2e;
  font-size: 24px;
  font-weight: 700;
}

.empty-state__description {
  color: #737c90;
  font-size: 14px;
}

.message {
  display: flex;
  gap: 14px;
  width: 100%;
  max-width: 820px;
  min-width: 0;
  color: #1a1c2e;
}

.message--user {
  flex-direction: row-reverse;
  align-self: center;
  max-width: 820px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}

.message--assistant,
.message--system {
  align-self: center;
  max-width: 820px;
  padding: 0;
}

.message__avatar {
  display: grid;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  margin-top: 2px;
  place-items: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #dde4f0, #c8d6f0);
  color: #4f6ef7;
  font-size: 12px;
  font-weight: 700;
}

.message--user .message__avatar {
  background: linear-gradient(135deg, #7c3aed, #4f6ef7);
  color: #fff;
}

.message__body {
  min-width: 0;
  max-width: min(100%, 700px);
  padding: 12px 16px;
  border: 1px solid rgba(16, 24, 40, 0.06);
  border-radius: 4px 14px 14px;
  background: #f6f7fa;
  font-size: 13.5px;
  line-height: 1.7;
}

.message--user .message__body {
  margin-left: auto;
  border-color: rgba(79, 110, 247, 0.14);
  border-radius: 14px 4px 14px 14px;
  background: #e2e8ff;
}

.message__role {
  margin-bottom: 6px;
  color: #9098a8;
  font-size: 11px;
  font-weight: 650;
}

.message--user .message__role {
  text-align: left;
}

.message--assistant .message__role {
  color: #5a6070;
}

.message--selected .message__body {
  border-color: rgba(79, 110, 247, 0.28);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.08);
}

.pending-thinking,
.message-thinking-summary {
  margin: 0 0 12px;
  padding: 0;
  overflow: hidden;
  border: 1px solid rgba(16, 24, 40, 0.06);
  border-radius: 10px;
  background: #f0f1f5;
}

.pending-thinking__header,
.message-thinking-summary summary {
  min-height: 40px;
  padding: 10px 14px;
  border-bottom: 0;
  color: #5a6070;
  font-size: 12px;
  font-weight: 600;
}

.pending-thinking__body {
  padding: 2px 14px 12px;
}

.message-thinking-summary[open] summary {
  border-bottom: 1px solid rgba(16, 24, 40, 0.06);
  padding-bottom: 10px;
}

.message-thinking-summary > :deep(.agent-run) {
  padding: 12px 14px 0;
}

.message-thinking-summary summary span {
  color: #5a6070;
}

.message-thinking-summary summary strong {
  color: #9098a8;
  font-size: 11px;
}

.message-duration {
  color: #9098a8;
  font-size: 11px;
}

.message-actions {
  min-height: 24px;
  margin-top: 8px;
  padding-top: 2px;
}

.message-copy {
  color: #9098a8;
}

.chat-error,
.confirmation {
  align-self: center;
  width: min(820px, 100%);
}

.chat-input {
  position: fixed;
  left: 300px;
  right: 20px;
  bottom: 0;
  z-index: 6;
  display: flex;
  justify-content: center;
  padding: 10px 24px 20px;
  background: linear-gradient(180deg, rgba(250, 251, 252, 0), #fafbfc 22%, #fafbfc);
}

.chat-workbench--history-collapsed .chat-input {
  left: 72px;
}

.composer {
  width: min(820px, 100%);
  gap: 4px;
  padding: 10px 14px 8px;
  border-color: rgba(16, 24, 40, 0.10);
  border-radius: 18px;
  background: #f0f1f5;
  box-shadow: none;
}

.composer:focus-within {
  border-color: rgba(79, 110, 247, 0.40);
  box-shadow: 0 14px 32px rgba(26, 28, 46, 0.10);
}

.composer__input :deep(.el-textarea__inner) {
  min-height: 30px !important;
  padding: 4px 0;
  background: transparent;
  color: #1a1c2e;
  font-size: 14px;
  line-height: 1.55;
}

.composer__footer {
  align-items: center;
  padding-top: 4px;
}

.composer__tools {
  gap: 8px;
}

.chat-input__actions {
  gap: 6px;
}

.composer__secondary {
  min-width: 0;
  height: 32px;
  padding: 0 12px;
  border-color: rgba(16, 24, 40, 0.10);
  background: #e8eaf0;
  color: #5a6070;
}

.composer__send {
  width: 30px;
  height: 30px;
  min-width: 30px;
  border-color: #4f6ef7;
  background: #4f6ef7;
}

.processing-dot {
  background: #10b981;
}

@media (max-width: 1180px) {
  .chat-workbench,
  .chat-workbench--history-collapsed {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  .chat-input,
  .chat-workbench--history-collapsed .chat-input {
    left: 260px;
  }
}

@media (max-width: 820px) {
  .chat-workbench,
  .chat-workbench--history-collapsed {
    grid-template-columns: 1fr;
  }

  .conversation-panel {
    position: static;
    height: auto;
    min-height: 52px;
    border-right: 0;
    border-bottom: 1px solid rgba(16, 24, 40, 0.08);
  }

  .chat-shell {
    min-height: calc(100vh - 64px);
  }

  .chat-toolbar {
    top: 64px;
  }

  .chat-messages,
  .chat-input {
    padding-right: 14px;
    padding-left: 14px;
  }

  .chat-input,
  .chat-workbench--history-collapsed .chat-input {
    left: 0;
    right: 0;
  }

  .message {
    max-width: 100%;
  }

  .message__body {
    max-width: calc(100% - 46px);
  }
}

/* UI v2 confirmed layout. Keep these rules last until the legacy style block is split. */
.chat-workbench {
  --chat-sidebar-width: 272px;
  --chat-header-height: 56px;
  --chat-footer-height: 60px;
  --chat-page: #f7f8fa;
  --chat-sidebar: #eef1f5;
  --chat-main: #fbfcfd;
  --chat-surface: #fff;
  --chat-surface-soft: #f2f4f7;
  --chat-surface-hover: #e8ecf2;
  --chat-surface-active: #dfe5ef;
  --chat-composer: #f3f5f9;
  --chat-line: rgba(18, 28, 45, 0.08);
  --chat-line-strong: rgba(18, 28, 45, 0.14);
  --chat-text: #171c2b;
  --chat-text-secondary: #657084;
  --chat-text-tertiary: #98a2b3;
  --chat-brand: #4f6ef7;
  --chat-brand-strong: #3f5ce0;
  --chat-brand-soft: rgba(79, 110, 247, 0.1);
  --chat-brand-line: rgba(79, 110, 247, 0.34);
  display: grid;
  grid-template-columns: var(--chat-sidebar-width) minmax(0, 1fr);
  gap: 0;
  min-width: 0;
  min-height: 100vh;
  padding: 0;
  overflow: visible;
  background: var(--chat-main);
  color: var(--chat-text);
  transition: grid-template-columns 0.2s ease;
}

.chat-workbench--dark {
  --chat-page: #0c1017;
  --chat-sidebar: #111722;
  --chat-main: #0f141d;
  --chat-surface: #171e29;
  --chat-surface-soft: #1b2330;
  --chat-surface-hover: #202a38;
  --chat-surface-active: #253143;
  --chat-composer: #171e29;
  --chat-line: rgba(255, 255, 255, 0.07);
  --chat-line-strong: rgba(255, 255, 255, 0.13);
  --chat-text: #eef2f7;
  --chat-text-secondary: #a6afbe;
  --chat-text-tertiary: #727d8e;
  --chat-brand: #6f89ff;
  --chat-brand-strong: #8da1ff;
  --chat-brand-soft: rgba(111, 137, 255, 0.13);
  --chat-brand-line: rgba(111, 137, 255, 0.38);
}

.chat-workbench--history-collapsed {
  grid-template-columns: 0 minmax(0, 1fr);
}

.conversation-panel {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-rows: var(--chat-header-height) auto minmax(0, 1fr) var(--chat-footer-height);
  width: var(--chat-sidebar-width);
  min-width: 0;
  height: 100vh;
  overflow: hidden;
  border: 0;
  border-right: 1px solid var(--chat-line);
  border-radius: 0;
  background: var(--chat-sidebar);
  box-shadow: none;
  transition: opacity 0.16s ease, transform 0.2s ease;
}

.chat-workbench--history-collapsed .conversation-panel {
  visibility: hidden;
  opacity: 0;
  transform: translateX(-100%);
  pointer-events: none;
}

.conversation-panel__header {
  display: flex;
  min-height: var(--chat-header-height);
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px 0 18px;
  border-bottom: 1px solid var(--chat-line);
  color: var(--chat-text);
}

.conversation-panel__header > .brand:first-child {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.brand__mark {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  background: var(--chat-brand);
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  box-shadow: 0 7px 18px rgba(79, 110, 247, 0.22);
}

.brand__name {
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-button,
.theme-button,
.conversation-refresh {
  border-color: transparent;
  background: transparent;
  color: var(--chat-text-secondary);
}

.icon-button:hover,
.theme-button:hover,
.conversation-refresh:hover {
  border-color: var(--chat-line);
  background: var(--chat-surface);
  color: var(--chat-text);
}

.new-conversation {
  width: calc(100% - 28px);
  height: 40px;
  margin: 14px 14px 10px;
  border: 1px solid var(--chat-line-strong);
  border-radius: 8px;
  background: var(--chat-brand-soft);
  color: var(--chat-brand-strong);
  font-size: 13px;
  font-weight: 600;
}

.new-conversation:hover,
.new-conversation:focus {
  border-color: var(--chat-brand);
  background: var(--chat-brand-soft);
  color: var(--chat-brand-strong);
}

.conversation-list {
  min-height: 0;
  padding: 2px 10px 14px;
}

.conversation-list__label {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  color: var(--chat-text-tertiary);
  font-size: 11px;
  font-weight: 600;
}

.conversation-refresh {
  width: 26px;
  height: 26px;
  opacity: 0;
  transition: opacity 0.14s ease;
}

.conversation-list:hover .conversation-refresh,
.conversation-refresh:focus-visible,
.conversation-refresh.is-loading {
  opacity: 1;
}

.conversation-item {
  grid-template-columns: minmax(0, 1fr) 28px;
  min-height: 58px;
  margin-bottom: 2px;
  padding: 4px 5px 4px 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--chat-text);
}

.conversation-item:hover { background: var(--chat-surface-hover); }

.conversation-item--active {
  border-color: var(--chat-line);
  background: var(--chat-surface-active);
  color: var(--chat-text);
}

.conversation-item__main {
  min-height: 48px;
  padding: 5px 2px;
  color: inherit;
}

.conversation-item__title { font-size: 12.5px; font-weight: 600; }
.conversation-item__time { margin-top: 4px; color: var(--chat-text-tertiary); font-size: 10.5px; }

.conversation-panel__footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: var(--chat-footer-height);
  padding: 8px 14px;
  border-top: 1px solid var(--chat-line);
  background: var(--chat-sidebar);
}

.identity {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  min-width: 0;
}

.identity__avatar {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #7159e8, var(--chat-brand));
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}

.identity__copy { display: grid; gap: 2px; min-width: 0; }
.identity__copy strong,
.identity__copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.identity__copy strong { color: var(--chat-text); font-size: 12px; font-weight: 600; }
.identity__copy small { color: var(--chat-text-tertiary); font-size: 10px; }

.chat-shell {
  display: grid;
  grid-template-rows: var(--chat-header-height) minmax(calc(100vh - var(--chat-header-height)), auto);
  align-content: start;
  min-width: 0;
  min-height: 100vh;
  overflow: visible;
  border: 0;
  border-radius: 0;
  background: var(--chat-main);
  box-shadow: none;
}

.chat-toolbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  min-height: var(--chat-header-height);
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 clamp(18px, 4vw, 54px);
  border-bottom: 1px solid var(--chat-line);
  background: color-mix(in srgb, var(--chat-main) 92%, transparent);
  backdrop-filter: blur(14px);
}

.chat-toolbar__leading,
.chat-toolbar__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.chat-toolbar__leading { flex: 1 1 auto; }
.chat-toolbar__actions { flex: 0 0 auto; }

.chat-toolbar__title {
  overflow: hidden;
  color: var(--chat-text);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-usage {
  display: inline-flex;
  height: 32px;
  align-items: center;
  gap: 7px;
  padding: 0 11px;
  border: 1px solid var(--chat-line);
  border-radius: 16px;
  background: var(--chat-surface-soft);
  color: var(--chat-text-tertiary);
  font-size: 11px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.theme-button {
  width: 34px;
  height: 34px;
  border: 1px solid var(--chat-line);
  background: var(--chat-surface-soft);
}

.chat-messages {
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-width: 0;
  min-height: calc(100vh - var(--chat-header-height));
  padding: 30px max(22px, calc((100% - 800px) / 2)) 180px;
  overflow: visible;
  background: var(--chat-main);
}

.empty-state {
  display: grid;
  width: min(800px, 100%);
  max-width: none;
  justify-items: center;
  gap: 14px;
  margin: clamp(70px, 15vh, 150px) auto 0;
  padding: 0;
  color: var(--chat-text-secondary);
  text-align: center;
}

.empty-state__mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid var(--chat-line);
  border-radius: 8px;
  background: var(--chat-surface);
  color: var(--chat-brand);
}

.empty-state__title {
  margin: 0;
  color: var(--chat-text);
  font-size: clamp(25px, 3vw, 34px);
  font-weight: 650;
  line-height: 1.25;
}

.empty-state__description {
  max-width: 650px;
  color: var(--chat-text-secondary);
  font-size: 13px;
  line-height: 1.85;
}

.quick-prompts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  width: 100%;
  margin-top: 18px;
}

.quick-prompt {
  display: grid;
  min-height: 90px;
  gap: 7px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--chat-line);
  border-radius: 8px;
  background: var(--chat-surface);
  color: var(--chat-text);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, transform 0.16s ease;
}

.quick-prompt:hover {
  border-color: var(--chat-brand-line);
  background: var(--chat-surface-soft);
  transform: translateY(-1px);
}

.quick-prompt strong { font-size: 12.5px; font-weight: 600; }
.quick-prompt span { color: var(--chat-text-secondary); font-size: 11.5px; line-height: 1.65; }

.message {
  display: flex;
  width: min(800px, 100%);
  max-width: 800px;
  min-width: 0;
  align-self: center;
  gap: 11px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--chat-text);
}

.message--user { justify-content: flex-end; }
.message--assistant,
.message--system { justify-content: flex-start; }

.message__avatar {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  margin-top: 1px;
  place-items: center;
  border: 1px solid var(--chat-line);
  border-radius: 8px;
  background: var(--chat-surface);
  color: var(--chat-brand);
}

.message__body {
  display: grid;
  min-width: 0;
  max-width: calc(100% - 41px);
  gap: 12px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--chat-text);
  font-size: 13px;
  line-height: 1.78;
}

.message--assistant .message__body,
.message--system .message__body {
  width: calc(100% - 41px);
}

.message--user .message__body {
  position: relative;
  max-width: min(72%, 560px);
  padding: 10px 13px;
  border: 1px solid var(--chat-brand-line);
  border-radius: 8px 8px 2px 8px;
  background: var(--chat-brand-soft);
}

.message--user :deep(.markdown-message p) {
  margin: 0;
}

.message--user .message-actions {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 3px;
}

.message--selected .message__body { box-shadow: none; }
.message__role { margin: 0; color: var(--chat-text-tertiary); font-size: 11px; }

.pending-thinking,
.message-thinking-summary {
  width: 100%;
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--chat-line);
  border-radius: 8px;
  background: var(--chat-surface-soft);
  color: var(--chat-text-secondary);
}

.pending-thinking__header,
.message-thinking-summary summary {
  min-height: 38px;
  padding: 8px 11px;
  border: 0;
  color: var(--chat-text-secondary);
  font-size: 11.5px;
}

.pending-thinking__body,
.message-thinking-summary > :deep(.agent-run) {
  padding: 2px 12px 11px 32px;
  border-top: 1px solid var(--chat-line);
}

.message-thinking-summary[open] summary { padding-bottom: 8px; border-bottom: 0; }
.message-thinking-summary summary span { color: var(--chat-text-secondary); }
.message-thinking-summary summary strong { color: var(--chat-text-tertiary); font-size: 10.5px; }
.message-duration { margin: 0; color: var(--chat-text-tertiary); font-size: 10.5px; }

.message-actions {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  min-height: 27px;
  margin-top: -5px;
  padding: 0;
}

.message--assistant .message-actions,
.message--system .message-actions {
  opacity: 1;
}

.message-actions__commands {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.message-copy {
  color: var(--chat-text-tertiary);
}

.message-copy:hover {
  background: var(--chat-surface-soft);
  color: var(--chat-brand-strong);
}

.message-action-info {
  display: inline-flex;
  align-items: center;
  margin-left: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--chat-text-tertiary);
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 10.5px;
  line-height: 1.2;
  white-space: nowrap;
}

.message-action-info--cancelled {
  color: var(--chat-text-secondary);
  font-family: inherit;
}

.chat-error,
.confirmation { width: min(800px, 100%); align-self: center; }

.chat-input {
  position: fixed;
  right: 0;
  bottom: 0;
  left: var(--chat-sidebar-width);
  z-index: 12;
  display: flex;
  justify-content: center;
  padding: 10px 24px 20px;
  background: linear-gradient(180deg, transparent, var(--chat-main) 24%, var(--chat-main));
  transition: left 0.2s ease;
}

.chat-workbench--history-collapsed .chat-input { left: 0; }

.composer {
  display: grid;
  width: min(800px, 100%);
  min-width: 0;
  gap: 6px;
  padding: 11px 12px 10px;
  border: 1px solid var(--chat-brand-line);
  border-radius: 14px;
  background: var(--chat-composer);
  box-shadow: 0 18px 48px rgba(20, 31, 51, 0.1), 0 2px 8px rgba(20, 31, 51, 0.04);
}

.chat-workbench--dark .composer { box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28); }

.composer:focus-within {
  border-color: var(--chat-brand);
  box-shadow: 0 20px 52px rgba(20, 31, 51, 0.13), 0 0 0 3px var(--chat-brand-soft);
}

.composer__input :deep(.el-textarea__inner) {
  min-height: 64px !important;
  padding: 5px 7px 8px;
  background: transparent;
  color: var(--chat-text);
  font-size: 13.5px;
  line-height: 1.65;
}

.composer__input :deep(.el-textarea__inner::placeholder) { color: var(--chat-text-tertiary); }

.composer__footer {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0;
}

.composer__options,
.composer__actions {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
}

.composer__options {
  width: fit-content;
  flex: 0 1 auto;
  gap: 2px;
  padding: 3px;
  overflow-x: auto;
  border: 1px solid var(--chat-line);
  border-radius: 18px;
  background: color-mix(in srgb, var(--chat-surface-soft) 78%, transparent);
  scrollbar-width: none;
}
.composer__options::-webkit-scrollbar { display: none; }
.composer__actions {
  flex: 0 0 auto;
  gap: 4px;
  padding: 1px;
}

.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 3px 3px;
}

.attachment-list :deep(.el-tag) {
  max-width: 260px;
  border-color: var(--chat-line);
  background: var(--chat-surface);
  color: var(--chat-text-secondary);
}

.composer__send {
  width: 32px;
  height: 32px;
  min-width: 32px;
  border-color: var(--chat-brand);
  background: var(--chat-brand);
  box-shadow: 0 4px 10px rgba(79, 110, 247, 0.22);
}

.composer__send:hover { border-color: var(--chat-brand-strong); background: var(--chat-brand-strong); }

.composer__send--stop {
  border-color: var(--chat-brand);
  background: var(--chat-brand);
  box-shadow: 0 4px 10px rgba(79, 110, 247, 0.18);
}

.composer__stop-icon {
  display: block;
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: #fff;
}

.chat-workbench--dark :deep(.markdown-message),
.chat-workbench--dark :deep(.agent-run),
.chat-workbench--dark :deep(.insight-report) {
  color: var(--chat-text);
}

.chat-workbench--dark :deep(.markdown-message pre),
.chat-workbench--dark :deep(.markdown-message table),
.chat-workbench--dark :deep(.agent-run__details),
.chat-workbench--dark :deep(.insight-report__metric),
.chat-workbench--dark :deep(.insight-report__chart) {
  border-color: var(--chat-line);
  background: var(--chat-surface);
  color: var(--chat-text);
}

.chat-workbench--dark :deep(.el-loading-mask) { background: rgba(15, 20, 29, 0.72); }

@media (max-width: 900px) {
  .chat-workbench,
  .chat-workbench--history-collapsed {
    grid-template-columns: minmax(0, 1fr);
  }

  .conversation-panel {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(var(--chat-sidebar-width), 86vw);
    box-shadow: 16px 0 40px rgba(13, 21, 36, 0.18);
    transform: translateX(0);
  }

  .chat-workbench--history-collapsed .conversation-panel {
    visibility: hidden;
    opacity: 0;
    transform: translateX(-105%);
  }

  .chat-toolbar {
    min-height: var(--chat-header-height);
    align-items: center;
    flex-direction: row;
    padding: 0 14px;
  }

  .chat-toolbar__leading { min-width: 0; }
  .chat-toolbar__title { min-width: 0; }
  .token-usage { padding: 0 9px; }
  .chat-messages { padding: 24px 14px 174px; }
  .quick-prompts { grid-template-columns: 1fr; }
  .message { max-width: 100%; }
  .message--user .message__body { max-width: 88%; }
  .chat-input,
  .chat-workbench--history-collapsed .chat-input { right: 0; left: 0; padding: 8px 12px 12px; }
}

@media (max-width: 520px) {
  .chat-toolbar { gap: 8px; }
  .chat-toolbar__actions { gap: 5px; }
  .token-usage { height: 30px; font-size: 10.5px; }
  .theme-button { width: 30px; height: 30px; }
  .empty-state { margin-top: 56px; }
  .quick-prompt:nth-child(n + 3) { display: none; }
  .composer { padding: 10px; }
  .composer__footer { gap: 7px; }
}
</style>
