const API_BASE = '/api'
const BACKEND_UNAVAILABLE_MESSAGE = '后端服务不可用，请确认后端已启动后重试。'

type ApiPayload<T> = {
  success: boolean
  data: T
  message?: string
}

export async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers
      },
      ...init
    })
  } catch {
    throw new Error(BACKEND_UNAVAILABLE_MESSAGE)
  }

  const payload = await readPayload<T>(response)
  if (!response.ok) {
    throw new Error(payload?.message ?? messageForStatus(response.status))
  }

  if (!payload) {
    throw new Error('Response body is empty')
  }
  if (!payload.success) {
    throw new Error(payload.message ?? 'Request failed')
  }
  return payload.data
}

function messageForStatus(status: number, fallbackMessage?: string) {
  if (status >= 500) {
    return BACKEND_UNAVAILABLE_MESSAGE
  }
  return fallbackMessage || `请求失败：${status}`
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
      return { success: false, data: undefined as T, message: messageForStatus(response.status, text) }
    }
    throw new Error('Response body is not valid JSON')
  }
}
