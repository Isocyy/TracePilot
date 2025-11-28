package com.nanik.tracepilot.tools.impl;

import com.nanik.tracepilot.debug.BreakpointInfo;
import com.nanik.tracepilot.debug.BreakpointManager;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.List;

/**
 * Tool to list all breakpoints.
 */
public class BreakpointListTool implements ToolHandler {
    
    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.noParams(
            "breakpoint_list",
            "List all breakpoints with their IDs, locations, and status."
        );
    }
    
    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }
        
        List<BreakpointInfo> breakpoints = BreakpointManager.getInstance().getAllBreakpoints();
        
        if (breakpoints.isEmpty()) {
            return ToolResult.success("No breakpoints set.");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Breakpoints ===\n\n");
        
        for (BreakpointInfo bp : breakpoints) {
            String status;
            if (bp.isPending()) {
                status = "pending";
            } else if (bp.isEnabled()) {
                status = "enabled";
            } else {
                status = "disabled";
            }

            sb.append(String.format("%-8s %s:%d [%s] (hits: %d)\n",
                bp.getId(),
                bp.getClassName(),
                bp.getLineNumber(),
                status,
                bp.getHitCount()
            ));
        }
        
        sb.append("\nTotal: ").append(breakpoints.size()).append(" breakpoint(s)");
        
        return ToolResult.success(sb.toString());
    }
}

