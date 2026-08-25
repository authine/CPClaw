#!/usr/bin/env node

// Generic stdio bridge for CPClaw's governed Yunshu MCP gateway.
// It forwards one JSON-RPC message per line. Credentials stay in the MCP client's local environment
// and are forwarded only to the local CPClaw service, never written to CPClaw persistence.
import readline from 'node:readline'

const apiBaseUrl = (process.env.CPCLAW_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')
const installationId = process.env.CPCLAW_MCP_INSTALLATION_ID
const cloudPivotUsername = process.env.CPC_CLOUDPIVOT_USERNAME
const cloudPivotPassword = process.env.CPC_CLOUDPIVOT_PASSWORD

if (!installationId) {
  process.stderr.write('CPClaw MCP adapter requires CPCLAW_MCP_INSTALLATION_ID\n')
  process.exit(2)
}

let gatewayUrl
try {
  gatewayUrl = new URL(apiBaseUrl)
  const localHosts = new Set(['localhost', '127.0.0.1', '[::1]', '::1'])
  if (!localHosts.has(gatewayUrl.hostname)) throw new Error('not-loopback')
} catch {
  process.stderr.write('CPClaw MCP adapter only forwards credentials to a local CPClaw service\n')
  process.exit(2)
}

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity })
for await (const line of input) {
  if (!line.trim()) continue
  try {
    const message = JSON.parse(line)
    const response = await fetch(`${apiBaseUrl}/api/mcp/cloudpivot`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-cpclaw-installation-id': installationId || '',
        'x-cpclaw-cloudpivot-username': cloudPivotUsername || '',
        'x-cpclaw-cloudpivot-password': cloudPivotPassword || ''
      },
      body: line
    })
    const payload = await response.text()
    if (message.id === undefined || String(message.method || '').startsWith('notifications/')) continue
    process.stdout.write(`${payload}\n`)
  } catch (error) {
    process.stdout.write(`${JSON.stringify({
      jsonrpc: '2.0',
      id: null,
      error: { code: -32098, message: error instanceof Error ? error.message : '无法连接 CPClaw MCP Gateway' }
    })}\n`)
  }
}
