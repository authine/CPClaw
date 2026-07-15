import type { MessageItem } from './conversation'

export interface CandidateOption {
  name: string
  type: string
  reason: string
}

export interface ExecutionStep {
  id: string
  title: string
  status: 'running' | 'completed' | 'warning' | 'failed'
  process: string
  conclusion: string
}

export interface AgentProcessState {
  steps: ExecutionStep[]
  streaming: boolean
  completed: boolean
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
