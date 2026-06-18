const API_BASE = '/api'

type ApiPayload<T> = {
  success: boolean
  data: T
  message?: string
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers
    },
    ...init
  })

  const payload = await readPayload<T>(response)
  if (!response.ok) {
    throw new Error(payload?.message ?? `Request failed: ${response.status}`)
  }

  if (!payload) {
    throw new Error('Response body is empty')
  }
  if (!payload.success) {
    throw new Error(payload.message ?? 'Request failed')
  }
  return payload.data
}

async function readPayload<T>(response: Response): Promise<ApiPayload<T> | undefined> {
  const text = await response.text()
  if (!text) {
    return undefined
  }
  try {
    return JSON.parse(text) as ApiPayload<T>
  } catch {
    if (!response.ok) {
      return { success: false, data: undefined as T, message: text }
    }
    throw new Error('Response body is not valid JSON')
  }
}
