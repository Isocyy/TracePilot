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
 * Tool to set a line breakpoint.
 */
public class BreakpointSetTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("className", "Fully qualified class name (e.g., com.example.Main)")
            .addInteger("lineNumber", "Line number to set breakpoint on")
            .setRequired("className", "lineNumber")
            .build();
        
        return new ToolDefinition(
            "breakpoint_set",
            "Set a line breakpoint. Returns PENDING if class not yet loaded (activates when class loads). Use resume + wait_for_stop to hit it.",
            schema
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM. Use debug_launch or debug_attach first.");
        }
        
        String className = request.getStringParam("className");
        Integer lineNumber = request.getIntParam("lineNumber");
        
        if (className == null || className.isEmpty()) {
            return ToolResult.error("className is required");
        }
        if (lineNumber == null) {
            return ToolResult.error("lineNumber is required");
        }
        
        try {
            BreakpointManager bpManager = BreakpointManager.getInstance();
            String breakpointId = bpManager.setLineBreakpoint(className, lineNumber);

            // Check if it's a pending (deferred) breakpoint
            boolean isPending = bpManager.getBreakpoint(breakpointId).isPending();

            StringBuilder sb = new StringBuilder();
            if (isPending) {
                sb.append("Breakpoint set (PENDING - class not yet loaded).\n");
                sb.append("The breakpoint will be activated when the class is loaded.\n");
            } else {
                sb.append("Breakpoint set successfully.\n");
            }
            sb.append("ID: ").append(breakpointId).append("\n");
            sb.append("Location: ").append(className).append(":").append(lineNumber);

            return ToolResult.success(sb.toString());
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to set breakpoint: " + e.getMessage());
        }
    }
}

