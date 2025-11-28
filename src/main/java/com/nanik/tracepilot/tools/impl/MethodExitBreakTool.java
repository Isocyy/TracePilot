package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.MethodBreakpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to set a method exit breakpoint.
 * The VM will suspend when the method is about to return.
 */
public class MethodExitBreakTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("className", "Fully qualified class name containing the method")
            .addString("methodName", "Name of the method (use '*' for all methods)")
            .setRequired("className", "methodName")
            .build();

        return new ToolDefinition(
            "method_exit_break",
            "Break when method returns. Use '*' for all methods. May show PENDING if class not loaded. Use wait_for_stop after resume.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM. Use debug_launch or debug_attach first.");
        }

        String className = request.getStringParam("className");
        String methodName = request.getStringParam("methodName");

        if (className == null || className.isEmpty()) {
            return ToolResult.error("className is required");
        }
        if (methodName == null || methodName.isEmpty()) {
            return ToolResult.error("methodName is required");
        }

        try {
            MethodBreakpointManager mbManager = MethodBreakpointManager.getInstance();
            String breakpointId = mbManager.setMethodExitBreakpoint(className, methodName);

            boolean isPending = mbManager.getMethodBreakpoint(breakpointId).isPending();

            StringBuilder sb = new StringBuilder();
            if (isPending) {
                sb.append("Method exit breakpoint set (PENDING - class not yet loaded).\n");
                sb.append("The breakpoint will be activated when the class is loaded.\n");
            } else {
                sb.append("Method exit breakpoint set successfully.\n");
            }
            sb.append("ID: ").append(breakpointId).append("\n");
            sb.append("Method: ").append(className).append(".").append(methodName).append("()");

            return ToolResult.success(sb.toString());
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to set method exit breakpoint: " + e.getMessage());
        }
    }
}

