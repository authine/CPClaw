export interface ModelConfigSummary {
  id: string
  name: string
  apiBaseUrl: string
  modelName: string
  supportsThinking: boolean
  defaultThinkingEnabled: boolean
  enabled: boolean
  hasApiKey: boolean
  credentialStatus: 'available' | 'missing' | 'unreadable'
}

export interface UserCloudPivotSettings {
  environmentConfigured: boolean
  username: string
  hasPassword: boolean
  credentialStatus: 'available' | 'missing' | 'unreadable'
}

export interface AdminMetadataSettings {
  targetBaseUrl: string
  username: string
  searchEngineType?: string
  searchEndpoint?: string
  hasPassword: boolean
  credentialStatus: 'available' | 'missing' | 'unreadable'
}

export interface SettingsResponse {
  userCloudPivot: UserCloudPivotSettings
  adminMetadata: AdminMetadataSettings
  models: ModelConfigSummary[]
}

export interface MemoryEntry {
  id: string
  /** Settings API only exposes manageable long-term scopes. SESSION is runtime-only. */
  scope: 'USER' | 'SYSTEM'
  memoryType: string
  content: string
  priority: number
  expiresAt?: string
  updatedAt?: string
  ownerPrincipal?: string
}

export interface MemorySettingsResponse {
  principalId: string
  displayName: string
  superAdmin: boolean
  personal: MemoryEntry[]
  global: MemoryEntry[]
  globalVisible: boolean
}

export interface SaveMemoryEntryRequest {
  memoryType: string
  content: string
  priority: number
  ttlDays: number
}

export interface SaveUserSettingsRequest {
  cloudPivotUsername: string
  cloudPivotPassword: string
  modelName: string
  modelApiBaseUrl: string
  modelApiKey: string
  modelDisplayName: string
  supportsThinking: boolean
  defaultThinkingEnabled: boolean
}

export interface SaveAdminSettingsRequest {
  targetBaseUrl: string
  username: string
  password: string
  searchEngineType: string
  searchEndpoint: string
}

export interface ConnectionTestResponse {
  success: boolean
  message: string
}

export interface ModelConnectionTestResponse {
  success: boolean
  message: string
  latencyMs: number
}

export interface SaveModelConfigRequest {
  modelName: string
  modelApiBaseUrl: string
  modelApiKey: string
  modelDisplayName: string
  supportsThinking: boolean
  defaultThinkingEnabled: boolean
}
