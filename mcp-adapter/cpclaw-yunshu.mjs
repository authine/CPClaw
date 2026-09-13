#!/usr/bin/env node

// Stdio bridge for the independently addressable Yunshu Skill Gateway.
// Every input line is one yunshu-skill/1.0 Canonical Command. This bridge
// never calls CPClaw MCP, persists credentials, or opens a CPClaw UI.
import readline from 'node:readline'

const gatewayBaseUrl = (process.env.YUNSHU_SKILL_GATEWAY_URL || 'http://localhost:8080').replace(/\/$/, '')
const hostId = process.env.YUNSHU_HOST_ID
const authorization = process.env.YUNSHU_AUTHORIZATION

if (!hostId) {
  process.stderr.write('Yunshu Skill bridge requires YUNSHU_HOST_ID\n')
  process.exit(2)
}

const input = readline.createInterface({ input: process.stdin, crlfDelay: Infinity })
for await (const line of input) {
  if (!line.trim()) continue
  try {
    const command = JSON.parse(line)
    const headers = {
      'content-type': 'application/json',
      'x-yunshu-host-id': hostId
    }
    if (authorization) headers.authorization = authorization
    const response = await fetch(`${gatewayBaseUrl}/api/yunshu-skill/tasks/command`, {
      method: 'POST', headers, body: JSON.stringify(command)
    })
    const payload = await response.text()
    process.stdout.write(`${payload}\n`)
  } catch (error) {
    process.stdout.write(`${JSON.stringify({
      protocolVersion: 'yunshu-skill/1.0',
      error: {
        code: 'GATEWAY_UNAVAILABLE',
        message: error instanceof Error ? error.message : '无法连接 Yunshu Skill Gateway',
        retryable: true
      }
    })}\n`)
  }
}
