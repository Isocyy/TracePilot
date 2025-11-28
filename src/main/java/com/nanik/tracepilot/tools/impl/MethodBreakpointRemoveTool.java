package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.MethodBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to remove a method entry/exit breakpoint by ID.
 */
public class MethodBreakpointRemoveTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("breakpointId", "ID of the method breakpoint to remove (e.g., 'me-1' or 'mx-2')")
            .setRequired("breakpointId")
            .build();

        return new ToolDefinition(
            "method_breakpoint_remove",
            "Remove a method entry or exit breakpoint by its ID. Use method_breakpoint_list to see all method breakpoints.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        String breakpointId = request.getStringParam("breakpointId");
        if (breakpointId == null || breakpointId.isEmpty()) {
            return ToolResult.error("breakpointId is required");
        }

        MethodBreakpointManager manager = MethodBreakpointManager.getInstance();
        boolean removed = manager.removeMethodBreakpoint(breakpointId);

        if (removed) {
            return ToolResult.success("Method breakpoint " + breakpointId + " removed.");
        } else {
            return ToolResult.error("Method breakpoint not found: " + breakpointId);
        }
    }
}

