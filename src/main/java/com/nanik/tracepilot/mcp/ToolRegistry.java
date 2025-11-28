package com.nanik.tracepilot.mcp;

import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.*;

/**
 * Registry for MCP tools.
 * 
 * Manages tool registration and dispatches tool calls.
 */
public class ToolRegistry {
    
    private final Map<String, ToolHandler> handlers;
    private final StdioTransport transport;
    
    public ToolRegistry(StdioTransport transport) {
        this.handlers = new LinkedHashMap<>(); // Preserve insertion order
        this.transport = transport;
    }
    
    /**
     * Register a tool handler.
     */
    public void register(ToolHandler handler) {
        String name = handler.getDefinition().getName();
        if (handlers.containsKey(name)) {
            transport.log("Warning: Replacing existing tool: " + name);
        }
        handlers.put(name, handler);
        transport.log("Registered tool: " + name);
    }
    
    /**
     * Register multiple tool handlers.
     */
    public void registerAll(ToolHandler... handlers) {
        for (ToolHandler handler : handlers) {
            register(handler);
        }
    }
    
    /**
     * Check if a tool exists.
     */
    public boolean hasTool(String name) {
        return handlers.containsKey(name);
    }
    
    /**
     * Get a tool handler by name.
     */
    public ToolHandler getTool(String name) {
        return handlers.get(name);
    }
    
    /**
     * Execute a tool by name.
     * 
     * @param name The tool name
     * @param request The MCP request
     * @return The tool result, or null if tool not found
     */
    public ToolResult execute(String name, McpRequest request) {
        ToolHandler handler = handlers.get(name);
        if (handler == null) {
            return null;
        }
        
        try {
            return handler.execute(request);
        } catch (Exception e) {
            transport.logError("Tool execution failed: " + name, e);
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }
    
    /**
     * Get all tool definitions.
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ToolHandler handler : handlers.values()) {
            definitions.add(handler.getDefinition());
        }
        return definitions;
    }
    
    /**
     * Get all tool names.
     */
    public Set<String> getToolNames() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
    
    /**
     * Get the number of registered tools.
     */
    public int size() {
        return handlers.size();
    }
}

