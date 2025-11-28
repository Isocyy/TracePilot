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
 * Tool to enable a breakpoint.
 */
public class BreakpointEnableTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("breakpointId", "ID of the breakpoint to enable (e.g., bp-1)")
            .setRequired("breakpointId")
            .build();
        
        return new ToolDefinition(
            "breakpoint_enable",
            "Enable a previously disabled breakpoint.",
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
        
        boolean enabled = BreakpointManager.getInstance().enableBreakpoint(breakpointId);
        
        if (enabled) {
            return ToolResult.success("Breakpoint " + breakpointId + " enabled.");
        } else {
            return ToolResult.error("Breakpoint not found: " + breakpointId);
        }
    }
}

