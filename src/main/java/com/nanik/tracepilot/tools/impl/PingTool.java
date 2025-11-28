package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Simple ping/pong tool for health checks.
 */
public class PingTool implements ToolHandler {
    
    private static final ToolDefinition DEFINITION = ToolDefinition.noParams(
        "ping",
        "Health check - returns 'pong'"
    );
    
    @Override
    public ToolDefinition getDefinition() {
        return DEFINITION;
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        return ToolResult.success("pong");
    }
}

