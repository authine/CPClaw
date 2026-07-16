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

export interface MetadataModelResponse {
  apps: MetadataAppModel[]
  apiActions: MetadataApiAction[]
}

export interface MetadataAppModel {
  id: string
  code: string
  name: string
  description?: string
  syncedAt?: string
  entities: MetadataEntityModel[]
}

export interface MetadataEntityModel {
  id: string
  appId: string
  code: string
  name: string
  type?: string
  syncedAt?: string
  dataItemCount: number
  relationCount: number
  dataItems: MetadataDataItemModel[]
  relations: MetadataRelationModel[]
  apiActions: MetadataApiAction[]
}

export interface MetadataDataItemModel {
  id: string
  code: string
  name: string
  dataType?: string
  required: boolean
  reference: boolean
  referenceEntityId?: string
  referenceEntityCode?: string
  referenceEntityName?: string
  description?: string
  syncedAt?: string
}

export interface MetadataRelationModel {
  id: string
  relationType?: string
  relationName?: string
  sourceEntityId: string
  sourceEntityCode?: string
  sourceEntityName?: string
  sourceDataItemId?: string
  sourceDataItemCode?: string
  sourceDataItemName?: string
  targetEntityId: string
  targetEntityCode?: string
  targetEntityName?: string
  syncedAt?: string
}

export interface MetadataApiAction {
  id: string
  apiCode: string
  name: string
  method: string
  path: string
  category: string
  operationType: string
  riskLevel: string
  requiresConfirmation: boolean
  inputSchemaJson?: string
  outputSchemaJson?: string
  dataScope?: string
  applicableObjectType?: string
  syncedAt?: string
}