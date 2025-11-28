package com.nanik.tracepilot.tools.impl;

import com.google.gson.JsonObject;
import com.nanik.tracepilot.debug.DebugSession;
import com.nanik.tracepilot.debug.WatchpointManager;
import com.nanik.tracepilot.protocol.McpRequest;
import com.nanik.tracepilot.tools.SchemaBuilder;
import com.nanik.tracepilot.tools.ToolDefinition;
import com.nanik.tracepilot.tools.ToolHandler;
import com.nanik.tracepilot.tools.ToolResult;

/**
 * Tool to set a field modification watchpoint.
 * The VM will suspend when the field is written.
 */
public class WatchpointModificationTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("className", "Fully qualified class name containing the field")
            .addString("fieldName", "Name of the field to watch")
            .setRequired("className", "fieldName")
            .build();

        return new ToolDefinition(
            "watchpoint_modification",
            "Break when field is WRITTEN. May show PENDING if class not loaded. Use wait_for_stop after resume to catch the write.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM. Use debug_launch or debug_attach first.");
        }

        String className = request.getStringParam("className");
        String fieldName = request.getStringParam("fieldName");

        if (className == null || className.isEmpty()) {
            return ToolResult.error("className is required");
        }
        if (fieldName == null || fieldName.isEmpty()) {
            return ToolResult.error("fieldName is required");
        }

        try {
            WatchpointManager wpManager = WatchpointManager.getInstance();
            String watchpointId = wpManager.setModificationWatchpoint(className, fieldName);

            boolean isPending = wpManager.getWatchpoint(watchpointId).isPending();

            StringBuilder sb = new StringBuilder();
            if (isPending) {
                sb.append("Modification watchpoint set (PENDING - class not yet loaded).\n");
                sb.append("The watchpoint will be activated when the class is loaded.\n");
            } else {
                sb.append("Modification watchpoint set successfully.\n");
            }
            sb.append("ID: ").append(watchpointId).append("\n");
            sb.append("Field: ").append(className).append(".").append(fieldName);

            return ToolResult.success(sb.toString());
        } catch (IllegalStateException e) {
            return ToolResult.error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Failed to set modification watchpoint: " + e.getMessage());
        }
    }
}

