import { requestJson } from './api'
import type { MetadataAppSummary, MetadataSearchResult, MetadataSyncResponse } from '../types/metadata'

export function listMetadataApps() {
  return requestJson<MetadataAppSummary[]>('/metadata/apps')
}

export function syncMetadata() {
  return requestJson<MetadataSyncResponse>('/metadata/sync', { method: 'POST' })
}

export function searchMetadata(query: string) {
  return requestJson<MetadataSearchResult[]>(`/metadata/search?query=${encodeURIComponent(query)}`)
}
