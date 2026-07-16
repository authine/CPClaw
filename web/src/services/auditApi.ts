import { requestJson } from './api'

export interface AuditTool {
  id: string
  toolName: string
  status: string
  inputJsonMasked: string
  outputJsonMasked: string
}

export interface AuditDetail {
  id: string
  status: string
  intent?: string
  riskLevel?: string
  planJson?: string
  reflectionJson?: string
  tools?: AuditTool[]
}

export interface ConfirmationResult {
  id: string
  status: string
  agentRunId: string
  executed?: boolean
  message?: string
}

export function getAgentRun(id: string) {
  return requestJson<AuditDetail>(`/audit/agent-runs/${id}`)
}

export function confirmOperation(id: string) {
  return requestJson<ConfirmationResult>(`/audit/confirmations/${id}/confirm`, { method: 'POST' })
}
