import type { MessageItem } from './conversation'

export interface CandidateOption {
  name: string
  type: string
  reason: string
}

export interface ExecutionStep {
  id?: string
  title: string
  status: string
  phase?: string
  state?: string
  kind?: 'thought' | 'execution' | 'progress'
  data?: Record<string, unknown>
  elapsedMs?: number
}

export interface AgentPlanPreview {
  id: string
  summary: string
  riskLevel: 'none' | 'low' | 'medium' | 'high'
}

export interface InsightKpi {
  label: string
  value: string
  unit: string
  tone: string
  description: string
}

export interface InsightChartSeries {
  name: string
  values: number[]
}

export interface InsightChart {
  id: string
  type: 'bar' | 'donut' | 'funnel' | 'line'
  title: string
  unit: string
  semantic?: 'ordered_stage' | 'verified_conversion' | 'time_series' | 'composition' | 'comparison' | string
  description?: string
  labels: string[]
  series: InsightChartSeries[]
}

export interface InsightSection {
  title: string
  findings: string[]
}

export interface InsightReport {
  skillId?: string
  skillName?: string
  templateVersion?: number
  title: string
  subject: string
  periodLabel: string
  scopeLabel: string
  confidence: string
  kpis: InsightKpi[]
  charts: InsightChart[]
  sections: InsightSection[]
  relatedQuestions: string[]
  dataSources: string[]
  warnings: string[]
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
  thinkingElapsedMs?: number
  answerElapsedMs?: number
  insightReport?: InsightReport
  assistantMessage: MessageItem
  /** Channel-neutral runtime contract; optional during legacy response replay. */
  taskExperience?: TaskExperienceEnvelope
}

export interface TaskExperienceEnvelope {
  experienceVersion: string
  task: { id: string; status: string; updatedAt?: string; retryable: boolean }
  summary: Record<string, unknown>
  visibleTrace: ExecutionStep[]
  output: Record<string, unknown>
  interaction: Record<string, unknown>
  hostAction: { type?: string; allowAnotherMcpCallThisTurn?: boolean; [key: string]: unknown }
  completion: Record<string, unknown>
  evidence: Record<string, unknown>
  continuation: Record<string, unknown>
}

export interface AttachmentResponse {
  id: string
  filename: string
  contentType?: string
  size: number
  status: string
}
