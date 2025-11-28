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
 * Tool to remove a watchpoint.
 */
public class WatchpointRemoveTool implements ToolHandler {

    @Override
    public ToolDefinition getDefinition() {
        JsonObject schema = new SchemaBuilder()
            .addString("watchpointId", "ID of the watchpoint to remove (e.g., wa-1 or wm-2)")
            .setRequired("watchpointId")
            .build();

        return new ToolDefinition(
            "watchpoint_remove",
            "Remove a watchpoint by its ID.",
            schema
        );
    }

    @Override
    public ToolResult execute(McpRequest request) {
        if (!DebugSession.getInstance().isConnected()) {
            return ToolResult.error("Not connected to a VM.");
        }

        String watchpointId = request.getStringParam("watchpointId");

        if (watchpointId == null || watchpointId.isEmpty()) {
            return ToolResult.error("watchpointId is required");
        }

        boolean removed = WatchpointManager.getInstance().removeWatchpoint(watchpointId);

        if (removed) {
            return ToolResult.success("Watchpoint " + watchpointId + " removed.");
        } else {
            return ToolResult.error("Watchpoint not found: " + watchpointId);
        }
    }
}

