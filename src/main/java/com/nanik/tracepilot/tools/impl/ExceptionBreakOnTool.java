package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.ExceptionBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to set an exception breakpoint.
 * The VM will suspend when the specified exception (or any exception) is thrown.
 */
public class ExceptionBreakOnTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("exceptionClass",
                "Fully qualified exception class name (e.g., java.lang.NullPointerException). " +
                "Use '*' or omit to break on all exceptions.")
            .addBoolean("caught", "Break on caught exceptions (default: true)", false, true)
            .addBoolean("uncaught", "Break on uncaught exceptions (default: true)", false, true)
            .build();

        return new ToolDefinition(
            "exception_break_on",
            "Set exception breakpoint. Use '*' to catch ALL exceptions. When hit, use exception_info to see details.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        try {
            String exceptionClass = request.getStringParam("exceptionClass");
            Boolean caught = request.getBoolParam("caught");
            Boolean uncaught = request.getBoolParam("uncaught");

            // Defaults
            if (caught == null) caught = true;
            if (uncaught == null) uncaught = true;

            String id = ExceptionBreakpointManager.getInstance()
                .setExceptionBreakpoint(exceptionClass, caught, uncaught);

            StringBuilder sb = new StringBuilder();
            sb.append("Exception breakpoint set.\n");
            sb.append("ID: ").append(id).append("\n");

            if (exceptionClass == null || exceptionClass.isEmpty() || exceptionClass.equals("*")) {
                sb.append("Exception: All exceptions\n");
            } else {
                sb.append("Exception: ").append(exceptionClass).append("\n");
            }

            sb.append("Caught: ").append(caught ? "yes" : "no").append("\n");
            sb.append("Uncaught: ").append(uncaught ? "yes" : "no");

            return ToolResult.success(sb.toString());

        } catch (IllegalStateException e) {
            return ToolResult.error("Not connected to a VM. Use debug_attach_socket or debug_launch first.");
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to set exception breakpoint: " + e.getMessage());
        }
    }
}

