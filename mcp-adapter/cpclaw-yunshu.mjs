#!/usr/bin/env node

// Standard MCP stdio adapter for the independently addressable Yunshu Skill Gateway.
// It never calls CPClaw chat/MCP, persists credentials, or opens a CPClaw UI.
import readline from 'node:readline'
import { createHmac } from 'node:crypto'

const MCP_PROTOCOL_VERSION = '2025-06-18'
const SKILL_PROTOCOL_VERSION = 'yunshu-skill/1.0'
const gatewayBaseUrl = (process.env.YUNSHU_SKILL_GATEWAY_URL || 'http://localhost:8080').replace(/\/$/, '')
const hostId = process.env.YUNSHU_HOST_ID
const authorization = process.env.YUNSHU_AUTHORIZATION
const tenantId = process.env.YUNSHU_TENANT_ID
const principal = process.env.YUNSHU_PRINCIPAL
const sharedSecret = process.env.YUNSHU_SHARED_SECRET

if (!hostId) {
  process.stderr.write('Yunshu Skill MCP adapter requires YUNSHU_HOST_ID\n')
  process.exit(2)
}

if (!sharedSecret && !authorization) {
  process.stderr.write('Yunshu Skill MCP adapter requires YUNSHU_SHARED_SECRET (HMAC) or YUNSHU_AUTHORIZATION (external Gateway assertion)\n')
  process.exit(2)
}
if (sharedSecret && (!tenantId || !principal)) {
  process.stderr.write('HMAC mode requires YUNSHU_TENANT_ID and YUNSHU_PRINCIPAL\n')
  process.exit(2)
}

let initialized = false
let clientProtocolVersion = MCP_PROTOCOL_VERSION

const tool = {
  name: 'yunshu_task',
  description: '直接操作云枢 Skill：根据自然语言目标完成查询、分析、智能填单、待办处理和多轮任务续接。不要填写 schemaCode、apiCode 或内部字段编码；需要确认时必须在当前宿主内完成确认。',
  inputSchema: {
    type: 'object',
    additionalProperties: false,
    properties: {
      operation: { type: 'string', enum: ['start', 'input', 'action', 'get', 'events'], description: '任务操作；首次目标使用 start。' },
      goal: { type: 'string', description: 'start 时的自然语言业务目标。' },
      taskId: { type: 'string', description: '非 start 操作使用服务端返回的原任务标识。' },
      input: { type: 'object', description: '用户补充内容或受控字段回答。' },
      action: { type: 'object', description: 'confirm、update_draft 或 cancel 等宿主动作。' },
      expectedStateVersion: { type: 'integer', minimum: 1 },
      continuationToken: { type: 'string' },
      clientRequestId: { type: 'string', description: '同一重试必须复用的稳定请求标识。' },
      conversationId: { type: 'string' },
      turnId: { type: 'string' },
      context: { type: 'array', items: { type: 'string' }, maxItems: 6 },
      taskSpec: { type: 'object' },
      presentation: { type: 'object' }
    }
  }
}

function response(id, result) {
  return { jsonrpc: '2.0', id, result }
}
function failure(id, code, message, data) {
  const error = { code, message }
  if (data !== undefined) error.data = data
  return { jsonrpc: '2.0', id, error }
}
function idOf(request) {
  return request && Object.prototype.hasOwnProperty.call(request, 'id') ? request.id : null
}
function write(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`)
}
function canonicalCommand(args) {
  const operation = typeof args.operation === 'string' && args.operation.trim() ? args.operation.trim() : 'start'
  const command = { protocolVersion: SKILL_PROTOCOL_VERSION, operation }
  const fields = ['goal', 'taskId', 'input', 'action', 'clientRequestId', 'expectedStateVersion', 'continuationToken', 'conversationId', 'turnId', 'context', 'taskSpec', 'presentation']
  for (const field of fields) {
    if (args[field] !== undefined && args[field] !== null) command[field] = args[field]
  }
  if (operation === 'start' && (!command.goal || typeof command.goal !== 'string' || !command.goal.trim())) {
    throw new Error('start 操作必须提供 goal')
  }
  if (operation !== 'start' && (!command.taskId || typeof command.taskId !== 'string' || !command.taskId.trim())) {
    throw new Error(`${operation} 操作必须提供 taskId`)
  }
  return command
}

async function callGateway(command) {
  const headers = { 'content-type': 'application/json', 'x-yunshu-host-id': hostId }
  if (authorization) headers.authorization = authorization
  if (sharedSecret) {
    const timestamp = Math.floor(Date.now() / 1000).toString()
    const canonical = `${hostId}\n${tenantId}\n${principal}\n${timestamp}`
    const signature = createHmac('sha256', sharedSecret).update(canonical, 'utf8').digest('base64url')
    headers['x-yunshu-tenant-id'] = tenantId
    headers['x-yunshu-principal'] = principal
    headers['x-yunshu-auth-timestamp'] = timestamp
    headers['x-yunshu-auth-signature'] = signature
  }
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 60_000)
  try {
    const result = await fetch(`${gatewayBaseUrl}/api/yunshu-skill/tasks/command`, {
      method: 'POST', headers, body: JSON.stringify(command), signal: controller.signal
    })
    const raw = await result.text()
    let payload
    try { payload = raw ? JSON.parse(raw) : {} } catch { payload = { error: { code: 'INVALID_GATEWAY_RESPONSE', message: 'Skill Gateway 返回了无法解析的响应' } } }
    if (!result.ok) {
      return { isError: true, content: [{ type: 'text', text: JSON.stringify(payload) }], structuredContent: payload }
    }
    return { content: [{ type: 'text', text: JSON.stringify(payload) }], structuredContent: payload }
  } catch (error) {
    const message = error?.name === 'AbortError' ? 'Skill Gateway 请求超时' : (error?.message || '无法连接 Yunshu Skill Gateway')
    return { isError: true, content: [{ type: 'text', text: JSON.stringify({ protocolVersion: SKILL_PROTOCOL_VERSION, error: { code: 'GATEWAY_UNAVAILABLE', message, retryable: true } }) }] }
  } finally {
    clearTimeout(timer)
  }
}

async function handle(request) {
  if (!request || typeof request !== 'object' || Array.isArray(request) || request.jsonrpc !== '2.0') {
    return failure(idOf(request), -32600, '无效的 JSON-RPC 2.0 请求')
  }
  const id = idOf(request)
  const method = request.method
  if (typeof method !== 'string') return failure(id, -32600, '缺少 method')
  if (method === 'notifications/initialized' || method.startsWith('notifications/')) return null
  if (method === 'initialize') {
    const params = request.params && typeof request.params === 'object' ? request.params : {}
    const requested = typeof params.protocolVersion === 'string' ? params.protocolVersion : MCP_PROTOCOL_VERSION
    clientProtocolVersion = requested === MCP_PROTOCOL_VERSION ? requested : MCP_PROTOCOL_VERSION
    initialized = true
    return response(id, {
      protocolVersion: clientProtocolVersion,
      capabilities: { tools: { listChanged: false } },
      serverInfo: { name: 'yunshu-skill-gateway', version: '1.0.0' },
      instructions: '使用 yunshu_task 直接操作 Yunshu Skill Gateway；不要调用 CPClaw 对话或旧 MCP 工具。'
    })
  }
  if (!initialized) return failure(id, -32002, 'MCP 尚未初始化')
  if (method === 'ping') return response(id, {})
  if (method === 'tools/list') return response(id, { tools: [tool] })
  if (method === 'tools/call') {
    const params = request.params && typeof request.params === 'object' ? request.params : {}
    if (params.name !== tool.name) return failure(id, -32602, `未知工具：${params.name || ''}`)
    try {
      const args = params.arguments && typeof params.arguments === 'object' ? params.arguments : {}
      return response(id, await callGateway(canonicalCommand(args)))
    } catch (error) {
      return failure(id, -32602, error?.message || '工具参数无效')
    }
  }
  return failure(id, -32601, `未实现的 MCP 方法：${method}`)
}

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity })
for await (const line of input) {
  if (!line.trim()) continue
  let request
  try { request = JSON.parse(line) } catch { write(failure(null, -32700, '无法解析 JSON')) ; continue }
  if (Array.isArray(request)) {
    for (const item of request) {
      const result = await handle(item)
      if (result) write(result)
    }
  } else {
    const result = await handle(request)
    if (result) write(result)
  }
}
