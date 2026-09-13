import type { ExecutionStep } from './agent'

/**
 * The host-facing, versioned Yunshu Skill contract.  The Web host only renders
 * this state and forwards user actions; it must not infer business rules.
 */
export type YunshuTaskStatus =
  | 'accepted'
  | 'needs_input'
  | 'draft_ready'
  | 'validation_error'
  | 'ready_to_submit'
  | 'confirmation_required'
  | 'submitting'
  | 'submitted'
  | 'failed'
  | 'unknown'
  | 'manual_review'
  | 'cancelled'
  | 'blocked'

export interface YunshuTaskRef {
  id: string
  kind: 'form' | 'query' | 'analysis' | 'workflow' | 'import' | string
  status: YunshuTaskStatus | string
  stateVersion: number
  retryable: boolean
  updatedAt?: string
}

export interface YunshuQuestionOption {
  label: string
  value: string | number | boolean
  disabled?: boolean
}

export interface YunshuInteractionQuestion {
  id: string
  slotRefs: string[]
  type: 'text' | 'textarea' | 'select' | 'number' | 'date' | 'boolean' | string
  prompt: string
  required: boolean
  options?: YunshuQuestionOption[]
  validation?: Record<string, unknown>
}

export interface YunshuInteraction {
  type: 'ask_user' | 'edit_draft' | 'request_confirmation' | 'none' | string
  questions: YunshuInteractionQuestion[]
  title?: string
  description?: string
  planHash?: string
  confirmationTicket?: string
  expiresAt?: string
  [key: string]: unknown
}

export interface YunshuDraftField {
  fieldCode: string
  label?: string
  type?: string
  value?: unknown
  required?: boolean
  source?: string
  confidence?: number
  validationStatus?: string
  error?: string
  editable?: boolean
}

export interface YunshuDraft {
  formRef: Record<string, unknown>
  metadataVersion: string
  draftVersion: number
  fields: YunshuDraftField[]
}

export interface YunshuValidationIssue {
  fieldCode?: string
  slotRef?: string
  message: string
  code?: string
  severity?: 'error' | 'warning' | string
}

export interface YunshuTaskExperienceEnvelope {
  protocolVersion: 'yunshu-skill/1.0' | string
  task: YunshuTaskRef
  interaction: YunshuInteraction
  draft: YunshuDraft
  validation: { issues: YunshuValidationIssue[] }
  next: {
    requiredInputSchema: Record<string, unknown>
    continuationToken?: string
    expiresAt?: string
  }
  completion: { state: 'incomplete' | 'ready' | 'submitted' | 'failed' | string; missing: string[] }
  output: Record<string, unknown>
  evidence: Record<string, unknown>
  hostAction: {
    type: 'ask_user' | 'edit_draft' | 'request_confirmation' | 'respond' | 'report_failure' | string
    allowAnotherCallThisTurn?: boolean
    payload?: Record<string, unknown>
  }
  auditRef: Record<string, unknown>
  error?: { code?: string; message?: string; retryable?: boolean }
  visibleTrace?: ExecutionStep[]
}

export type YunshuTaskOperation = 'start' | 'input' | 'action' | 'get' | 'events'

export interface YunshuTaskCommand {
  protocolVersion: 'yunshu-skill/1.0'
  operation: YunshuTaskOperation
  taskId?: string
  goal?: string
  input?: Record<string, unknown>
  action?: {
    type: 'confirm' | 'update_draft' | 'cancel'
    confirmationTicket?: string
    patch?: Record<string, unknown>
  }
  clientRequestId: string
  expectedStateVersion?: number
  continuationToken?: string
}
