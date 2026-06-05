import { requestJson } from './api'

export function getAgentRun(id: string) {
  return requestJson(`/audit/agent-runs/${id}`)
}

export function confirmOperation(id: string) {
  return requestJson(`/audit/confirmations/${id}/confirm`, { method: 'POST' })
}
