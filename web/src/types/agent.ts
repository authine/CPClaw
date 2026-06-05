import type { MessageItem } from './conversation'

export interface CandidateOption {
  name: string
  type: string
  reason: string
}

export interface ExecutionStep {
  title: string
  status: string
}

export interface AgentPlanPreview {
  id: string
  summary: string
  riskLevel: 'none' | 'low' | 'medium' | 'high'
}

export interface AgentResponse {
  agentRunId: string
  intent: string
  riskLevel: 'none' | 'low' | 'medium' | 'high'
  requiresConfirmation: boolean
  planSummary: string
  matchReason: string
  candidates: CandidateOption[]
  steps: ExecutionStep[]
  confirmationId?: string
  assistantMessage: MessageItem
}

export interface AttachmentResponse {
  id: string
  filename: string
  contentType?: string
  size: number
  status: string
}
