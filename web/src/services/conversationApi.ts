import { requestJson } from './api'
import type { AgentResponse, ExecutionStep } from '../types/agent'
import type {
  ConversationDetail,
  ConversationSummary,
  CreateConversationRequest,
  SendMessageRequest,
  SendMessageResponse
} from '../types/conversation'

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

export function sendMessage(payload: SendMessageRequest) {
  return requestJson<SendMessageResponse>('/conversations/messages', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export interface MessageStreamHandlers {
  onStep: (step: ExecutionStep) => void
  onMetadata: (response: AgentResponse) => void
  onContent: (delta: string) => void
  onDone?: (messageId: string) => void
}

type StreamEvent = {
  type: 'step' | 'metadata' | 'content' | 'done' | 'error'
  data: unknown
}

export async function sendMessageStream(payload: SendMessageRequest, handlers: MessageStreamHandlers, signal?: AbortSignal) {
  let response: Response
  try {
    response = await fetch('/api/conversations/messages/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/x-ndjson'
      },
      body: JSON.stringify(payload),
      signal
    })
  } catch {
    throw new Error('后端服务不可用，请确认后端已启动后重试。')
  }

  if (!response.ok || !response.body) {
    throw new Error(await streamErrorMessage(response))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const line of lines) {
      dispatchStreamEvent(line, handlers)
    }
    if (done) {
      break
    }
  }

  if (buffer.trim()) {
    dispatchStreamEvent(buffer, handlers)
  }
}

function dispatchStreamEvent(line: string, handlers: MessageStreamHandlers) {
  if (!line.trim()) {
    return
  }
  const event = JSON.parse(line) as StreamEvent
  if (event.type === 'step') {
    handlers.onStep(event.data as ExecutionStep)
    return
  }
  if (event.type === 'metadata') {
    handlers.onMetadata(event.data as AgentResponse)
    return
  }
  if (event.type === 'content') {
    handlers.onContent((event.data as { delta: string }).delta)
    return
  }
  if (event.type === 'done') {
    handlers.onDone?.((event.data as { messageId: string }).messageId)
    return
  }
  if (event.type === 'error') {
    throw new Error((event.data as { message?: string }).message || '请求处理失败')
  }
}

async function streamErrorMessage(response: Response) {
  const text = await response.text()
  if (!text) {
    return response.status >= 500 ? '后端服务不可用，请确认后端已启动后重试。' : `请求失败：${response.status}`
  }
  try {
    const payload = JSON.parse(text) as { message?: string }
    return payload.message || text
  } catch {
    return text
  }
}
