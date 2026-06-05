export interface ModelConfigSummary {
  id: string
  name: string
  apiBaseUrl: string
  modelName: string
  supportsThinking: boolean
  defaultThinkingEnabled: boolean
  enabled: boolean
  hasApiKey: boolean
}

export interface UserCloudPivotSettings {
  baseUrl: string
  username: string
  hasPassword: boolean
}

export interface AdminMetadataSettings {
  targetBaseUrl: string
  username: string
  searchEngineType?: string
  searchEndpoint?: string
  hasPassword: boolean
}

export interface SettingsResponse {
  userCloudPivot: UserCloudPivotSettings
  adminMetadata: AdminMetadataSettings
  models: ModelConfigSummary[]
}

export interface SaveUserSettingsRequest {
  cloudPivotBaseUrl: string
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
