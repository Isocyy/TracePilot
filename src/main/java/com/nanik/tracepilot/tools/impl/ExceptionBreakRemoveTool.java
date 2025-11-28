package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.ExceptionBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to remove an exception breakpoint.
 */
public class ExceptionBreakRemoveTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("breakpointId", "ID of the exception breakpoint to remove (e.g., ex-1)", true)
            .build();

        return new ToolDefinition(
            "exception_break_remove",
            "Remove an exception breakpoint by its ID.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        try {
            String breakpointId = request.getStringParam("breakpointId");

            if (breakpointId == null || breakpointId.isEmpty()) {
                return ToolResult.error("breakpointId is required");
            }

            boolean removed = ExceptionBreakpointManager.getInstance()
                .removeExceptionBreakpoint(breakpointId);

            if (removed) {
                return ToolResult.success("Exception breakpoint removed: " + breakpointId);
            } else {
                return ToolResult.error("Exception breakpoint not found: " + breakpointId);
            }

        } catch (Exception e) {
            return ToolResult.error("Failed to remove exception breakpoint: " + e.getMessage());
        }
    }
}

