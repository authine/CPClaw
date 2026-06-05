import { requestJson } from './api'

export function getAgentRun(id: string) {
  return requestJson(`/audit/agent-runs/${id}`)
}
