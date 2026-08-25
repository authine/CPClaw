export interface McpCapability {
  name: string
  status: string
  rule: string
}

export interface McpCloudPivotInstallation {
  installationId: string
  displayName: string
  status: 'PENDING' | 'ENABLED' | 'DISABLED' | string
  environmentConfigured: boolean
  environmentSource: string
  enabledAt?: string
  mcpClientConfig: Record<string, unknown>
  capabilities: McpCapability[]
}

export interface McpCloudPivotBindingRequest {
  installationId: string
  displayName: string
}
