import { requestJson } from './api'
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
