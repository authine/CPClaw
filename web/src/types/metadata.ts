export interface MetadataAppSummary {
  id: string
  code: string
  name: string
  entityCount: number
  syncedAt?: string
}

export interface MetadataSyncResponse {
  syncId: string
  status: string
  appCount: number
  entityCount: number
  dataItemCount: number
  relationCount: number
  searchDocumentCount: number
  createdAt: string
}

export interface MetadataSearchResult {
  objectType: string
  objectId: string
  name: string
  code: string
  graphPath: string
  riskLevel: string
  reason: string
}
