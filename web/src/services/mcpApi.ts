import { requestJson } from './api'
import type { McpCloudPivotBindingRequest, McpCloudPivotInstallation } from '../types/mcp'

export function getCloudPivotMcp(installationId?: string) {
  const query = installationId ? `?installationId=${encodeURIComponent(installationId)}` : ''
  return requestJson<McpCloudPivotInstallation>(`/settings/mcp/cloudpivot${query}`)
}

export function enableCloudPivotMcp(payload: McpCloudPivotBindingRequest) {
  return requestJson<McpCloudPivotInstallation>('/settings/mcp/cloudpivot/enable', { method: 'POST', body: JSON.stringify(payload) })
}

export function disableCloudPivotMcp(installationId: string) {
  return requestJson<McpCloudPivotInstallation>('/settings/mcp/cloudpivot/disable', {
    method: 'POST',
    body: JSON.stringify({ installationId, displayName: '' })
  })
}
