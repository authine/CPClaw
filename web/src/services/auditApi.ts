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

export interface LogAnalyticsSummary {
  invocations: number
  successes: number
  failures: number
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  totalTokens: number
  usageRecorded: number
  averageLatencyMs?: number
}

export interface LogAnalyticsItem {
  id: string
  createdAt?: string
  modelName: string
  intent?: string
  businessIntent?: string
  status: string
  inputSummary?: string
  outputSummary?: string
  promptTokens?: number
  completionTokens?: number
  cachedTokens?: number
  totalTokens?: number
  durationMs?: number
  toolCallCount: number
}

export interface LogAnalyticsResponse {
  summary: LogAnalyticsSummary
  items: LogAnalyticsItem[]
  page: number
  size: number
  total: number
}

export interface TokenUsageBucket {
  invocations: number
  usageRecorded: number
  promptTokens: number
  completionTokens: number
  cachedTokens: number
  totalTokens: number
}

export interface TokenUsagePeriod extends TokenUsageBucket {
  period: string
  label: string
}

export interface TokenUsageModel extends TokenUsageBucket {
  modelName: string
}

export interface TokenUsageDashboard {
  summary: TokenUsageBucket
  daily: TokenUsagePeriod[]
  weekly: TokenUsagePeriod[]
  monthly: TokenUsagePeriod[]
  models: TokenUsageModel[]
}

export function getAgentRun(id: string) {
  return requestJson<AuditDetail>(`/audit/agent-runs/${id}`)
}

export function confirmOperation(id: string) {
  return requestJson<ConfirmationResult>(`/audit/confirmations/${id}/confirm`, { method: 'POST' })
}

export function getLogAnalytics(params: { from?: string; to?: string; status?: string; intent?: string; modelConfigId?: string; page?: number; size?: number } = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  const suffix = query.toString()
  return requestJson<LogAnalyticsResponse>(`/audit/analytics${suffix ? `?${suffix}` : ''}`)
}

export function getTokenUsageDashboard(params: { from?: string; to?: string; status?: string; modelConfigId?: string } = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  const suffix = query.toString()
  return requestJson<TokenUsageDashboard>(`/audit/analytics/usage${suffix ? `?${suffix}` : ''}`)
}
