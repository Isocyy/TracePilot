package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.BreakpointManager;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to disable a breakpoint.
 */
public class BreakpointDisableTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("breakpointId", "ID of the breakpoint to disable (e.g., bp-1)")
            .setRequired("breakpointId")
            .build();
        
        return new ToolDefinition(
            "breakpoint_disable",
            "Disable a breakpoint without removing it. Can be re-enabled later.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        String breakpointId = request.getStringParam("breakpointId");
        
        if (breakpointId == null || breakpointId.isEmpty()) {
            return ToolResult.error("breakpointId is required");
        }
        
        boolean disabled = BreakpointManager.getInstance().disableBreakpoint(breakpointId);
        
        if (disabled) {
            return ToolResult.success("Breakpoint " + breakpointId + " disabled.");
        } else {
            return ToolResult.error("Breakpoint not found: " + breakpointId);
        }
    }
}

