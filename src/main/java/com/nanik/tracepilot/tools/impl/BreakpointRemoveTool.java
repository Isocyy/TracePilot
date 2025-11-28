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
 * Tool to remove a breakpoint.
 */
public class BreakpointRemoveTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("breakpointId", "ID of the breakpoint to remove (e.g., bp-1)")
            .setRequired("breakpointId")
            .build();
        
        return new ToolDefinition(
            "breakpoint_remove",
            "Remove a breakpoint by its ID.",
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
        
        boolean removed = BreakpointManager.getInstance().removeBreakpoint(breakpointId);
        
        if (removed) {
            return ToolResult.success("Breakpoint " + breakpointId + " removed.");
        } else {
            return ToolResult.error("Breakpoint not found: " + breakpointId);
        }
    }
}

