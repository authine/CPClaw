package com.cpclaw.mcp;

/** Raised when a remote MCP host cannot prove its caller identity. */
public class McpAuthenticationException extends RuntimeException {
    public McpAuthenticationException(String message) {
        super(message);
    }
}
