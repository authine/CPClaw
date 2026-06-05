const API_BASE = '/api'

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers
    },
    ...init
  })

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`)
  }

  const payload = (await response.json()) as { success: boolean; data: T; message?: string }
  if (!payload.success) {
    throw new Error(payload.message ?? 'Request failed')
  }
  return payload.data
}
