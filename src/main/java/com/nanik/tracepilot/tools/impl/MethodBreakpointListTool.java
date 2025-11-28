package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.MethodBreakpointInfo;
import com.nanik.tracepilot.debug.MethodBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.List;

/**
 * Tool to list all method entry/exit breakpoints.
 */
public class MethodBreakpointListTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "method_breakpoint_list",
            "List all method entry and exit breakpoints with their IDs, status, and target methods.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        MethodBreakpointManager manager = MethodBreakpointManager.getInstance();
        List<MethodBreakpointInfo> breakpoints = manager.getAllMethodBreakpoints();

        if (breakpoints.isEmpty()) {
            return ToolResult.success("No method breakpoints set.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Method Breakpoints ===\n\n");

        for (MethodBreakpointInfo info : breakpoints) {
            sb.append(info.getId());
            sb.append("  ");
            sb.append(info.getType() == MethodBreakpointInfo.MethodBreakpointType.ENTRY ? "ENTRY" : "EXIT");
            sb.append("  ");
            sb.append(info.getClassName());
            sb.append(".");
            sb.append(info.getMethodName());
            sb.append("()");

            if (info.isPending()) {
                sb.append(" [pending]");
            } else if (!info.isEnabled()) {
                sb.append(" [disabled]");
            } else {
                sb.append(" [enabled]");
            }

            sb.append("\n");
        }

        sb.append("\nTotal: ").append(breakpoints.size()).append(" method breakpoint(s)");

        return ToolResult.success(sb.toString());
    }
}

