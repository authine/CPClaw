import { requestJson } from './api'
import type {
  ConnectionTestResponse,
  ModelConfigSummary,
  SaveAdminSettingsRequest,
  SaveUserSettingsRequest,
  SettingsResponse
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

export function testUserCloudPivotConnection() {
  return requestJson<ConnectionTestResponse>('/settings/cloudpivot/test', { method: 'POST' })
}

export function testAdminCloudPivotConnection() {
  return requestJson<ConnectionTestResponse>('/settings/metadata-cloudpivot/test', { method: 'POST' })
}
