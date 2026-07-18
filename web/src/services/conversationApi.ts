import { requestJson } from './api'
import type { AgentResponse, ExecutionStep } from '../types/agent'
import type {
  ConversationDetail,
  ConversationSummary,
  CreateConversationRequest,
  SendMessageRequest,
  SendMessageResponse
} from '../types/conversation'

const API_BASE = '/api'

export type MessageStreamEvent =
  | { type: 'progress'; step: ExecutionStep }
  | { type: 'thought'; step: ExecutionStep }
  | { type: 'execution'; step: ExecutionStep }
  | { type: 'answer_start'; mode: string }
  | { type: 'answer_delta'; content: string }
  | { type: 'answer_reset'; reason: string }
  | { type: 'answer_end'; mode: string }
  | { type: 'final'; response: AgentResponse }
  | { type: 'error'; message: string }

export function listConversations() {
  return requestJson<ConversationSummary[]>('/conversations')
}

export function createConversation(payload: CreateConversationRequest) {
  return requestJson<ConversationSummary>('/conversations', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getConversation(id: string) {
  return requestJson<ConversationDetail>(`/conversations/${id}`)
}

export function deleteConversation(id: string) {
  const conversationId = id?.trim()
  if (!conversationId) {
    throw new Error('会话ID缺失，无法删除')
  }
  return requestJson<void>(`/conversations/${encodeURIComponent(conversationId)}`, { method: 'DELETE' })
}

export function sendMessage(payload: SendMessageRequest) {
  return requestJson<SendMessageResponse>('/conversations/messages', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export interface CancelExecutionResult {
  cancelled: boolean
  state: string
}

export function cancelMessageExecution(executionId: string) {
  return requestJson<CancelExecutionResult>(`/conversations/executions/${encodeURIComponent(executionId)}/cancel`, {
    method: 'POST'
  })
}

export async function sendMessageStream(
  payload: SendMessageRequest,
  onEvent: (event: MessageStreamEvent) => void | Promise<void>,
  options: { signal?: AbortSignal } = {}
) {
  let response: Response
  try {
    response = await fetch(`${API_BASE}/conversations/messages/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: options.signal
    })
  } catch (error) {
    if (options.signal?.aborted) {
      throw error
    }
    throw new Error('后端服务不可用，请确认后端已启动后重试。')
  }

  if (!response.ok || !response.body) {
    throw new Error(response.status >= 500 ? '后端服务不可用，请确认后端已启动后重试。' : `请求失败：${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let receivedFinal = false
  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n\r?\n/)
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      const event = parseSseEvent(part)
      if (event) {
        receivedFinal ||= event.type === 'final'
        await onEvent(event)
      }
    }
  }
  buffer += decoder.decode()
  if (buffer.trim()) {
    const event = parseSseEvent(buffer)
    if (event) {
      receivedFinal ||= event.type === 'final'
      await onEvent(event)
    }
  }
  if (!receivedFinal) {
    throw new Error('流式响应已结束，但未收到 final 事件，请重试。')
  }
}

function parseSseEvent(block: string): MessageStreamEvent | undefined {
  const lines = block.split(/\r?\n/)
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')
  if (!data) {
    return undefined
  }
  const payload = JSON.parse(data) as Record<string, unknown>
  if (eventName === 'progress') {
    return {
      type: 'progress',
      step: {
        id: optionalString(payload.id),
        title: String(payload.title || ''),
        status: String(payload.status || ''),
        state: String(payload.state || ''),
        kind: 'progress',
        data: isRecord(payload.data) ? payload.data : {},
        elapsedMs: optionalNumber(payload.elapsedMs)
      }
    }
  }
  if (eventName === 'thought') {
    return {
      type: 'thought',
      step: {
        id: optionalString(payload.id),
        title: String(payload.title || ''),
        status: String(payload.status || ''),
        phase: String(payload.phase || ''),
        state: String(payload.state || ''),
        kind: 'thought',
        data: isRecord(payload.data) ? payload.data : {},
        elapsedMs: optionalNumber(payload.elapsedMs)
      }
    }
  }
  if (eventName === 'execution') {
    return {
      type: 'execution',
      step: {
        id: optionalString(payload.id),
        title: String(payload.title || ''),
        status: String(payload.status || ''),
        phase: String(payload.phase || ''),
        state: String(payload.state || ''),
        kind: 'execution',
        data: isRecord(payload.data) ? payload.data : {},
        elapsedMs: optionalNumber(payload.elapsedMs)
      }
    }
  }
  if (eventName === 'answer_start') {
    return { type: 'answer_start', mode: String(payload.mode || '') }
  }
  if (eventName === 'answer_delta' || eventName === 'chunk') {
    return { type: 'answer_delta', content: String(payload.content || '') }
  }
  if (eventName === 'answer_reset') {
    return { type: 'answer_reset', reason: String(payload.reason || '') }
  }
  if (eventName === 'answer_end') {
    return { type: 'answer_end', mode: String(payload.mode || '') }
  }
  if (eventName === 'final') {
    return { type: 'final', response: payload as unknown as AgentResponse }
  }
  if (eventName === 'error') {
    return { type: 'error', message: String(payload.message || '流式对话执行失败') }
  }
  return undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function optionalString(value: unknown) {
  const result = value === undefined || value === null ? '' : String(value)
  return result || undefined
}

function optionalNumber(value: unknown) {
  const result = Number(value)
  return Number.isFinite(result) && result >= 0 ? result : undefined
}
