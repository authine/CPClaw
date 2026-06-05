import type { AttachmentResponse } from '../types/agent'

export async function uploadAttachment(file: File) {
  const data = new FormData()
  data.append('file', file)

  const response = await fetch('/api/attachments', {
    method: 'POST',
    body: data
  })

  if (!response.ok) {
    throw new Error(`Upload failed: ${response.status}`)
  }

  const payload = (await response.json()) as { data: AttachmentResponse }
  return payload.data
}
