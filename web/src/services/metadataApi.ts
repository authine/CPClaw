import { requestJson } from './api'
import type {
  MetadataAppSummary,
  MetadataGraphNeighborhood,
  MetadataGraphOverview,
  MetadataModelResponse,
  MetadataSearchResult,
  MetadataSyncResponse
} from '../types/metadata'

export function listMetadataApps() {
  return requestJson<MetadataAppSummary[]>('/metadata/apps')
}

export function loadMetadataModel() {
  return requestJson<MetadataModelResponse>('/metadata/model')
}

export function syncMetadata() {
  return requestJson<MetadataSyncResponse>('/metadata/sync', { method: 'POST' })
}

export function searchMetadata(query: string) {
  return requestJson<MetadataSearchResult[]>(`/metadata/search?query=${encodeURIComponent(query)}`)
}

export function loadMetadataGraphOverview() {
  return requestJson<MetadataGraphOverview>('/metadata/graph/overview')
}

export function loadMetadataGraphNeighborhood(objectType: string, objectId: string, depth = 1, limit = 100) {
  const params = new URLSearchParams({ objectType, objectId, depth: String(depth), limit: String(limit) })
  return requestJson<MetadataGraphNeighborhood>(`/metadata/graph/neighborhood?${params.toString()}`)
}

export function rebuildMetadataGraph() {
  return requestJson<MetadataGraphOverview>('/metadata/graph/rebuild', { method: 'POST' })
}
