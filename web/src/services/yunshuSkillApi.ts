import type { YunshuTaskCommand, YunshuTaskExperienceEnvelope } from '../types/yunshuTask'

/**
 * Adapter for the independent Yunshu Skill Gateway.  The default is proxied by
 * the Web deployment, while a separate gateway can be configured at build time.
 */
const SKILL_API_BASE = (import.meta.env.VITE_YUNSHU_SKILL_API_BASE || '/api/yunshu-skill').replace(/\/$/, '')

export async function sendYunshuTaskCommand(command: YunshuTaskCommand) {
  let response: Response
  try {
    response = await fetch(`${SKILL_API_BASE}/tasks/command`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Yunshu-Host-Id': 'cpclaw-web'
      },
      body: JSON.stringify(command)
    })
  } catch {
    throw new Error('云枢 Skill Gateway 不可用，请确认服务已启动。')
  }
  const payload = await readResponse(response)
  if (!response.ok) throw new Error(payload.message || `云枢 Skill 请求失败：${response.status}`)
  return payload.envelope as YunshuTaskExperienceEnvelope
}

async function readResponse(response: Response): Promise<{ envelope?: unknown; message?: string }> {
  const body = await response.text()
  if (!body) return { message: '云枢 Skill 未返回任务状态。' }
  try {
    const parsed = JSON.parse(body) as Record<string, unknown>
    // Accept the repository's legacy { success, data, message } envelope only
    // during migration; an independent gateway returns the canonical body.
    if ('success' in parsed) return { envelope: parsed.data, message: typeof parsed.message === 'string' ? parsed.message : undefined }
    return { envelope: parsed, message: typeof parsed.message === 'string' ? parsed.message : undefined }
  } catch {
    return { message: body }
  }
}
