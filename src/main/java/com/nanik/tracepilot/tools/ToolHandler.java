package com.nanik.tracepilot.tools;

import com.nanik.tracepilot.protocol.McpRequest;

/**
 * Interface for implementing MCP tools.
 * 
 * Each tool must provide:
 * - A definition (name, description, input schema)
 * - An execute method that processes requests
 */
public interface ToolHandler {
    
    /**
     * Get the tool definition for this handler.
     * This is used to populate the tools/list response.
     */
    ToolDefinition getDefinition();
    
    /**
     * Execute the tool with the given request.
     * 
     * @param request The MCP request containing tool parameters
     * @return The result of the tool execution
     */
    ToolResult execute(McpRequest request);
}

