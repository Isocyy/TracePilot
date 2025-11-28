package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.ExceptionBreakpointInfo;
import com.nanik.tracepilot.debug.ExceptionBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

import java.util.List;

/**
 * Tool to list all exception breakpoints.
 */
public class ExceptionBreakListTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder().build();

        return new ToolDefinition(
            "exception_break_list",
            "List all exception breakpoints with their IDs, exception types, and status.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        try {
            List<ExceptionBreakpointInfo> breakpoints =
                ExceptionBreakpointManager.getInstance().getAllExceptionBreakpoints();

            if (breakpoints.isEmpty()) {
                return ToolResult.success("No exception breakpoints set.");
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Exception breakpoints (").append(breakpoints.size()).append("):\n\n");

            for (ExceptionBreakpointInfo info : breakpoints) {
                sb.append("─────────────────────────────────\n");
                sb.append("ID: ").append(info.getId()).append("\n");

                if (info.isAllExceptions()) {
                    sb.append("Exception: All exceptions\n");
                } else {
                    sb.append("Exception: ").append(info.getExceptionClassName()).append("\n");
                }

                sb.append("Status: ").append(info.isEnabled() ? "enabled" : "disabled").append("\n");
                sb.append("Caught: ").append(info.isCatchCaught() ? "yes" : "no").append("\n");
                sb.append("Uncaught: ").append(info.isCatchUncaught() ? "yes" : "no").append("\n");
                sb.append("Hits: ").append(info.getHitCount()).append("\n");
            }

            return ToolResult.success(sb.toString());

        } catch (Exception e) {
            return ToolResult.error("Failed to list exception breakpoints: " + e.getMessage());
        }
    }
}

