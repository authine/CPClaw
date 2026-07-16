import type { AgentResponse } from './agent'

export interface ConversationSummary {
  id: string
  title: string
  updatedAt: string
}

export interface MessageItem {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt: string
  metadataJson?: string
  thinkingElapsedMs?: number
  answerElapsedMs?: number
}

export interface CreateConversationRequest {
  title: string
  modelConfigId?: string
  thinkingEnabled: boolean
}

export interface SendMessageRequest {
  conversationId: string
  content: string
  modelConfigId?: string
  thinkingEnabled: boolean
  attachmentIds: string[]
  executionId: string
}

export interface ConversationDetail {
  conversation: ConversationSummary
  messages: MessageItem[]
}

export type SendMessageResponse = AgentResponse
