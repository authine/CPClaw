import { requestJson } from './api'
import type {
  ConnectionTestResponse,
  ModelConnectionTestResponse,
  ModelConfigSummary,
  SaveAdminSettingsRequest,
  SaveModelConfigRequest,
  SaveUserSettingsRequest,
  SettingsResponse,
  MemorySettingsResponse,
  MemoryEntry,
  SaveMemoryEntryRequest
} from '../types/settings'

export function getSettings() {
  return requestJson<SettingsResponse>('/settings')
}

export function listModelConfigs() {
  return requestJson<ModelConfigSummary[]>('/settings/models')
}

export function saveUserSettings(payload: SaveUserSettingsRequest) {
  return requestJson<SettingsResponse>('/settings/user', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function saveAdminSettings(payload: SaveAdminSettingsRequest) {
  return requestJson<SettingsResponse>('/settings/admin', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function saveModelConfig(payload: SaveModelConfigRequest) {
  return requestJson<ModelConfigSummary>('/settings/models', { method: 'POST', body: JSON.stringify(payload) })
}

export function updateModelConfig(modelConfigId: string, payload: SaveModelConfigRequest) {
  return requestJson<ModelConfigSummary>(`/settings/models/${encodeURIComponent(modelConfigId)}`, { method: 'PUT', body: JSON.stringify(payload) })
}

export function deleteModelConfig(modelConfigId: string) {
  return requestJson<void>(`/settings/models/${encodeURIComponent(modelConfigId)}`, { method: 'DELETE' })
}

export function testModelConnection(modelConfigId: string) {
  return requestJson<ModelConnectionTestResponse>(`/settings/models/${encodeURIComponent(modelConfigId)}/test`, { method: 'POST' })
}

export function testUnsavedModelConnection(payload: SaveModelConfigRequest) {
  return requestJson<ModelConnectionTestResponse>('/settings/models/test', { method: 'POST', body: JSON.stringify(payload) })
}

export function testUserCloudPivotConnection(payload: SaveUserSettingsRequest) {
  return requestJson<ConnectionTestResponse>('/settings/cloudpivot/test', { method: 'POST', body: JSON.stringify(payload) })
}

export function testAdminCloudPivotConnection(payload: SaveAdminSettingsRequest) {
  return requestJson<ConnectionTestResponse>('/settings/metadata-cloudpivot/test', { method: 'POST', body: JSON.stringify(payload) })
}

export function getMemorySettings() {
  return requestJson<MemorySettingsResponse>('/settings/memory')
}

export function savePersonalMemory(payload: SaveMemoryEntryRequest) {
  return requestJson<MemoryEntry>('/settings/memory/personal', { method: 'POST', body: JSON.stringify(payload) })
}

export function deletePersonalMemory(id: string) {
  return requestJson<void>(`/settings/memory/personal/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export function saveGlobalMemory(payload: SaveMemoryEntryRequest) {
  return requestJson<MemoryEntry>('/settings/memory/global', { method: 'POST', body: JSON.stringify(payload) })
}

export function deleteGlobalMemory(id: string) {
  return requestJson<void>(`/settings/memory/global/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
