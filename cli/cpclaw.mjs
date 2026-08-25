#!/usr/bin/env node

// Channel-neutral CPClaw CLI. It deliberately contains no Yunshu business rules;
// all planning, permission checks and execution stay in the server TaskGateway.
const baseUrl = (process.env.CPCLAW_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const principal = process.env.CPCLAW_PRINCIPAL || 'huangj'
const installation = process.env.CPCLAW_MCP_INSTALLATION_ID || 'default-mcp-installation'

function usage() {
  console.error(`Usage:
  cpclaw delegate run --spec task.json
  cpclaw task status <taskId>
  cpclaw task events <taskId>
  cpclaw task continue <taskId> --token <token> [--message text] [--turn-id id]
  cpclaw task cancel <taskId>`)
  process.exit(2)
}

function option(args, name, fallback = '') {
  const index = args.indexOf(name)
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback
}

async function request(path, init = {}) {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      accept: 'application/json',
      'content-type': 'application/json',
      'x-cpclaw-principal': principal,
      'x-cpclaw-installation-id': installation,
      ...(init.headers || {})
    }
  })
  const text = await response.text()
  let value
  try { value = JSON.parse(text) } catch { value = { raw: text } }
  if (!response.ok) throw new Error(`${response.status}: ${JSON.stringify(value)}`)
  return value
}

async function main(argv) {
  const [group, command, ...args] = argv
  if (!group || !command) usage()
  if (group === 'delegate' && command === 'run') {
    const specPath = option(args, '--spec')
    if (!specPath) usage()
    const spec = JSON.parse(await (await import('node:fs/promises')).readFile(specPath, 'utf8'))
    const result = await request('/api/mcp/cloudpivot', {
      method: 'POST',
      body: JSON.stringify({
        jsonrpc: '2.0', id: Date.now(), method: 'tools/call',
        params: { name: 'cpclaw_cloudpivot_agent', arguments: { ...spec, externalPrincipal: principal } }
      })
    })
    console.log(JSON.stringify(result, null, 2))
    return
  }
  if (group === 'task' && ['status', 'events', 'cancel'].includes(command)) {
    const taskId = args[0]
    if (!taskId) usage()
    const suffix = command === 'status' ? '' : command === 'events' ? '/events' : '/cancel'
    const result = await request(`/api/tasks/${encodeURIComponent(taskId)}${suffix}`, { method: command === 'cancel' ? 'POST' : 'GET', body: command === 'cancel' ? '{}' : undefined })
    console.log(JSON.stringify(result, null, 2))
    return
  }
  if (group === 'task' && command === 'continue') {
    const taskId = args[0]
    const token = option(args, '--token')
    if (!taskId || !token) usage()
    const result = await request(`/api/mcp/cloudpivot/tasks/${encodeURIComponent(taskId)}/continue`, {
      method: 'POST',
      body: JSON.stringify({ continuationToken: token, request: option(args, '--message', '继续上一任务'), turnId: option(args, '--turn-id', `continue-${Date.now()}`), clientRequestId: option(args, '--client-request-id', `continue-${Date.now()}`), conversationId: option(args, '--conversation-id', '') })
    })
    console.log(JSON.stringify(result, null, 2))
    return
  }
  usage()
}

main(process.argv.slice(2)).catch(error => { console.error(error.message); process.exit(1) })
